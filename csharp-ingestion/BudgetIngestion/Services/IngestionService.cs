// Fetches indicator data from the external APIs, validates and saves it, then runs the rule engine.
using BudgetIngestion.Data;
using BudgetIngestion.Models;
using Microsoft.EntityFrameworkCore;

namespace BudgetIngestion.Services;

public record IngestResult(
    string IndicatorCode,
    string SourceId,
    int IndicatorsSaved,
    int AlertsCreated,
    IReadOnlyList<ExternalIndicator> LatestIndicators,
    IReadOnlyList<RuleAlert> Alerts,
    string? Error = null
);

public class IngestionService(
    IngestionDbContext db,
    WorldBankClient worldBankClient,
    OecdClient oecdClient,
    RuleEngine ruleEngine,
    ILogger<IngestionService> logger
)
{
    private const int LatestIndicatorsReturned = 5;

    public async Task<IReadOnlyList<IngestResult>> IngestAllAsync(
        CancellationToken cancellationToken = default
    )
    {
        var results = new List<IngestResult>();

        // One unreachable source is reported in its own result instead of failing the whole run.
        foreach (var definition in IndicatorCatalog.All)
        {
            try
            {
                results.Add(await IngestIndicatorAsync(definition, cancellationToken));
            }
            catch (Exception error)
                when (error is HttpRequestException
                    || (error is TaskCanceledException && !cancellationToken.IsCancellationRequested))
            {
                logger.LogWarning(error, "Ingest failed for {Code}.", definition.Code);
                results.Add(new IngestResult(definition.Code, string.Empty, 0, 0, [], [], error.Message));
            }
        }

        return results;
    }

    public async Task<IngestResult> IngestIndicatorAsync(
        IndicatorDefinition definition,
        CancellationToken cancellationToken = default
    )
    {
        var indicatorType =
            await db.IndicatorTypes.FirstOrDefaultAsync(
                t => t.IndicatorCode == definition.Code,
                cancellationToken
            )
            ?? throw new InvalidOperationException(
                $"Indicator '{definition.Code}' is missing from indicator_type. Check 02_reference_data.sql."
            );

        var points = await FetchAsync(definition, cancellationToken);
        var source = await GetOrCreateSourceAsync(definition, cancellationToken);

        // Upsert by source, indicator and period so re-running an ingest updates values instead of duplicating them.
        var existingByPeriod = await db
            .ExternalIndicators.Where(i =>
                i.SourceId == source.SourceId && i.IndicatorCode == definition.Code
            )
            .ToDictionaryAsync(i => i.RelevantPeriod, cancellationToken);

        var savedIndicators = new List<ExternalIndicator>();

        foreach (var point in points)
        {
            var period = point.Year.ToString();

            if (!existingByPeriod.TryGetValue(period, out var indicator))
            {
                indicator = new ExternalIndicator
                {
                    SourceId = source.SourceId,
                    IndicatorCode = definition.Code,
                    RelevantPeriod = period,
                };
                db.ExternalIndicators.Add(indicator);
                existingByPeriod[period] = indicator;
            }

            indicator.IndicatorName = indicatorType.DisplayName;
            indicator.IndicatorValue = point.Value;
            indicator.MeasurementUnit = indicatorType.MeasurementUnit;
            indicator.ValidationStatus = Validate(point);
            indicator.CollectedAt = DateTime.UtcNow;
            savedIndicators.Add(indicator);
        }

        await db.SaveChangesAsync(cancellationToken);

        var alerts = await ruleEngine.EvaluateIndicatorRulesAsync(
            definition.Code,
            savedIndicators,
            cancellationToken
        );

        logger.LogInformation(
            "Ingest complete. Indicator={Code}, source={SourceId}, indicators={Count}, alerts={AlertCount}",
            definition.Code,
            source.SourceId,
            savedIndicators.Count,
            alerts.Count
        );

        return new IngestResult(
            definition.Code,
            source.SourceId,
            savedIndicators.Count,
            alerts.Count,
            savedIndicators
                .OrderByDescending(i => i.RelevantPeriod)
                .Take(LatestIndicatorsReturned)
                .ToList(),
            alerts
        );
    }

    // Reuses the data_source row for this API series, creating it on the first ingest.
    private async Task<DataSource> GetOrCreateSourceAsync(
        IndicatorDefinition definition,
        CancellationToken cancellationToken
    )
    {
        var source = await db.DataSources.FirstOrDefaultAsync(
            s => s.SourceIdentifier == definition.SourceIdentifier,
            cancellationToken
        );

        if (source is not null)
        {
            return source;
        }

        source = new DataSource
        {
            SourceName = definition.SourceName,
            SourceIdentifier = definition.SourceIdentifier,
            SourceType = SourceTypes.Api,
            TrustedStatus = TrustedStatuses.Trusted,
        };
        db.DataSources.Add(source);
        await db.SaveChangesAsync(cancellationToken);
        return source;
    }

    private async Task<IReadOnlyList<IndicatorPoint>> FetchAsync(
        IndicatorDefinition definition,
        CancellationToken cancellationToken
    ) =>
        definition.Provider switch
        {
            IndicatorProvider.WorldBank => await worldBankClient.FetchIndicatorAsync(
                definition.SeriesKey,
                cancellationToken: cancellationToken
            ),
            IndicatorProvider.Oecd => await oecdClient.FetchAnnualInflationAsync(
                definition.SeriesKey,
                cancellationToken: cancellationToken
            ),
            _ => throw new NotSupportedException($"Unknown provider {definition.Provider}."),
        };

    // Wrong country or impossible years are rejected; extreme but possible price changes
    // are flagged for review. Only validated values can raise rule alerts.
    private static string Validate(IndicatorPoint point)
    {
        if (
            point.CountryCode != IndicatorCatalog.CountryCode
            || point.Year < 1990
            || point.Year > DateTime.UtcNow.Year
        )
        {
            return ValidationStatuses.Rejected;
        }

        if (point.Value < -50m || point.Value > 100m)
        {
            return ValidationStatuses.Flagged;
        }

        return ValidationStatuses.Validated;
    }
}
