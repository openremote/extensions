# EMS setup

This is a detailed instruction on how to set up an **Energy Management System (EMS)** using the EMS extension.

⚠️ When encountering problems, always check the logs.

### 1) Create EMS with manual power limit profiles

1. Create asset:
   - Go to the `Assets` page and click the `+` icon
   - Select the `Ems Energy Optimisation Asset` and name it (**e.g. EMS**)
   - Click `Add` to create the asset

2. Generate power limit profiles:
   - Set a value in the `Power limit maximum input (kW)` field (**e.g. 1000**)
   - Click the `Generate power limit maximum profile manual input`
   - Set a value in the `Power limit minimum input (kW)` field (**e.g. -1000**)
   - Click the `Generate power limit minimum profile manual input`

This will generate a power limit profile with 15-minute intervals for each day of the week in the `Power Limit Maximum Profile manual input` and `Power Limit Minimum Profile manual input` fields. Individual interval values can be adjusted manually. Once all modifications have been made, the updated profile can be saved by clicking the submit arrow.

Alternatively, the values can be imported and modified in a spreadsheet application:

- Copy the generated profile into a text editor, and save it as a `.csv` file
- Open the CSV file in a spreadsheet application, such as Google Sheets
- Make any required changes
- Export the updated profile as a `.csv` file (ensure that the values are `,` comma-separated)
- Open the exported CSV file in a text editor, and copy its contents into the respective `Power Limit Maximum Profile manual input` or `Power Limit Minimum Profile manual input` field. Click the submit arrow to save the changes

**Note**: Configuration of either only a maximum profile, only a minimum profile, or both profiles is supported. The optimisation routine does not require both profiles to be defined.

3. Display power limit profiles:
   - Go to the `Insights` page and click the `+` icon
   - Give the new dashboard a name (**e.g. Power limit profiles**)
   - Create a `Line Chart` by dragging it onto the canvas
   - Add the `Power limit maximum profile manual` and `Power limit minimum profile manual` attributes using the `+ Attribute` button in the Line Chart settings
   - Set the **Time** `Default timeframe` to **Week**
   - Click `Save`, then `View` to exit the `Modify` mode

You now have a basic line chart showing your power limit profiles for this week.

### 2) Add GOPACS

**Note:** Only add a GOPACS asset if you want to use GOPACS in your EMS.

1. Set up GOPACS:
   - Set up GOPACS following the instructions in the [GOPACS Integration](docs/Gopacs.md) guide

2. Create asset:
   - Go to the `Assets` page, select the **EMS** asset created in section 1 and click the `+` icon
   - Select the `Ems GOPACS Asset` and name it (**e.g. GOPACS**)
   - Check if the parent is the **EMS** asset and click `Add` to create the asset
   - Fill in the `Contracted EAN` field

After completing the steps above, GOPACS flex requests can be received and are automatically validated and processed into flex orders. The flex orders are combined with the **manual power limit profiles** to create the `Power limit maximum profile total (kW)` and `Power limit minimum profile total (kW)` on the **EMS** asset.

**Note:** GOPACS power limits can be used without setting manual power limits.

### 3) Add battery

1. Create asset:
   - Go to the `Assets` page, select the **EMS** asset created in section 1 and click the `+` icon
   - Select the `Ems electricity battery asset` and name it (**e.g. Battery 1**)
   - Check if the parent is the **EMS** asset and click `Add` to create the asset
   - Set values for the following attributes:
     - `Charge efficiency (%)` (**e.g. 90**)
     - `Discharge efficiency (%)` (**e.g. 90**)
     - `Charge power maximum (kW)` (**e.g. 100**)
     - `Discharge power maximum (kW)` (**e.g. -100**)
     - `Energy capacity (kWh)` (**e.g. 400**)
     - `Energy level percentage maximum (kW)` (**e.g. 90**)
     - `Energy level percentage minimum (kW)` (**e.g. 10**)
   - Connect the following attributes with your battery:
     - Write to attribute
       - `Energy level (kWh)` (Optional)
       - `Energy level percentage (%)`
       - `Power (kW)`
     - Read from attribute
       - `Power setpoint (kW)`
   - Select the `Allow charging` and `Allow discharging` checkboxes to allow battery control by the EMS.

