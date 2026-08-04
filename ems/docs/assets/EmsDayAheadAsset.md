# Ems Day Ahead Asset

For detailed setup instructions, see the [EMS Setup](../EmsSetup.md) guide.

The `Ems Day Ahead Asset` must be a child asset of the `Ems Energy Optimisation Asset` to be available to the EMS. This asset is used to store day-ahead tariff forecasts at a user-specified time of day.

## Input attributes

### Set attributes:

| Attribute Name               | Value Type | Units | Description                                                                                                                                                                                                                              |
|------------------------------|------------|-------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `collectTimeForecasts`       | Text       | -     | Sets the time of day at which the forecast is collected from the `Ems Energy Optimisation Asset` for the `tariffExport` and `tariffImport` attributes. Example: 10:00                                                                    |
| `useTariffDayAheadForecasts` | Boolean    | -     | When enabled, `tariffExportDayAheadForecast` and `tariffImportDayAheadForecast` are used as the day-ahead tariffs for optimisation instead of the `tariffExport` and `tariffImport` attributes from the `Ems Energy Optimisation Asset`. |

## Output attributes

| Attribute Name                 | Value Type | Units | Description                                                                                                                                                                                                                                                                           |
|--------------------------------|------------|-------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `lastUpdateForecasts`          | Text       | -     | Displays when the forecasts for the `Ems Day Ahead Asset` were last updated                                                                                                                                                                                                           |
| `tariffExportDayAheadForecast` | Number     | €/kWh | Stores the `tariffExport` attribute from the `Ems Energy Optimisation Asset` for the day-ahead at the time set by `collectTimeForecasts`. (Tariffs are stored directly in the database. The attribute value itself remains empty to prevent duplicate data points from being stored.) |
| `tariffImportDayAheadForecast` | Number     | €/kWh | Stores the `tariffImport` attribute from the `Ems Energy Optimisation Asset` for the day-ahead at the time set by `collectTimeForecasts`. (Tariffs are stored directly in the database. The attribute value itself remains empty to prevent duplicate data points from being stored.) |
