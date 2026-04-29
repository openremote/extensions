package ems.rules

import org.openremote.container.persistence.PersistenceService
import org.openremote.extension.ems.agent.EmsEnergyOptimisationAsset
import org.openremote.manager.rules.RulesBuilder
import org.openremote.model.query.AssetQuery
import org.openremote.model.rules.Assets
import org.openremote.model.util.ValueUtil
import org.postgresql.util.PGobject

import java.sql.*
import java.text.SimpleDateFormat
import java.time.*
import java.util.Date
import java.util.concurrent.ThreadLocalRandom
import java.util.logging.Logger

import static java.lang.Math.PI
import static java.lang.Math.sin

Logger LOG = binding.LOG
RulesBuilder rules = binding.rules
Assets assets = binding.assets


// -------------------------input------------------------- //

// Set asset ID's:
String energyOptimisationAssetId = "setId1"

// Set values:
String powerConsumptionConstantStr = "setValue1"
String powerConsumptionProfileNumber = "setValue2"
String powerFluctuationsStr = "setValue3"

boolean variableTariffs = false

// Set tariff prices:
List<Double> tariffPricesImport = [
        95.60, 80.00, 64.71, 66.04, 83.00, 79.65, 72.93, 64.42, 72.15, 63.50, 58.80, 50.00, 51.84, 51.00, 53.51, 57.58,
        50.00, 56.52, 60.22, 70.00, 53.85, 68.01, 89.25, 115.09, 71.41, 110.00, 117.00, 127.28, 127.28, 129.01, 133.14, 133.14,
        147.20, 147.37, 137.98, 127.45, 139.49, 129.01, 123.23, 117.00, 114.81, 108.08, 100.00, 93.98, 20.00, 20.00, 11.00, 11.00,
        5.00, 0.00, 0.00, 0.00, 81.99, 81.99, 87.70, 88.50, 0.00, 0.00, 0.00, 0.00, 85.00, 85.00, 85.00, 85.00,
        89.25, 105.37, 117.00, 118.03, 114.30, 118.03, 124.67, 127.00, 121.07, 125.27, 127.28, 133.14, 132.27, 136.92, 158.07, 158.07,
        158.07, 133.16, 127.00, 124.96, 129.89, 126.20, 118.28, 115.00, 118.03, 116.65, 115.20, 109.62, 114.01, 112.65, 104.37, 102.97
]

//List<Double> tariffPricesImport = [100.0, 100.0, 100.0, 100.0, // 0
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0, // 4
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0, // 8
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0, // 12
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0, // 16
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0, // 20
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0,
//                                   100.0, 100.0, 100.0, 100.0]

// Set the link forecast ["inputAssetId", "inputAttributeName", "outputAssetId", "outputAttributeName", "+/-"] here:
def attributes = [
        ["linkInputAssetId1", "linkInputAttributeName1", "linkOutputAssetId1", "linkOutputAttributeName1", "+"]
]

// Set the sum forecasts ["inputAssetName", "inputAttributeName" , "inputAssetId", "+/-"] here:
def inputAttributes = [
        ["sumInputAssetName1", "sumInputAttributeName1", "sumInputAssetId1", "+"],
        ["sumInputAssetName2", "sumInputAttributeName2", "sumInputAssetId2", "+"],
        ["sumInputAssetName3", "sumInputAttributeName3", "sumInputAssetId3", "+"]
]

// Set the sum forecasts ["outputAssetName", "outputAttributeName" , "outputAssetId"] here:
def outputAttribute = ["sumOutputAssetName", "sumOutputAttributeName", "sumOutputAssetId"]

// Set forecast period in days here:
long forecastPeriodDays = 7

// Set desired forecast interval here:
long forecastIntervalMinutes = 15

// ------------------------------------------------------- //


Double powerConsumptionConstant = powerConsumptionConstantStr.toDouble()
double powerFluctuation = powerFluctuationsStr.toDouble()
SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

// Time triggers for rules
long rulesStartTimeMillis = System.currentTimeMillis()
long previousTimeMillisRule1 = rulesStartTimeMillis - rulesStartTimeMillis % (1 * 60 * 1000) + (1 * 60 * 1000)
long previousTimeMillisRule3 = rulesStartTimeMillis - rulesStartTimeMillis % (1 * 60 * 1000) + (1 * 60 * 1000)
long previousTimeMillisRule4 = rulesStartTimeMillis - rulesStartTimeMillis % (1 * 60 * 1000) + (1 * 60 * 1000 + 15 * 1000)
long timestampMillisPrevious = rulesStartTimeMillis

