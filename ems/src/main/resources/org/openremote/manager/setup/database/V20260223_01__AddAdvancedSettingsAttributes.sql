-- Add attribute advancedSettingsAttributes to Ems energy optimisation Asset
SELECT a.id, ADD_ATTRIBUTE(a, 'advancedSettingsAttributes', 'text', null, now(), jsonb_build_object('multiline', true, 'readOnly', true))
FROM asset a WHERE a.type = 'EmsEnergyOptimisationAsset';