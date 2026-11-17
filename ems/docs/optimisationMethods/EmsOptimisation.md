Work in progress

## Unavailable tariff forecasts

Both `EmsOptimisation` and `EmsOptimisationBeta` treat null tariff values as unavailable. A moving-average window is eligible for tariff-based charging or discharging only when all its prices are available (the default window contains eight datapoints). Windows containing nulls are excluded; available windows before or after them can still be selected at their original timestamps.

Import and export forecasts are evaluated independently. If no complete import window exists for a day, no tariff-based charging zone is selected for it; the same applies to export windows and discharging. If neither has a complete window, tariff-based zone selection produces no zones for that day. Other optimisation logic continues. Missing prices are not replaced with zero, interpolated, or carried forward.
