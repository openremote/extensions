# Ems Electricity Battery Asset

For detailed setup instructions, see the [EMS Setup](../EmsSetup.md) guide.  

The `Ems Electricity Battery Asset` must be a child asset of the `Ems Energy Optimisation Asset` to be available as flexible power to the EMS.

## Input attributes

### Set attributes:

| Attribute Name                 | Value Type       | Units | Description                                          |
|--------------------------------|------------------|-------|------------------------------------------------------|
| `allowCharging`                | Boolean          | -     | Allow the EMS to control charging of the battery.    |
| `allowDischarging`             | Boolean          | -     | Allow the EMS to control discharging of the battery. |
| `chargeEfficiency`             | Positive integer | %     | Efficiency of the charging process.                  |
| `chargePowerMaximum`           | Positive number  | kW    | Maximum allowed charging power.                      |
| `dischargeEfficiency`          | Positive integer | %     | Efficiency of the discharging process.               |
| `dischargePowerMaximum`        | Negative number  | kW    | Maximum allowed discharging power (negative).        |
| `energyCapacity`               | Positive number  | kWh   | Total energy capacity of the battery.                |
| `energyLevelPercentageMaximum` | Positive integer | %     | Maximum allowed energy level percentage.             |
| `energyLevelPercentageMinimum` | Positive integer | %     | Minimum allowed energy level percentage.             |

### Connect attributes:

| Attribute Name          | Value Type      | Units | Description                                                       |
|-------------------------|-----------------|-------|-------------------------------------------------------------------|
| `energyLevel`           | Positive number | kWh   | Current stored energy in the battery (Optional).                  |
| `energyLevelPercentage` | Positive number | %     | Current energy level percentage.                                  |
| `power`                 | Number          | kW    | Current power flow (positive = charging, negative = discharging). |

## Output attributes

| Attribute Name     | Value Type | Units | Description                                                  |
|--------------------|------------|-------|--------------------------------------------------------------|
| `connectionStatus` | Enum       | -     | Current connection status of the battery.                    |
| `powerSetpoint`    | Number     | kW    | Current active battery power setpoint calculated by the EMS. |