// Date triggers for rules
SimpleDateFormat dateOnlyFormat = new SimpleDateFormat("yyyy-MM-dd")
String datePreviousRule2 = dateOnlyFormat.format(new Date())

// Trigger on rule creation
boolean triggerRule2 = true

rules.add()
        .priority(1)
        .name("Simulate power data-points rule")
        .when({ facts ->
            long currentTimeMillis = facts.clock.currentTimeMillis

            // Trigger rule every 1 minutes
            if (currentTimeMillis > previousTimeMillisRule1) {
                previousTimeMillisRule1 += (1 * 60 * 1000)
                return true
            }

            return false
        })
        .then({ facts ->
            // Get power set-point for each battery
            def powerSetpointBatteries = facts
                    .matchAssetState(new AssetQuery().parents(energyOptimisationAssetId).attributeNames("powerSetpoint"))
                    .toList()

            String[] attributeNames = ["energyCapacity", "energyLevel", "energyLevelPercentage"]

            def attributeInfoList = facts
                    .matchAssetState(new AssetQuery().parents(energyOptimisationAssetId).attributeNames(attributeNames))
                    .toList()

            // Create a map using id as key and attribute structure as value
            def assetsAttributesMap = [:].withDefault { [:].withDefault { null } }

            attributeInfoList.each { attributeInfo ->
                String assetId = attributeInfo.id
                String attributeName = attributeInfo.name
                def value = attributeInfo.value.orElse(null)

                assetsAttributesMap[assetId][attributeName] = value
            }

            // Battery energy level calculation
            long timestampMillisCurrent = facts.clock.currentTimeMillis

            powerSetpointBatteries.forEach {
                String assetId = it.id
                def powerSetpoint = it.value.orElse(0.0)

                def assetAttributesMap = assetsAttributesMap.get(assetId)
                def energyCapacity = assetAttributesMap.getOrDefault("energyCapacity", null) as Double
                def energyLevel = assetAttributesMap.getOrDefault("energyLevel", null) as Double
                def energyLevelPercentage = assetAttributesMap.getOrDefault("energyLevelPercentage", null) as Double

                if (energyCapacity != null && energyLevelPercentage != null) {
                    double energyLevelNew

                    if (energyLevel == null) {
                        energyLevelNew = (Math.round(energyLevelPercentage * energyCapacity * 10)) / 1000
                    } else {
                        def timeIntervalMillis = timestampMillisCurrent - timestampMillisPrevious
                        double energyChange = powerSetpoint * (timeIntervalMillis / 3600000.0)
                        energyLevelNew = (Math.round((energyLevel + energyChange) * 1000.0)) / 1000
                    }

                    long energyLevelPercentageNew = Math.round(energyLevelNew / energyCapacity * 100)

                    assets.dispatch(assetId, "energyLevel", energyLevelNew)
                    assets.dispatch(assetId, "energyLevelPercentage", energyLevelPercentageNew)
                } else if (energyCapacity != null && energyLevel != null && energyLevelPercentage == null) {
                    long energyLevelPercentageNew = Math.round(energyLevel / energyCapacity * 100)

                    assets.dispatch(assetId, "energyLevelPercentage", energyLevelPercentageNew)
                }
            }

            timestampMillisPrevious = timestampMillisCurrent

            // Power consumption profiles
            def powerConsumption

            switch (powerConsumptionProfileNumber) {
                case "2":
                    // Power Consumption profile: "Flip plus and minus"
                    def now = LocalTime.now()
                    if (now.isBefore(LocalTime.NOON)) {
                        powerConsumption = powerConsumptionConstant
                    } else {
                        powerConsumption = -powerConsumptionConstant
                    }
                    break
                case "3":
                    // Power Consumption profile: "Sinus wave"
                    def amplitude = powerConsumptionConstant
                    def periodHours = 24
                    def phaseShift = 0
                    def verticalShift = 0

                    def currentSecondOfDay = LocalTime.now().toSecondOfDay()
                    def x = currentSecondOfDay / (60 * 60) // Hours
                    def functionSin = amplitude * sin(2 * PI / periodHours * (x + phaseShift) + verticalShift)

                    powerConsumption = (functionSin * 1000).round() / 1000
                    break
                default:
                    powerConsumption = powerConsumptionConstant
            }

            // Add random fluctuations to power consumption
            double powerNoise = 0.0

            if (powerFluctuation > 0) {
                double powerFluctuationHalf = powerFluctuation / 2
                powerNoise = Math.round(ThreadLocalRandom.current().nextDouble(-powerFluctuationHalf, powerFluctuationHalf) * 1000.0) / 1000.0
            }

            powerConsumption = powerConsumption + powerNoise

            def powerFlexible = (powerSetpointBatteries.collect { it.value.orElse(0.0) }.sum() ?: 0.0) as double
            def powerNet = powerConsumption + powerFlexible

            // Update power attributes
            powerSetpointBatteries.forEach {
                if (it.value.isPresent()) {
                    assets.dispatch(it.id, "power", it.value)
                }
            }

            assets.dispatch(energyOptimisationAssetId, "powerConsumption", Math.round((double) (powerConsumption * 1000.0)) / 1000.0)
            assets.dispatch(energyOptimisationAssetId, "powerFlexible", Math.round((double) (powerFlexible * 1000.0)) / 1000.0)
            assets.dispatch(energyOptimisationAssetId, "powerNet", Math.round((double) (powerNet * 1000.0)) / 1000.0)
        })

