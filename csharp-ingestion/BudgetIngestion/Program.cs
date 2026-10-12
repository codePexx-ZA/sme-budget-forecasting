// Application entry point: registers services, the database context and API controllers, then starts the web API.
using BudgetIngestion.Data;
using BudgetIngestion.Services;
using Microsoft.EntityFrameworkCore;

var builder = WebApplication.CreateBuilder(args);

builder.Services.AddControllers();
builder.Services.AddEndpointsApiExplorer();
builder.Services.AddSwaggerGen();

// EF Core only maps existing tables; the schema and reference data come from database/init SQL scripts.
builder.Services.AddDbContext<IngestionDbContext>(options =>
    options.UseNpgsql(builder.Configuration.GetConnectionString("DefaultConnection"))
);

builder.Services.AddHttpClient<WorldBankClient>().ConfigureHttpClient(c => c.Timeout = TimeSpan.FromSeconds(20));
builder.Services.AddHttpClient<OecdClient>().ConfigureHttpClient(c => c.Timeout = TimeSpan.FromSeconds(20));
builder.Services.AddScoped<RuleEngine>();
builder.Services.AddScoped<IngestionService>();

var app = builder.Build();

if (app.Environment.IsDevelopment())
{
    app.UseSwagger();
    app.UseSwaggerUI();
}

app.MapControllers();

app.Run();
