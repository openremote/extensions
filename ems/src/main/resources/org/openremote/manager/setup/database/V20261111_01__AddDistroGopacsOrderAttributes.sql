-- Add the GOPACS order attributes to existing EmsDistroEnergyAsset instances. An attribute the
-- asset already has keeps its value and meta, so this is safe to run on any asset.

-- Configuration: left unset, so ordering stays off until an operator enables it
SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsEnabled', 'boolean', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsEnabled' IS NULL;

-- Announcement inputs, copied from the EmsGOPACSAsset with the same parent (read-only)
SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsEan', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsEan' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsAnnouncementId', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsAnnouncementId' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsStartTime', 'timestamp', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsStartTime' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsEndTime', 'timestamp', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsEndTime' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsBidValidityEnd', 'timestamp', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsBidValidityEnd' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsEanEffectivity', 'text', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsEanEffectivity' IS NULL;

-- Next order, pre-filled by the handler and set by the operator
SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsOrderType', 'gopacsOrderTypeValueType', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsOrderType' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsOrderQuantity', 'number', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsOrderQuantity' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsOrderPrice', 'positiveNumber', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsOrderPrice' IS NULL;

-- Operator actions, reset by the handler
SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsConfirmOrder', 'boolean', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsConfirmOrder' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsCancelOrders', 'boolean', null, now(), '{}'::jsonb)
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsCancelOrders' IS NULL;

-- Status (read-only)
SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsOrders', 'JSONObject', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsOrders' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsOrderHistory', 'JSONObject', null, now(), jsonb_build_object('readOnly', true, 'storeDataPoints', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsOrderHistory' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsSettlementHistory', 'JSONObject', null, now(), jsonb_build_object('readOnly', true, 'storeDataPoints', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsSettlementHistory' IS NULL;

SELECT a.id, ADD_ATTRIBUTE(a, 'gopacsLastSync', 'timestamp', null, now(), jsonb_build_object('readOnly', true))
FROM asset a WHERE a.type = 'EmsDistroEnergyAsset' AND a.attributes -> 'gopacsLastSync' IS NULL;
