// Model linking an indicator to an income statement category it affects.
using System.Text.Json.Serialization;

namespace BudgetIngestion.Models;

public class IndicatorCategoryLink
{
    public string IndicatorCode { get; set; } = string.Empty;
    public string CategoryCode { get; set; } = string.Empty;

    [JsonIgnore]
    public IndicatorType? IndicatorType { get; set; }

    [JsonIgnore]
    public LineCategory? Category { get; set; }
}
