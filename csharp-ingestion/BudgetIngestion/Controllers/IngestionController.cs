// API endpoints that trigger fetching and storing external indicators, for all or one indicator.
using BudgetIngestion.Services;
using Microsoft.AspNetCore.Mvc;

namespace BudgetIngestion.Controllers;

[ApiController]
[Route("api/[controller]")]
public class IngestionController(IngestionService ingestionService) : ControllerBase
{
    /// <summary>
    /// Fetches every South African indicator in the catalog, validates and stores them, and evaluates rules.
    /// </summary>
    [HttpPost("run")]
    public async Task<IActionResult> IngestAll(CancellationToken cancellationToken)
    {
        var results = await ingestionService.IngestAllAsync(cancellationToken);
        return Ok(results);
    }

    /// <summary>
    /// Fetches one indicator by code: cpi_headline, cpi_core, electricity_price or fuel_price.
    /// </summary>
    [HttpPost("{code}")]
    public async Task<IActionResult> IngestOne(string code, CancellationToken cancellationToken)
    {
        var definition = IndicatorCatalog.Find(code);
        if (definition is null)
        {
            return NotFound(
                new
                {
                    error = $"Unknown indicator '{code}'.",
                    validCodes = IndicatorCatalog.All.Select(d => d.Code),
                }
            );
        }

        var result = await ingestionService.IngestIndicatorAsync(definition, cancellationToken);
        return Ok(result);
    }
}
