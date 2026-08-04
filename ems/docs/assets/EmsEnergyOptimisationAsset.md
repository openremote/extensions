# Ems Energy Optimisation Asset

For detailed setup instructions, see the [EMS Setup](../EmsSetup.md) guide.

The `Ems Energy Optimisation Asset` serves as the parent asset of the Energy Management System (EMS). Other assets that are part of the EMS extension, such as the `Ems Electricity Battery Asset`, must be added as child assets before they can be used by the EMS.

## Input attributes

### Set attributes:

| Attribute Name                                | Value Type | Units | Description                                                                                                                                                                                                        |
| --------------------------------------------- | ---------- | ----- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `enableDetailedLogging`                       | Boolean    | -     | Enables detailed logging (optimisation method specific).                                                                                                                                                           |
| `generatePowerLimitMaximumProfileManualInput` | Boolean    | -     | Generates maximum power limit profile based on the `powerLimitMaximumInput` value.                                                                                                                                 |
| `generatePowerLimitMinimumProfileManualInput` | Boolean    | -     | Generates minimum power limit profile based on the `powerLimitMinimumInput` value.                                                                                                                                 |
| `optimisationDisabled`                        | Boolean    | -     | Disables optimisation logic, including power limit profiles.                                                                                                                                                       |
| `optimisationMethod`                          | Enum       | -     | Selects the optimisation strategy.                                                                                                                                                                                 |
| `powerLimitMaximumInput`                      | Number     | kW    | Power limit maximum, used to generate `powerLimitMaximumProfileManualInput`.                                                                                                                                       |
| `powerLimitMaximumProfileManualInput`         | Text       | -     | Multiline input for manual maximum power limit profile. First, generate the general profile using `generatePowerLimitMaximumProfileManualInput`. Then, adjust the power limits for individual 15-minute intervals. |
| `powerLimitMinimumInput`                      | Number     | kW    | Power limit minimum, used to generate `powerLimitMinimumProfileManualInput`.                                                                                                                                       |
| `powerLimitMinimumProfileManualInput`         | Text       | -     | Multiline input for manual minimum power limit profile. First, generate the general profile using `generatePowerLimitMinimumProfileManualInput`. Then, adjust the power limits for individual 15-minute intervals. |

### Connect attributes:

The exact way in which the following attributes are connected is left to the user. This is intentional and allows maximum flexibility when configuring the EMS based on the available power meter readings. The last column provides an example of how each attribute can be connected. Attribute values can be calculated using either _Flow rules_ or, for more advanced use cases, _Groovy rules_. Ensure that the `Rule state` configuration item is added to all attributes involved in the calculations.

| Attribute Name      | Value Type | Units | Description                                                         | Connection example                                                                                         |
| ------------------- | ---------- | ----- | ------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| `energyExportTotal` | Number     | kWh   | Total energy exported                                               | Connect: main meter. (Not required for EMS optimisation)                                                   |
| `energyImportTotal` | Number     | kWh   | Total energy imported                                               | Connect: main meter. (Not required for EMS optimisation)                                                   |
| `powerConsumption`  | Number     | kW    | Current power consumption.                                          | Calculate: sum of power consumer assets or, powerConsumption = powerNet - powerFlexible - powerProduction. |
| `powerFlexible`     | Number     | kW    | Current power from flexible assets, such as batteries, charges etc. | Calculate: sum of power flexible assets.                                                                   |
| `powerNet`          | Number     | kW    | Current net power.                                                  | Connect: main meter.                                                                                       |
| `powerProduction`   | Number     | kW    | Current power from power producing assets, such as solar panels.    | Calculate: sum of power production assets.                                                                 |
| `tariffExport`      | Number     | €/kWh | Export tariff used by the EMS for optimisation                      | Connect: ENTSO-E agent for EPEX spot prices in your region.                                                |
| `tariffImport`      | Number     | €/kWh | Import tariff used by the EMS for optimisation                      | Connect: ENTSO-E agent for EPEX spot prices in your region.                                                |

## Output attributes

| Attribute Name                   | Value Type | Units | Description                                                                                                                                                          |
| -------------------------------- | ---------- | ----- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `advancedSettingsAttributes`     | Text       | -     | List of advanced settings attributes that can be added manually for the selected `optimisationMethod` (Only visible in `MODIFY` mode, optimisation method specific). |
| `powerLimitMaximumProfileManual` | Number     | kW    | Current manual maximum power limit based on `powerLimitMaximumProfileManualInput`.                                                                                   |
| `powerLimitMaximumProfileTotal`  | Number     | kW    | Current total maximum power limit, calculated as the sum of the manual power limit and the GOPACS order power limit. This value is used for optimisation.            |
| `powerLimitMinimumProfileManual` | Number     | kW    | Current manual minimum power limit based on `powerLimitMinimumProfileManualInput`.                                                                                   |
| `powerLimitMinimumProfileTotal`  | Number     | kW    | Current total minimum power limit, calculated as the sum of the manual power limit and the GOPACS order power limit. This value is used for optimisation.            |