rules.add()
        .priority(2)
        .name("Simulate forecasts rule")
        .when({ facts ->
            long currentMillis = facts.clock.currentTimeMillis
            String dateCurrent = dateOnlyFormat.format(new Date(currentMillis))

            // Trigger rule at 0:00am
            if (dateCurrent != datePreviousRule2) {
                datePreviousRule2 = dateCurrent
                triggerRule2 = true
            }

            return triggerRule2
        })
        .then({ facts ->
            triggerRule2 = false

            long currentTimeMillis = facts.clock.currentTimeMillis
            long intervalMillis = (long) (3600000 * 24 / tariffPricesImport.size())
            ZoneId zoneId = ZoneId.systemDefault()
            LocalDate dateCurrent = Instant.ofEpochMilli(currentTimeMillis).atZone(zoneId).toLocalDate()

            TreeMap<String, Double> datePriceExportMapForecast = new TreeMap<>()
            TreeMap<String, Double> datePriceExportMapHistoric = new TreeMap<>()
            TreeMap<String, Double> datePriceImportMapForecast = new TreeMap<>()
            TreeMap<String, Double> datePriceImportMapHistoric = new TreeMap<>()

            TreeMap<String, Double> datePowerConsumptionMapForecast = new TreeMap<>()

            // Create forecast data-points
            for (int i = -1; i <= forecastPeriodDays; i++) {
                for (int j = 0; j < tariffPricesImport.size(); j++) {
                    def startOfDay = dateCurrent.atStartOfDay(zoneId).plusDays(i) as ZonedDateTime
                    long timeMillis = startOfDay.toInstant().toEpochMilli() + j * intervalMillis //- 10 * intervalMillis
                    def key = sdf.format(new Date(timeMillis)) as String

                    double factor = 1.0

                    if (variableTariffs) {
                        factor = 0.5 + Math.random()
                    }

                    def valueImport = (factor * tariffPricesImport[j] / 1000).round(2)
                    def valueExport = -valueImport

                    if (timeMillis <= currentTimeMillis) {
                        datePriceExportMapHistoric.put(key, valueExport)
                        datePriceImportMapHistoric.put(key, valueImport)
                    } else {
                        datePriceExportMapForecast.put(key, valueExport)
                        datePriceImportMapForecast.put(key, valueImport)
                        datePowerConsumptionMapForecast.put(key, powerConsumptionConstant)
                    }
                }
            }

            upsertDatabaseDatapoints("asset_predicted_datapoint", energyOptimisationAssetId, EmsEnergyOptimisationAsset.TARIFF_EXPORT.getName(), datePriceExportMapForecast)
            upsertDatabaseDatapoints("asset_datapoint", energyOptimisationAssetId, EmsEnergyOptimisationAsset.TARIFF_EXPORT.getName(), datePriceExportMapHistoric)
            upsertDatabaseDatapoints("asset_predicted_datapoint", energyOptimisationAssetId, EmsEnergyOptimisationAsset.TARIFF_IMPORT.getName(), datePriceImportMapForecast)
            upsertDatabaseDatapoints("asset_datapoint", energyOptimisationAssetId, EmsEnergyOptimisationAsset.TARIFF_IMPORT.getName(), datePriceImportMapHistoric)
            upsertDatabaseDatapoints("asset_predicted_datapoint", energyOptimisationAssetId, EmsEnergyOptimisationAsset.POWER_CONSUMPTION.getName(), datePowerConsumptionMapForecast)
        })

