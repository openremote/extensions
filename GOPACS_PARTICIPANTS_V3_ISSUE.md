# Migrate GOPACS UFTP Participants API from V2 to V3

**Label:** Enhancement

## Background

GOPACS has announced (email, April 2026) that the **UFTP Participants API V2** is deprecated and will be removed starting **26 October 2026**. The acceptance environment is removed first, then production. Our EMS integration in `ems/manager/gopacs/` calls this API whenever Shapeshifter needs a peer's public key to verify an incoming UFTP message signature, so if we miss the deadline, inbound GOPACS messaging stops working.

The main functional change in V3 is that participants can now be looked up by **ContractID + role** as well as by domain, which lets GOPACS route activation messages to per-contract handlers. We are **not** adopting that new capability in this migration — it would require restructuring `GOPACSHandler` (currently one handler per EAN) and can be a follow-up if we ever need per-Capacity-Steering-Contract / per-Time-bound-Transport-Right behaviour. This migration is a minimal drop-in replacement to stay functional past the deadline.

## What's changing in V3

| | V2 | V3 |
|---|---|---|
| Base URL (prod) | `https://clc-message-broker.gopacs-services.eu` | `https://api.gopacs-services.eu` |
| Base URL (acc) | *n/a via our config* | `https://api.acc.gopacs-services.eu` |
| Participant lookup | `GET /v2/participants/DSO?contractedEan={ean}` → list of all DSOs for an EAN | `GET /uftp-participants/v3/participants/{uftpDomainName}` → single `ParticipantView` |
| Content type | XML | JSON |
| Auth | (not enforced in our client) | Bearer JWT |
| Response fields | `domain`, `publicKey`, `endpoint`, … | `domain`, `publicKey` only |
| Removed | — | An unspecified "old and temporary endpoint" |

V3 also adds `GET /uftp-participants/v3/participants/contracts/{contractId}/roles/{uftpRole}` (roles: `AGR`, `DSO`; `CRO` listed as not supported). Our signature-verification call site always has the sender domain so the by-domain endpoint is the direct substitute — we don't need the contract/role endpoint for this migration.

Swagger documentation:
- Acceptance: https://api.acc.gopacs-services.eu/docs/uftp-broker/v3/api-docs/uftp-participants-v3
- Production: https://api.gopacs-services.eu/docs/uftp-broker/v3/api-docs/uftp-participants-v3

## Scope of this change

- [x] Update `GOPACSAddressBookResource` to the V3 by-domain endpoint (JSON, Authorization header).
- [x] Add `ParticipantView` DTO record mirroring the V3 response schema.
- [x] Rewrite `GOPACSHandler.getParticipantInformation(...)` to call the V3 endpoint with a Bearer token from the existing OAuth2 client-credentials flow. Extract the token acquisition into a shared `fetchBearerToken()` helper.
- [x] Change `DEFAULT_GOPACS_PARTICIPANT_URL` to `https://api.gopacs-services.eu`. Acceptance environment selected via existing `GOPACS_PARTICIPANT_URL` override.
- [x] Drop the `endpoint` field from cached `UftpParticipantInformation` (V3 no longer returns it — we pass `null`).

Out of scope (follow-up if needed):
- Per-ContractID / per-role handler registration using `GET /participants/contracts/{contractId}/roles/{uftpRole}`.
- WireMock tests for the V3 lookup — `GOPACSHandler` currently has no unit tests at all, and adding test infrastructure is a larger separate piece of work.
- Upgrading `shapeshifter-core` from `3.2.2` to `3.3.0`. Shapeshifter is not involved in the V2/V3 migration (the participants API is a GOPACS-broker-specific REST API, not part of the UFTP protocol). A library bump can be a separate PR if desired.

## Deployment note

Any deployment that overrides `GOPACS_PARTICIPANT_URL` (for example, pinning acceptance to `https://clc-message-broker-acc.gopacs-services.eu`) **must be updated** to the new host:

- Production: `GOPACS_PARTICIPANT_URL=https://api.gopacs-services.eu` (or leave unset to use the default).
- Acceptance: `GOPACS_PARTICIPANT_URL=https://api.acc.gopacs-services.eu`.

## Acceptance criteria

- [ ] `./gradlew clean build` passes.
- [ ] Manager deploys cleanly with a GOPACS-enabled `EmsGOPACSAsset`.
- [ ] Against the acceptance environment: an inbound signed UFTP `FlexRequest` from a known DSO triggers a successful V3 participant lookup (200 with `publicKey`) and signature verification passes in `processRawMessage`. Downstream `POWER_*` predicted datapoints are written as before.
- [ ] 404 from the V3 endpoint for an unknown domain produces a FINE-level log, not an error.
- [ ] Works in both acceptance (`api.acc.gopacs-services.eu`) and production (`api.gopacs-services.eu`) via the `GOPACS_PARTICIPANT_URL` override.

## Open risk to validate during smoke test

V3's `ParticipantView` no longer carries an `endpoint` field, so the cached `UftpParticipantInformation` stores `endpoint=null`. Shapeshifter's `UftpSendMessageService` reads the endpoint when sending outbound messages. Needs confirmation during the acceptance smoke test that outgoing `FlexOffer` / `FlexOrderResponse` messaging still works with `endpoint=null`; if not, we'll need a follow-up to either introduce a configured broker URL fallback or route outbound messages differently.

## Timeline

- **25 October 2026** — last day GOPACS guarantees V2 is available.
- **26 October 2026** — GOPACS begins removing V2, starting with the acceptance environment.

Aim to have this merged and deployed to the acceptance environment well ahead of 26 October 2026 so we can validate against acceptance before production removal.
