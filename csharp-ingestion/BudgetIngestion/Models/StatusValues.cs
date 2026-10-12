// Allowed status values for the text columns this service writes or filters on.
// They must match the CHECK constraints in database/init/01_schema.sql.
namespace BudgetIngestion.Models;

public static class ValidationStatuses
{
    public const string Pending = "pending";
    public const string Validated = "validated";
    public const string Flagged = "flagged";
    public const string Rejected = "rejected";
}

public static class ActiveStatuses
{
    public const string Active = "active";
}

public static class AlertSeverities
{
    public const string Info = "info";
    public const string Warning = "warning";
}

public static class ResolutionStatuses
{
    public const string Open = "open";
    public const string Resolved = "resolved";
}

public static class SourceTypes
{
    public const string Api = "api";
}

public static class TrustedStatuses
{
    public const string Trusted = "trusted";
}
