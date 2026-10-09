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
    IReadOnlyList<RuleAlert> Alerts
);

public record IndicatorPoint(string CountryCode, int Year, decimal Value);

public class IngestionService(
    IngestionDbContext db,
    WorldBankClient worldBankClient,
    OecdClient oecdClient,
    RuleEngine ruleEngine,
    ILogger<IngestionService> logger
)
{
    public async Task<IReadOnlyList<IngestResult>> IngestAllAsync(
        CancellationToken cancellationToken = default
    )
    {
        var results = new List<IngestResult>();

        foreach (var definition in IndicatorCatalog.All)
        {
            results.Add(await IngestIndicatorAsync(definition, cancellationToken));
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

        // Reuse the data_source row for this API series, creating it on the first ingest.
        var source = await db.DataSources.FirstOrDefaultAsync(
            s => s.SourceIdentifier == definition.SourceIdentifier,
            cancellationToken
        );

        if (source is null)
        {
            source = new DataSource
            {
                SourceName = definition.SourceName,
                SourceIdentifier = definition.SourceIdentifier,
                SourceType = "api",
                TrustedStatus = "trusted",
            };
            db.DataSources.Add(source);
            await db.SaveChangesAsync(cancellationToken);
        }

        var savedIndicators = new List<ExternalIndicator>();

        foreach (var point in points)
        {
            var period = point.Year.ToString();

            // Upsert by source, indicator and period so re-running an ingest updates values instead of duplicating them.
            var indicator = await db.ExternalIndicators.FirstOrDefaultAsync(
                i =>
                    i.SourceId == source.SourceId
                    && i.IndicatorCode == definition.Code
                    && i.RelevantPeriod == period,
                cancellationToken
            );

            if (indicator is null)
            {
                indicator = new ExternalIndicator
                {
                    SourceId = source.SourceId,
                    IndicatorCode = definition.Code,
                    RelevantPeriod = period,
                };
                db.ExternalIndicators.Add(indicator);
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
            savedIndicators.OrderByDescending(i => i.RelevantPeriod).Take(5).ToList(),
            alerts
        );
    }

    private async Task<IReadOnlyList<IndicatorPoint>> FetchAsync(
        IndicatorDefinition definition,
        CancellationToken cancellationToken
    )
    {
        switch (definition.Provider)
        {
            case IndicatorProvider.WorldBank:
                var worldBankPoints = await worldBankClient.FetchIndicatorAsync(
                    IndicatorCatalog.CountryCode,
                    definition.SeriesKey,
                    cancellationToken: cancellationToken
                );
                return worldBankPoints
                    .Select(p => new IndicatorPoint(p.CountryCode, p.Year, p.Value))
                    .ToList();

            case IndicatorProvider.Oecd:
                var oecdPoints = await oecdClient.FetchAnnualInflationAsync(
                    definition.SeriesKey,
                    cancellationToken: cancellationToken
                );
                return oecdPoints
                    .Select(p => new IndicatorPoint(p.CountryCode, p.Year, p.Value))
                    .ToList();

            default:
                throw new NotSupportedException($"Unknown provider {definition.Provider}.");
        }
    }

    // Wrong country or impossible years are rejected; extreme but possible price changes
    // are flagged for review. Only "validated" values can raise rule alerts.
    private static string Validate(IndicatorPoint point)
    {
        if (point.CountryCode != IndicatorCatalog.CountryCode)
        {
            return "rejected";
        }

        if (point.Year < 1990 || point.Year > DateTime.UtcNow.Year)
        {
            return "rejected";
        }

        if (point.Value < -50m || point.Value > 100m)
        {
            return "flagged";
        }

        return "validated";
    }
}