Repeat the above steps to add multiple batteries to the EMS.

**Note:** The `Ems Electricity Battery Asset` must be a child asset of the `Ems Energy Optimisation Asset` to be available as flexible power to the EMS.

For more information about battery attributes, see the [Ems Electricity Battery Asset](assets/EmsElectricityBatteryAsset.md).

### 4) Connect EMS power attributes

1. Connect attributes:
   - Go to the `Assets` page, select the **EMS** asset created in section 1
   - Connect the `Power net (kW)` attribute with your main power meter
   - Connect the `Power flexible (kW)` attribute with the sum of the `Power (kW)` attribute of the battery assets created in section 3 using a Flow rule or a custom Groovy rule
   - (Optional) Connect the `Power production (kW)`attribute with the sum of the `Power (kW)` attribute of power producing assets, such as solar panels, using a Flow rule or a custom Groovy rule
   - Calculate the `Power consumption (kW)` = `Power net (kW)` - `Power flexible (kW)` - `Power production (kW)` using a Flow rule or a custom Groovy rule

For more information on how to use rules, see [Rules and Forecasting](https://docs.openremote.io/docs/category/rules-and-forecasting).

### 5) Add power forecasts

1. Add power consumption forecast:
   - Go to the `Assets` page, select the **EMS** asset created in section 1
   - Click `Modify` and select the `powerConsumption` attribute
   - Click `Add configuration items`, select `Forecast` and click `Add`
   - Click the new Forecast field and select `Forecast Configuration Weighted Exponential Average`
   - Set the following values:
     - Forecast Count = 672
     - Forecast Period = PT15M
     - Past Count = 3
     - Past Period = P7D

Note: You can use a different forecast methods such as the `ML Forecasting Service`or your custom forecast method for the power consumption forecast.

Step 2 is only required when there are power producing assets.

2. (Optional) Add power production forecast:
   - Link the sum of the `Power (kW)` attribute forecasts of power producing assets, such as solar panels, using a custom Groovy rule. OpenRemote currently does not provide built-in functionality for linking/summing forecasts.

3. (Optional) Add power flexible forecast:
   - Link the sum of the `Power (kW)` attribute forecasts of `Ems Electricity Battery Asset` assets using a custom Groovy rule. OpenRemote currently does not provide built-in functionality for linking/summing forecasts.

4. (Optional) Add power net forecast:
   - Calculate the `Power Net (kW)` = `Power consumption (kW)` + `Power flexible (kW)` + `Power production (kW)` forecast using a custom Groovy rule. OpenRemote currently does not provide built-in functionality for linking/summing forecasts.

### 6) Add tariffs forecasts

Add the [ENTSO-E extension](https://github.com/openremote/extensions/tree/main/entsoe/) to your project dependencies. For more information on how to include extensions, see [Extensions](https://docs.openremote.io/docs/developer-guide/extensions).

1. Create asset:
   - Go to the `Assets` page and click the `+` icon
   - Select the `Entsoe agent` and name it (**e.g. ENTSO-E agent**)
   - Click `Add` to create the asset

2. Get EPEX spot prices:
   - Follow the [ENTSO-E documentation](https://github.com/openremote/extensions/blob/main/entsoe/README.md) to connect the EPEX spot prices to a custom attribute (e.g. `nlPrices`) added to the `Entsoe agent`

3. Link EPEX spot prices to EMS:
   - Link the `nlPrices` attribute forecast of the **Entsoe agent** to the `tariffImport` of the `Ems Energy Optimisation Asset` using a custom Groovy rule. Don't forget to make the conversion from €/MWh to €/kWh. OpenRemote currently does not provide built-in functionality for linking/summing forecasts.
   - Repeat the above step for the `tariffExport` of the `Ems Energy Optimisation Asset`, and additionally multiply the forecast prices with -1.

### 7) Select Optimisation method

- Go to the `Assets` page, select the **EMS** asset created in section 1
- Find the `Optimisatiom method` attribute and select `Ems optimisation` from the dropdown menu. This will start the optimisation routine

The EMS is now set up and the optimisation is running. For a detailed explanation about the `Ems optimisation` method, see (Work in progress)
