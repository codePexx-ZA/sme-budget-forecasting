// Model for an income statement category, such as revenue or electricity.
namespace BudgetIngestion.Models;

public class LineCategory
{
    public string CategoryCode { get; set; } = string.Empty;
    public string DisplayName { get; set; } = string.Empty;
    public short SortOrder { get; set; }
    public bool TeamManaged { get; set; } // true for revenue and employee costs: set by the business, not by indicators
}
