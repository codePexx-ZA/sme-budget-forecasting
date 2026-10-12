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
                && r.ActiveStatus == ActiveStatuses.Active
                && r.ThresholdValue != null
            )
            .ToListAsync(cancellationToken);

        if (rules.Count == 0)
        {
            logger.LogInformation("No active rules for {Code}; no alerts evaluated.", indicatorCode);
            return [];
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
            .Where(i => i.IndicatorCode == indicatorCode && i.ValidationStatus == ValidationStatuses.Validated)
            .ToList();

        var alerts = new List<RuleAlert>();

        foreach (var rule in rules)
        {
            var threshold = rule.ThresholdValue!.Value;

            // One unresolved alert per rule and year: a revised value updates it, a value back under the threshold resolves it.
            var unresolved = await db
                .RuleAlerts.Where(a =>
                    a.RuleId == rule.RuleId && a.ResolutionStatus != ResolutionStatuses.Resolved
                )
                .ToListAsync(cancellationToken);

            foreach (var indicator in validated)
            {
                var prefix = $"South Africa {displayName} for {indicator.RelevantPeriod} ";
                var existing = unresolved.FirstOrDefault(a => a.Message.StartsWith(prefix, StringComparison.Ordinal));

                if (indicator.IndicatorValue <= threshold)
                {
                    if (existing is not null)
                    {
                        existing.ResolutionStatus = ResolutionStatuses.Resolved;
                        unresolved.Remove(existing);
                        logger.LogInformation("Rule alert resolved: {Message}", existing.Message);
                    }
                    continue;
                }

                var message = string.Create(
                    CultureInfo.InvariantCulture,
                    $"{prefix}is {indicator.IndicatorValue:F2}% (threshold {threshold:F1}%). "
                        + $"Review forecasts for: {categories}."
                );

                if (existing is not null)
                {
                    existing.Message = message;
                    continue;
                }

                var alert = new RuleAlert
                {
                    RuleId = rule.RuleId,
                    Severity = AlertSeverities.Warning,
                    Message = message,
                    ResolutionStatus = ResolutionStatuses.Open,
                    CreatedAt = DateTime.UtcNow,
                };

                db.RuleAlerts.Add(alert);
                unresolved.Add(alert);
                alerts.Add(alert);

                logger.LogInformation("Rule alert created: {Message}", message);
            }
        }

        await db.SaveChangesAsync(cancellationToken);

        return alerts;
    }
}
