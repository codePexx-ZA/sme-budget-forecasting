// API endpoints for reading stored indicators, the indicator catalog, categories and rule alerts.
using BudgetIngestion.Data;
using BudgetIngestion.Models;
using BudgetIngestion.Services;
using Microsoft.AspNetCore.Mvc;
using Microsoft.EntityFrameworkCore;

namespace BudgetIngestion.Controllers;

[ApiController]
[Route("api/[controller]")]
public class IndicatorsController(IngestionDbContext db) : ControllerBase
{
    [HttpGet]
    public async Task<IActionResult> GetLatest(
        [FromQuery] string? code = null,
        [FromQuery] int take = 20,
        CancellationToken cancellationToken = default
    )
    {
        take = Math.Clamp(take, 1, 100);

        var query = db.ExternalIndicators.AsNoTracking();
        if (!string.IsNullOrWhiteSpace(code))
        {
            query = query.Where(i => i.IndicatorCode == code);
        }

        var items = await query
            .OrderByDescending(i => i.RelevantPeriod)
            .ThenBy(i => i.IndicatorCode)
            .Take(take)
            .Select(i => new
            {
                i.IndicatorId,
                i.SourceId,
                i.IndicatorCode,
                i.IndicatorName,
                i.IndicatorValue,
                i.MeasurementUnit,
                i.RelevantPeriod,
                i.ValidationStatus,
                i.CollectedAt,
            })
            .ToListAsync(cancellationToken);

        return Ok(items);
    }

    [HttpGet("catalog")]
    public async Task<IActionResult> GetCatalog(CancellationToken cancellationToken)
    {
        var types = await db
            .IndicatorTypes.AsNoTracking()
            .Select(t => new
            {
                t.IndicatorCode,
                t.DisplayName,
                t.Description,
                t.MeasurementUnit,
                t.OfficialSource,
                Categories = t.CategoryLinks.Select(l => l.Category!.DisplayName).ToList(),
            })
            .ToListAsync(cancellationToken);

        var thresholds = await db
            .RuleDefinitions.AsNoTracking()
            .Where(r => r.IndicatorCode != null && r.ActiveStatus == ActiveStatuses.Active)
            .Select(r => new { r.IndicatorCode, r.ThresholdValue })
            .ToListAsync(cancellationToken);

        var typesByCode = types.ToDictionary(t => t.IndicatorCode);

        // Combines the code-side fetch settings with names, categories and thresholds stored in the database.
        var catalog = IndicatorCatalog.All.Select(d =>
        {
            var type = typesByCode.GetValueOrDefault(d.Code);
            return new
            {
                d.Code,
                type?.DisplayName,
                type?.Description,
                type?.MeasurementUnit,
                Provider = d.Provider.ToString(),
                d.SeriesKey,
                type?.OfficialSource,
                Threshold = thresholds.FirstOrDefault(r => r.IndicatorCode == d.Code)?.ThresholdValue,
                LinkedCategories = type?.Categories ?? [],
            };
        });

        return Ok(catalog);
    }

    [HttpGet("categories")]
    public async Task<IActionResult> GetCategories(CancellationToken cancellationToken) =>
        Ok(
            await db
                .LineCategories.AsNoTracking()
                .OrderBy(c => c.SortOrder)
                .ToListAsync(cancellationToken)
        );

    [HttpGet("alerts")]
    public async Task<IActionResult> GetAlerts(
        [FromQuery] int take = 20,
        CancellationToken cancellationToken = default
    )
    {
        take = Math.Clamp(take, 1, 100);

        var alerts = await db
            .RuleAlerts.AsNoTracking()
            .OrderByDescending(a => a.CreatedAt)
            .Take(take)
            .Select(a => new
            {
                a.AlertId,
                a.RuleId,
                a.Rule!.IndicatorCode,
                a.Severity,
                a.Message,
                a.ResolutionStatus,
                a.CreatedAt,
            })
            .ToListAsync(cancellationToken);

        return Ok(alerts);
    }
}
