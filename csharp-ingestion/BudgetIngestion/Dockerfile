# Builds and publishes the C# ingestion API into a container image.
FROM mcr.microsoft.com/dotnet/sdk:10.0 AS build
WORKDIR /src
COPY BudgetIngestion/BudgetIngestion.csproj BudgetIngestion/
RUN dotnet restore BudgetIngestion/BudgetIngestion.csproj
COPY BudgetIngestion/ BudgetIngestion/
RUN dotnet publish BudgetIngestion/BudgetIngestion.csproj -c Release -o /app/publish /p:UseAppHost=false

FROM mcr.microsoft.com/dotnet/aspnet:10.0 AS final
WORKDIR /app
COPY --from=build /app/publish .
ENV ASPNETCORE_URLS=http://+:8080
EXPOSE 8080
ENTRYPOINT ["dotnet", "BudgetIngestion.dll"]
