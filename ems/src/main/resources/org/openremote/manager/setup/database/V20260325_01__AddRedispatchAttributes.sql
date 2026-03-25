-- Add redispatch configuration attributes to existing EmsGOPACSAsset instances
SELECT a.id, ADD_ATTRIBUTE(a, 'postalCode', 'text', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchEnabled', 'boolean', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

-- Add redispatch status attributes (read-only)
SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchAnnouncementId', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchComplianceType', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchAnnouncementMessage', 'text', null, now(), jsonb_build_object('multiline', true, 'readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchStartTime', 'positiveInteger', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchEndTime', 'positiveInteger', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchBidValidityEnd', 'positiveInteger', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchRequestedPower', 'number', null, now(), jsonb_build_object('readOnly', true, 'hasPredictedDatapoints', true, 'storeDataPoints', true, 'dataPointsMaxAgeDays', 7))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchEanEffectivity', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchRequestAreaBuy', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchRequestAreaSell', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchLastPoll', 'positiveInteger', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

-- Add redispatch bid attributes
SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchSuggestedPower', 'number', null, now(), jsonb_build_object('readOnly', true, 'hasPredictedDatapoints', true, 'storeDataPoints', true, 'dataPointsMaxAgeDays', 7))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchSuggestedVolume', 'number', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchBidPrice', 'number', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

-- Add redispatch confirmation workflow attributes
SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchConfirmBid', 'boolean', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchBidStatus', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

-- Add redispatch history attributes
SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchAnnouncementHistory', 'JSONObject', null, now(), jsonb_build_object('readOnly', true, 'storeDataPoints', true, 'dataPointsMaxAgeDays', 90))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';

SELECT a.id, ADD_ATTRIBUTE(a, 'redispatchBidHistory', 'JSONObject', null, now(), jsonb_build_object('readOnly', true, 'storeDataPoints', true, 'dataPointsMaxAgeDays', 90))
FROM asset a WHERE a.type = 'EmsGOPACSAsset';
