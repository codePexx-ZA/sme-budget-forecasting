// Model for one collected indicator value for a specific period, with its validation status.
using System.Text.Json.Serialization;

namespace BudgetIngestion.Models;

public class ExternalIndicator
{
    public string IndicatorId { get; set; } = Guid.NewGuid().ToString("N");
    public string SourceId { get; set; } = string.Empty;
    public string IndicatorCode { get; set; } = string.Empty;
    public string IndicatorName { get; set; } = string.Empty;
    public decimal IndicatorValue { get; set; }
    public string MeasurementUnit { get; set; } = string.Empty;
    public string RelevantPeriod { get; set; } = string.Empty; // calendar year as text, e.g. "2024"
    public string ValidationStatus { get; set; } = ValidationStatuses.Pending;
    public DateTime CollectedAt { get; set; } = DateTime.UtcNow;

    [JsonIgnore]
    public DataSource? Source { get; set; }
}
