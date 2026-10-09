// Checks ingested indicators against active rule thresholds and creates warning alerts.
using System.Globalization;
using BudgetIngestion.Data;
using BudgetIngestion.Models;
using Microsoft.EntityFrameworkCore;

namespace BudgetIngestion.Services;

public class RuleEngine(IngestionDbContext db, ILogger<RuleEngine> logger)
{
    public async Task<IReadOnlyList<RuleAlert>> EvaluateIndicatorRulesAsync(
        string indicatorCode,
        IEnumerable<ExternalIndicator> indicators,
        CancellationToken cancellationToken = default
    )
    {
        var rules = await db
            .RuleDefinitions.Where(r =>
                r.IndicatorCode == indicatorCode
                && r.ActiveStatus == "active"
                && r.ThresholdValue != null
            )
            .ToListAsync(cancellationToken);

        if (rules.Count == 0)
        {
            logger.LogInformation("No active rules for {Code}; no alerts evaluated.", indicatorCode);
            return Array.Empty<RuleAlert>();
        }

        var displayName = await db
            .IndicatorTypes.Where(t => t.IndicatorCode == indicatorCode)
            .Select(t => t.DisplayName)
            .FirstAsync(cancellationToken);

        var categoryNames = await db
            .IndicatorCategoryLinks.Where(l => l.IndicatorCode == indicatorCode)
            .OrderBy(l => l.Category!.SortOrder)
            .Select(l => l.Category!.DisplayName)
            .ToListAsync(cancellationToken);

        var categories = categoryNames.Count > 0 ? string.Join(", ", categoryNames) : "no linked categories";

        // Flagged or rejected values never raise alerts.
        var validated = indicators
            .Where(i => i.IndicatorCode == indicatorCode && i.ValidationStatus == "validated")
            .ToList();

        var alerts = new List<RuleAlert>();

        foreach (var rule in rules)
        {
            var threshold = rule.ThresholdValue!.Value;

            // Messages already raised for this rule are skipped, so re-running an ingest does not duplicate alerts.
            var seen = (
                await db
                    .RuleAlerts.Where(a => a.RuleId == rule.RuleId)
                    .Select(a => a.Message)
                    .ToListAsync(cancellationToken)
            ).ToHashSet();

            foreach (var indicator in validated)
            {
                if (indicator.IndicatorValue <= threshold)
                {
                    continue;
                }

                var message = string.Create(
                    CultureInfo.InvariantCulture,
                    $"South Africa {displayName} for {indicator.RelevantPeriod} "
                        + $"is {indicator.IndicatorValue:F2}% (threshold {threshold:F1}%). "
                        + $"Review forecasts for: {categories}."
                );

                if (!seen.Add(message))
                {
                    continue;
                }

                var alert = new RuleAlert
                {
                    RuleId = rule.RuleId,
                    Severity = "warning",
                    Message = message,
                    ResolutionStatus = "open",
                    CreatedAt = DateTime.UtcNow,
                };

                db.RuleAlerts.Add(alert);
                alerts.Add(alert);

                logger.LogInformation("Rule alert created: {Message}", message);
            }
        }

        if (alerts.Count > 0)
        {
            await db.SaveChangesAsync(cancellationToken);
        }

        return alerts;
    }
}