rules.add()
        .priority(3)
        .name("Link forecasts rule")
        .when({ facts ->
            long currentTimeMillis = facts.clock.currentTimeMillis

            // Trigger rule every 1 minutes
            if (currentTimeMillis > previousTimeMillisRule3) {
                previousTimeMillisRule3 += (1 * 60 * 1000)
                return true
            }

            return false
        })
        .then({ facts ->
            long currentTimeMillis = facts.clock.currentTimeMillis

            // Forecast period
            long startTimeMillis = currentTimeMillis - currentTimeMillis % (forecastIntervalMinutes * 60 * 1000)
            long endTimeMillis = startTimeMillis - startTimeMillis % (24 * 60 * 60000) + ((forecastPeriodDays + 1) * 24 * 60 * 60000)

            // Convert time in milliseconds to a string timestamp
            String startDateTimeStr = sdf.format(new Date(startTimeMillis))
            String endDateTimeStr = sdf.format(new Date(endTimeMillis))
            String databaseTableName = "asset_predicted_datapoint"

            // Link forecast for each input attribute
            for (attribute in attributes) {
                String inputAssetId = attribute[0]
                String inputAttributeName = attribute[1]
                String outputAssetId = attribute[2]
                String outputAttributeName = attribute[3]
                String plusOrMinus = attribute[4]

                // Get data-points
                TreeMap<String, Double> forecast = getDatabaseDatapoints(databaseTableName, inputAssetId, inputAttributeName, startDateTimeStr, endDateTimeStr)

                // Check if forecast is present
                if (forecast.isEmpty()) {
                    continue
                }

                if (plusOrMinus == "-") {
                    // Flip sign of forecast values
                    forecast = new TreeMap<String, Double>(forecast.collectEntries { key, value -> [key, -value] })
                }

                // Update data-points in database
                upsertDatabaseDatapoints(databaseTableName, outputAssetId, outputAttributeName, forecast)
            }
        })

rules.add()
        .priority(4)
        .name("Sum forecasts rule")
        .when({ facts ->
            long currentTimeMillis = facts.clock.currentTimeMillis

            // Trigger rule every 1 minutes
            if (currentTimeMillis > previousTimeMillisRule4) {
                previousTimeMillisRule4 += (1 * 60 * 1000)
                return true
            }

            return false
        })
        .then({ facts ->
            long currentTimeMillis = facts.clock.currentTimeMillis

            // Interpolation interval in milliseconds
            long intervalMillis = forecastIntervalMinutes * 60 * 1000

            // Forecast period
            long startTimeMillis = currentTimeMillis - intervalMillis
            long endTimeMillis = startTimeMillis - startTimeMillis % (24 * 60 * 60000) + ((forecastPeriodDays + 1) * 24 * 60 * 60000)

            boolean updateOnlyFutureDatapoints = false

            sumForecasts(inputAttributes, outputAttribute, currentTimeMillis, intervalMillis, startTimeMillis, endTimeMillis, 3, updateOnlyFutureDatapoints)
        })

