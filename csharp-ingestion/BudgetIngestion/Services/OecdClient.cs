// HTTP client that reads annual consumer price inflation series for South Africa from the OECD API.
using System.Globalization;

namespace BudgetIngestion.Services;

public record OecdIndicatorPoint(string CountryCode, string SeriesKey, int Year, decimal Value);

public class OecdClient(HttpClient httpClient, ILogger<OecdClient> logger)
{
    private const string BaseUrl =
        "https://sdmx.oecd.org/public/rest/data/OECD.SDD.TPS,DSD_PRICES@DF_PRICES_ALL,1.0";

    public async Task<IReadOnlyList<OecdIndicatorPoint>> FetchAnnualInflationAsync(
        string seriesKey,
        string countryCode = IndicatorCatalog.CountryCode,
        int years = IndicatorCatalog.HistoryYears,
        CancellationToken cancellationToken = default
    )
    {
        var startYear = DateTime.UtcNow.Year - years;
        // SDMX series key: annual (A) consumer price growth on the previous year (GY) for one country and CPI series.
        var url = $"{BaseUrl}/{countryCode}.A.N.CPI.PA.{seriesKey}.N.GY?startPeriod={startYear}";

        logger.LogInformation("Fetching OECD data from {Url}", url);

        using var request = new HttpRequestMessage(HttpMethod.Get, url);
        request.Headers.Add("Accept", "application/vnd.sdmx.data+csv; charset=utf-8");

        using var response = await httpClient.SendAsync(request, cancellationToken);
        response.EnsureSuccessStatusCode();

        var csv = await response.Content.ReadAsStringAsync(cancellationToken);
        var lines = csv.Split('\n', StringSplitOptions.RemoveEmptyEntries | StringSplitOptions.TrimEntries);

        if (lines.Length == 0)
        {
            return Array.Empty<OecdIndicatorPoint>();
        }

        // Columns are found by header name because the CSV column order is not guaranteed.
        var header = lines[0].Split(',');
        var areaIndex = Array.IndexOf(header, "REF_AREA");
        var periodIndex = Array.IndexOf(header, "TIME_PERIOD");
        var valueIndex = Array.IndexOf(header, "OBS_VALUE");

        if (areaIndex < 0 || periodIndex < 0 || valueIndex < 0)
        {
            throw new InvalidOperationException("Unexpected OECD response shape.");
        }

        var results = new List<OecdIndicatorPoint>();

        foreach (var line in lines.Skip(1))
        {
            var fields = line.Split(',');
            if (fields.Length <= valueIndex)
            {
                continue;
            }

            if (!int.TryParse(fields[periodIndex], out var year))
            {
                continue;
            }

            if (
                !decimal.TryParse(
                    fields[valueIndex],
                    NumberStyles.Float,
                    CultureInfo.InvariantCulture,
                    out var value
                )
            )
            {
                continue;
            }

            var country = fields[areaIndex];
            if (!string.Equals(country, countryCode, StringComparison.OrdinalIgnoreCase))
            {
                continue;
            }

            results.Add(new OecdIndicatorPoint(country, seriesKey, year, value));
        }

        logger.LogInformation(
            "Parsed {Count} OECD points for {Country}/{Series}",
            results.Count,
            countryCode,
            seriesKey
        );

        return results.OrderByDescending(p => p.Year).ToList();
    }
}
