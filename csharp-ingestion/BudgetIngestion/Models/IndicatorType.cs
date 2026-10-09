// Model for a tracked indicator type, such as headline CPI or fuel price change.
using System.Text.Json.Serialization;

namespace BudgetIngestion.Models;

public class IndicatorType
{
    public string IndicatorCode { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public string? Description { get; set; }
    public string MeasurementUnit { get; set; } = "percent";
    public string OfficialSource { get; set; } = string.Empty;

    [JsonIgnore]
    public ICollection<IndicatorCategoryLink> CategoryLinks { get; set; } =
        new List<IndicatorCategoryLink>();
}
