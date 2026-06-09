-- Add attribute disableOptimisationService to EmsEnergyOptimisationAsset
SELECT a.id, ADD_ATTRIBUTE(a, 'disableOptimisationService', 'boolean', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsEnergyOptimisationAsset';