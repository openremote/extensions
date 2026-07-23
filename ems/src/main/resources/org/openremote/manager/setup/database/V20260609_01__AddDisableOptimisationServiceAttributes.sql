-- Change attribute name from optimisationDisabled to disableOptimisationService for EmsEnergyOptimisationAsset
UPDATE asset
SET attributes =
    (
        attributes - 'optimisationDisabled'
    ) || jsonb_build_object(
        'disableOptimisationService',
        jsonb_set(
            attributes -> 'optimisationDisabled',
            '{name}',
            '"disableOptimisationService"',
            false
        )
    )
WHERE type = 'EmsEnergyOptimisationAsset';