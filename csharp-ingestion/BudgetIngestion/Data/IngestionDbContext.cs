// Entity Framework database context that maps the C# models to the PostgreSQL tables.
using BudgetIngestion.Models;
using Microsoft.EntityFrameworkCore;

namespace BudgetIngestion.Data;

public class IngestionDbContext(DbContextOptions<IngestionDbContext> options) : DbContext(options)
{
    public DbSet<DataSource> DataSources => Set<DataSource>();
    public DbSet<ExternalIndicator> ExternalIndicators => Set<ExternalIndicator>();
    public DbSet<RuleDefinition> RuleDefinitions => Set<RuleDefinition>();
    public DbSet<RuleAlert> RuleAlerts => Set<RuleAlert>();
    public DbSet<LineCategory> LineCategories => Set<LineCategory>();
    public DbSet<IndicatorType> IndicatorTypes => Set<IndicatorType>();
    public DbSet<IndicatorCategoryLink> IndicatorCategoryLinks => Set<IndicatorCategoryLink>();

    // Table names, column names and lengths must match database/init/01_schema.sql exactly.
    protected override void OnModelCreating(ModelBuilder modelBuilder)
    {
        modelBuilder.Entity<LineCategory>(entity =>
        {
            entity.ToTable("line_category");
            entity.HasKey(e => e.CategoryCode);
            entity.Property(e => e.CategoryCode).HasColumnName("category_code").HasMaxLength(50);
            entity.Property(e => e.DisplayName).HasColumnName("display_name").HasMaxLength(100);
            entity.Property(e => e.SortOrder).HasColumnName("sort_order");
            entity.Property(e => e.TeamManaged).HasColumnName("team_managed");
        });

        modelBuilder.Entity<IndicatorType>(entity =>
        {
            entity.ToTable("indicator_type");
            entity.HasKey(e => e.IndicatorCode);
            entity.Property(e => e.IndicatorCode).HasColumnName("indicator_code").HasMaxLength(50);
            entity.Property(e => e.DisplayName).HasColumnName("display_name").HasMaxLength(200);
            entity.Property(e => e.Description).HasColumnName("description").HasMaxLength(1000);
            entity
                .Property(e => e.MeasurementUnit)
                .HasColumnName("measurement_unit")
                .HasMaxLength(50);
            entity
                .Property(e => e.OfficialSource)
                .HasColumnName("official_source")
                .HasMaxLength(500);
        });

        modelBuilder.Entity<IndicatorCategoryLink>(entity =>
        {
            entity.ToTable("indicator_category_link");
            entity.HasKey(e => new { e.IndicatorCode, e.CategoryCode });
            entity.Property(e => e.IndicatorCode).HasColumnName("indicator_code").HasMaxLength(50);
            entity.Property(e => e.CategoryCode).HasColumnName("category_code").HasMaxLength(50);

            entity
                .HasOne(e => e.IndicatorType)
                .WithMany(t => t.CategoryLinks)
                .HasForeignKey(e => e.IndicatorCode);

            entity.HasOne(e => e.Category).WithMany().HasForeignKey(e => e.CategoryCode);
        });

        modelBuilder.Entity<DataSource>(entity =>
        {
            entity.ToTable("data_source");
            entity.HasKey(e => e.SourceId);
            entity.Property(e => e.SourceId).HasColumnName("source_id");
            entity.Property(e => e.SourceName).HasColumnName("source_name").HasMaxLength(200);
            entity
                .Property(e => e.SourceIdentifier)
                .HasColumnName("source_identifier")
                .HasMaxLength(500);
            entity.Property(e => e.SourceType).HasColumnName("source_type").HasMaxLength(50);
            entity.Property(e => e.TrustedStatus).HasColumnName("trusted_status").HasMaxLength(50);
        });

        modelBuilder.Entity<ExternalIndicator>(entity =>
        {
            entity.ToTable("external_indicator");
            entity.HasKey(e => e.IndicatorId);
            entity.Property(e => e.IndicatorId).HasColumnName("indicator_id");
            entity.Property(e => e.SourceId).HasColumnName("source_id");
            entity.Property(e => e.IndicatorCode).HasColumnName("indicator_code").HasMaxLength(50);
            entity.Property(e => e.IndicatorName).HasColumnName("indicator_name").HasMaxLength(200);
            entity
                .Property(e => e.IndicatorValue)
                .HasColumnName("indicator_value")
                .HasPrecision(18, 6);
            entity
                .Property(e => e.MeasurementUnit)
                .HasColumnName("measurement_unit")
                .HasMaxLength(50);
            entity
                .Property(e => e.RelevantPeriod)
                .HasColumnName("relevant_period")
                .HasMaxLength(20);
            entity
                .Property(e => e.ValidationStatus)
                .HasColumnName("validation_status")
                .HasMaxLength(50);
            entity.Property(e => e.CollectedAt).HasColumnName("collected_at");

            entity
                .HasOne(e => e.Source)
                .WithMany(s => s.Indicators)
                .HasForeignKey(e => e.SourceId)
                .OnDelete(DeleteBehavior.Cascade);

            entity.HasIndex(e => new { e.IndicatorCode, e.RelevantPeriod });
        });

        modelBuilder.Entity<RuleDefinition>(entity =>
        {
            entity.ToTable("rule_definition");
            entity.HasKey(e => e.RuleId);
            entity.Property(e => e.RuleId).HasColumnName("rule_id");
            entity.Property(e => e.RuleName).HasColumnName("rule_name").HasMaxLength(200);
            entity.Property(e => e.Condition).HasColumnName("condition").HasMaxLength(500);
            entity.Property(e => e.Action).HasColumnName("action").HasMaxLength(500);
            entity.Property(e => e.IndicatorCode).HasColumnName("indicator_code").HasMaxLength(50);
            entity
                .Property(e => e.ThresholdValue)
                .HasColumnName("threshold_value")
                .HasPrecision(18, 6);
            entity.Property(e => e.ActiveStatus).HasColumnName("active_status").HasMaxLength(50);
        });

        modelBuilder.Entity<RuleAlert>(entity =>
        {
            entity.ToTable("rule_alert");
            entity.HasKey(e => e.AlertId);
            entity.Property(e => e.AlertId).HasColumnName("alert_id");
            entity.Property(e => e.RuleId).HasColumnName("rule_id");
            entity.Property(e => e.UnitId).HasColumnName("unit_id");
            entity.Property(e => e.RunId).HasColumnName("run_id");
            entity.Property(e => e.Severity).HasColumnName("severity").HasMaxLength(50);
            entity.Property(e => e.Message).HasColumnName("message").HasMaxLength(1000);
            entity
                .Property(e => e.ResolutionStatus)
                .HasColumnName("resolution_status")
                .HasMaxLength(50);
            entity.Property(e => e.CreatedAt).HasColumnName("created_at");

            entity
                .HasOne(e => e.Rule)
                .WithMany(r => r.Alerts)
                .HasForeignKey(e => e.RuleId)
                .OnDelete(DeleteBehavior.Restrict); // a rule that has raised alerts cannot be deleted
        });
    }
}
