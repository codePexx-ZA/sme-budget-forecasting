// Catalog of the South African indicators to ingest and which API and series each one is fetched from.
namespace BudgetIngestion.Services;

public enum IndicatorProvider
{
    WorldBank,
    Oecd,
}

public record IndicatorDefinition(
    string Code,
    IndicatorProvider Provider,
    string SeriesKey,
    string SourceName,
    string SourceIdentifier
);

// One annual value returned by either API client.
public record IndicatorPoint(string CountryCode, int Year, decimal Value);

// Only fetch settings live here. Every Code must also exist in indicator_type; names,
// linked categories and rule thresholds are reference data in 02_reference_data.sql.
public static class IndicatorCatalog
{
    public const string CountryCode = "ZAF";

    // 5 years of business history plus the current year.
    public const int HistoryYears = 6;

    public static readonly IReadOnlyList<IndicatorDefinition> All =
    [
        new(
            "cpi_headline",
            IndicatorProvider.WorldBank,
            "FP.CPI.TOTL.ZG",
            "World Bank Open Data",
            "worldbank:FP.CPI.TOTL.ZG:ZAF"
        ),
        new(
            "cpi_core",
            IndicatorProvider.Oecd,
            "_TXCP01_NRG",
            "OECD Consumer Prices",
            "oecd:DF_PRICES_ALL:ZAF:_TXCP01_NRG"
        ),
        new(
            "electricity_price",
            IndicatorProvider.Oecd,
            "CP045",
            "OECD Consumer Prices",
            "oecd:DF_PRICES_ALL:ZAF:CP045"
        ),
        new(
            "fuel_price",
            IndicatorProvider.Oecd,
            "CP0722",
            "OECD Consumer Prices",
            "oecd:DF_PRICES_ALL:ZAF:CP0722"
        ),
    ];

    public static IndicatorDefinition? Find(string code) =>
        All.FirstOrDefault(d => string.Equals(d.Code, code, StringComparison.OrdinalIgnoreCase));
}