private void sumForecasts(List<List<String>> inputAttributes, List<String> outputAttribute, long currentTimeMillis, long intervalMillis, long startTimeMillis, long endTimeMillis, int decimals, boolean updateOnlyFutureDatapoints) {
    // Map with all the input forecasts for summation
    Map<String, TreeMap<String, Double>> interpolatedForecastsMap = new HashMap()

    // Database date-time format
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    List<Long> startMillisList = new ArrayList()
    List<Long> endMillisList = new ArrayList()

    // Get the input forecasts and interpolate
    for (attribute in inputAttributes) {
        String attributeName = attribute[1]
        String assetId = attribute[2]
        String plusOrMinus = attribute[3]

        // Convert time in milliseconds to a string timestamp
        String startDateTimeStr = sdf.format(new Date(startTimeMillis))
        String endDateTimeStr = sdf.format(new Date(endTimeMillis))

        // Get forecast data-points from database
        TreeMap<String, Double> forecast = getDatabaseDatapoints("asset_predicted_datapoint", assetId, attributeName, startDateTimeStr, endDateTimeStr)

        if (forecast.isEmpty()) {
            continue
        }

        if (plusOrMinus == "-") {
            // Flip sign of forecast values
            forecast = new TreeMap<String, Double>(forecast.collectEntries { key, value -> [key, -value] })
        }

        // Find the forecast period
        String firstTimestamp = forecast.firstEntry().key
        String lastTimestamp = forecast.lastEntry().key

        long firstTimeMillis = sdf.parse(firstTimestamp).getTime()
        long lastTimeMillis = sdf.parse(lastTimestamp).getTime()

        long firstTimeMillisRounded = firstTimeMillis - firstTimeMillis % intervalMillis
        long lastTimeMillisRounded = lastTimeMillis - lastTimeMillis % intervalMillis + intervalMillis

        startMillisList.add(firstTimeMillisRounded)
        endMillisList.add(lastTimeMillisRounded)

        // List with desired date-times for forecast interpolation
        List<String> dateTimeList = new ArrayList<>()

        for (long i = firstTimeMillisRounded; i <= lastTimeMillisRounded; i += intervalMillis) {
            String dateTimeStr = sdf.format(new Date(i))
            dateTimeList.add(dateTimeStr)
        }

        TreeMap<String, Double> interpolatedForecast = interpolateForecast(dateTimeList, sdf, forecast)

        // Add interpolated forecast for each unique attributeRef
        String attributeRef = assetId + attributeName
        interpolatedForecastsMap.put(attributeRef, interpolatedForecast)
    }

    // Check if interpolated forecasts are present
    if (interpolatedForecastsMap.isEmpty()) {
        return
    }

    // List with data-times for output forecast
    List<String> dateTimeListGeneral = new ArrayList<>()

    long startMillis = startMillisList.min() as long
    long endMillis = endMillisList.min() as long

    for (long i = startMillis; i <= endMillis; i += intervalMillis) {
        String dateTimeStr = sdf.format(new Date(i))
        dateTimeListGeneral.add(dateTimeStr)
    }

    // Calculate output forecast
    TreeMap<String, Double> outputForecast = new TreeMap<>()
    def factor = Math.pow(10, decimals)

    for (String dateTime : dateTimeListGeneral) {
        long timeMillis = sdf.parse(dateTime).getTime()

        if (!updateOnlyFutureDatapoints || (updateOnlyFutureDatapoints && timeMillis > currentTimeMillis)) {
            List<Double> forecastValueList = new ArrayList()

            // Find all values per date-time
            interpolatedForecastsMap.each {
                def interpolatedForecast = it.value as TreeMap<String, Double>
                def forecastValue = interpolatedForecast.get(dateTime)

                if (forecastValue != null) {
                    forecastValueList.add(forecastValue)
                }
            }

            // Sum the values per date-time
            if (!forecastValueList.isEmpty()) {
                def forecastValueSum = forecastValueList.sum() as double
                def forecastValueSumRounded = Math.round(forecastValueSum * factor) / factor
                outputForecast.put(dateTime, forecastValueSumRounded)
            }
        }
    }

    // Upsert forecast data-points into database
    if (!outputForecast.isEmpty()) {
        upsertDatabaseDatapoints("asset_predicted_datapoint", outputAttribute[2], outputAttribute[1], outputForecast)
    }
}

private TreeMap<String, Double> interpolateForecast(List<String> dateTimeList, SimpleDateFormat sdf, TreeMap<String, Double> forecast) {
    Logger LOG = binding.LOG

    TreeMap<String, Double> interpolatedValues = new TreeMap<>()

    for (String dateTimeStr : dateTimeList) {
        // Find the two closest date-times in forecast
        String lowerDateTimeStr = forecast.floorKey(dateTimeStr)
        String upperDateTimeStr = forecast.ceilingKey(dateTimeStr)

        if (lowerDateTimeStr != null && upperDateTimeStr != null) {
            if (lowerDateTimeStr == upperDateTimeStr) {
                // Exact value found in forecast
                Double exactValue = forecast.get(dateTimeStr)

                interpolatedValues.put(dateTimeStr, exactValue)
            } else {
                try {
                    Date dateTime = sdf.parse(dateTimeStr)
                    Date lowerDateTime = sdf.parse(lowerDateTimeStr)
                    Date upperDateTime = sdf.parse(upperDateTimeStr)

                    // Calculate interpolation factor
                    long diff1 = dateTime.getTime() - lowerDateTime.getTime()
                    long diff2 = upperDateTime.getTime() - lowerDateTime.getTime()
                    double factor = (double) diff1 / diff2

                    // Interpolate value
                    Double lowerValue = forecast.get(lowerDateTimeStr)
                    Double upperValue = forecast.get(upperDateTimeStr)
                    Double interpolatedValue = lowerValue + factor * (upperValue - lowerValue)

                    interpolatedValues.put(dateTimeStr, interpolatedValue)
                } catch (Exception e) {
                    LOG.warning("Failed interpolation; Exception: " + e)
                }
            }
        } else {
            interpolatedValues.put(dateTimeStr, null)
        }
    }
    return interpolatedValues
}


