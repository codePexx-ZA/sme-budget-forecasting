// HTTP client that reads annual indicator values for South Africa from the World Bank API.
using System.Text.Json;

namespace BudgetIngestion.Services;

public record WorldBankIndicatorPoint(
    string CountryCode,
    string IndicatorCode,
    string IndicatorName,
    int Year,
    decimal Value
);

public class WorldBankClient(HttpClient httpClient, ILogger<WorldBankClient> logger)
{
    public const string DefaultIndicatorCode = "FP.CPI.TOTL.ZG";
    public const string DefaultCountryCode = "ZAF";

    public async Task<IReadOnlyList<WorldBankIndicatorPoint>> FetchIndicatorAsync(
        string countryCode = DefaultCountryCode,
        string indicatorCode = DefaultIndicatorCode,
        int years = IndicatorCatalog.HistoryYears,
        CancellationToken cancellationToken = default
    )
    {
        var endYear = DateTime.UtcNow.Year;
        var startYear = endYear - years;
        var url =
            $"https://api.worldbank.org/v2/country/{countryCode}/indicator/{indicatorCode}?format=json&date={startYear}:{endYear}&per_page=100";

        logger.LogInformation("Fetching World Bank data from {Url}", url);

        using var response = await httpClient.GetAsync(url, cancellationToken);
        response.EnsureSuccessStatusCode();

        await using var stream = await response.Content.ReadAsStreamAsync(cancellationToken);
        using var document = await JsonDocument.ParseAsync(
            stream,
            cancellationToken: cancellationToken
        );

        // The API returns [paging metadata, rows]; rows is null when no data exists for the range.
        if (
            document.RootElement.ValueKind != JsonValueKind.Array
            || document.RootElement.GetArrayLength() < 2
        )
        {
            throw new InvalidOperationException("Unexpected World Bank response shape.");
        }

        var rows = document.RootElement[1];
        if (rows.ValueKind == JsonValueKind.Null)
        {
            return Array.Empty<WorldBankIndicatorPoint>();
        }

        var results = new List<WorldBankIndicatorPoint>();

        foreach (var row in rows.EnumerateArray())
        {
            // Years the World Bank has not published yet come back with a null value and are skipped.
            if (
                !row.TryGetProperty("value", out var valueElement)
                || valueElement.ValueKind == JsonValueKind.Null
            )
            {
                continue;
            }

            if (!row.TryGetProperty("date", out var dateElement))
            {
                continue;
            }

            if (!int.TryParse(dateElement.GetString(), out var year))
            {
                continue;
            }

            var value = valueElement.GetDecimal();
            var indicatorName =
                row.GetProperty("indicator").GetProperty("value").GetString() ?? indicatorCode;
            var country = row.GetProperty("countryiso3code").GetString() ?? countryCode;

            if (!string.Equals(country, countryCode, StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            results.Add(
                new WorldBankIndicatorPoint(country, indicatorCode, indicatorName, year, value)
            );
        }

        logger.LogInformation(
            "Parsed {Count} World Bank points for {Country}/{Indicator}",
            results.Count,
            countryCode,
            indicatorCode
        );

        return results.OrderByDescending(p => p.Year).ToList();
    }
}
