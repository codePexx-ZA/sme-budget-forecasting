// Model for an external data source (an API or dataset) that indicators are collected from.
using System.Text.Json.Serialization;

namespace BudgetIngestion.Models;

public class DataSource
{
    public string SourceId { get; set; } = Guid.NewGuid().ToString("N");
    public string SourceName { get; set; } = string.Empty;
    public string SourceIdentifier { get; set; } = string.Empty;
    public string SourceType { get; set; } = SourceTypes.Api;
    public string TrustedStatus { get; set; } = TrustedStatuses.Trusted;

    [JsonIgnore]
    public ICollection<ExternalIndicator> Indicators { get; set; } = new List<ExternalIndicator>();
}