private TreeMap<String, Double> getDatabaseDatapoints(String tableName, String assetId, String attributeName, String dateTimeFromStr, String dateTimeToStr) {
    Logger LOG = binding.LOG

    TreeMap<String, Double> datapoints = new TreeMap<>()

    try (Connection connection = connectToDatabase()
         Statement statement = connection.createStatement()) {

        String query =
                "SELECT timestamp, value " +
                        "FROM " + tableName + " " +
                        "WHERE entity_id = '" + assetId + "' " +
                        "AND attribute_name = '" + attributeName + "' " +
                        "AND timestamp BETWEEN '" + dateTimeFromStr + "' AND '" + dateTimeToStr + "' " +
                        "ORDER BY timestamp ASC;"

        try (ResultSet resultSet = statement.executeQuery(query)) {
            while (resultSet.next()) {
                String value1 = resultSet.getString("timestamp")
                Double value2 = resultSet.getDouble("value")

                datapoints.put(value1, value2)
            }
        }
    } catch (SQLException e) {
        LOG.warning("Failed to obtain data-points from database; Exception: " + e)
    }
    return datapoints
}

private void upsertDatabaseDatapoints(String tableName, String assetId, String attributeName, TreeMap<String, Double> datapoints) {
    Logger LOG = binding.LOG

    try (Connection connection = connectToDatabase()) {
        String query =
                "INSERT INTO " + tableName + " (entity_id, attribute_name, value, timestamp) VALUES (?, ?, ?, ?)" +
                        "ON CONFLICT (entity_id, attribute_name, timestamp) DO UPDATE " +
                        "SET value = EXCLUDED.value"

        PreparedStatement preparedStatement = connection.prepareStatement(query)

        for (Map.Entry<String, Double> entry : datapoints.entrySet()) {
            String key = entry.getKey()
            Double value = entry.getValue()

            PGobject pgJsonValue = new PGobject()
            pgJsonValue.setType("jsonb")
            pgJsonValue.setValue(ValueUtil.asJSON(value).orElse("null"))

            preparedStatement.setString(1, assetId)
            preparedStatement.setString(2, attributeName)
            preparedStatement.setObject(3, pgJsonValue)
            preparedStatement.setTimestamp(4, Timestamp.valueOf(key))

            preparedStatement.addBatch()
        }

        int[] affectedRows = preparedStatement.executeBatch()

        for (int affectedRow : affectedRows) {
            if (affectedRow != 1) {
                LOG.warning("Failed to insert row into database")
            }
        }
    } catch (SQLException e) {
        LOG.warning("Failed to insert data-points into database; Exception: " + e)
    }
}

private static Connection connectToDatabase() throws SQLException {
    String dbPort = System.getenv(PersistenceService.OR_DB_PORT)
    String dbHost = System.getenv(PersistenceService.OR_DB_HOST)
    String dbName = System.getenv(PersistenceService.OR_DB_NAME)
    String dbUsername = System.getenv(PersistenceService.OR_DB_USER)
    String dbPassword = System.getenv(PersistenceService.OR_DB_PASSWORD)

    if (dbPort == null) {
        dbPort = PersistenceService.OR_DB_PORT_DEFAULT.toString()
    }
    if (dbHost == null) {
        dbHost = PersistenceService.OR_DB_HOST_DEFAULT
    }
    if (dbName == null) {
        dbName = PersistenceService.OR_DB_NAME_DEFAULT
    }
    if (dbUsername == null) {
        dbUsername = PersistenceService.OR_DB_USER_DEFAULT
    }
    if (dbPassword == null) {
        dbPassword = PersistenceService.OR_DB_PASSWORD_DEFAULT
    }

    String databaseUrl = "jdbc:postgresql://" + dbHost + ":" + dbPort + "/" + dbName

    return DriverManager.getConnection(databaseUrl, dbUsername, dbPassword)
}
