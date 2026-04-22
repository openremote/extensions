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
- Upgrading `shapeshifter-core` from `3.2.2` to `3.5.0`. Shapeshifter is not involved in the V2/V3 migration itself (the participants API is a GOPACS-broker-specific REST API, not part of the UFTP protocol). Verified against the 3.5.0 sources: `UftpParticipantInformation`, `ParticipantResolutionService`, and `UftpSendMessageService.doSend()` are unchanged vs. 3.2.2 — so the version bump would not fix the outbound-send defect described below either. A library bump can be a separate PR if desired.

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

## Known follow-up: outbound messaging needs a broker-URL fallback

V3's `ParticipantView` no longer carries an `endpoint` field, so the cached `UftpParticipantInformation` stores `endpoint=null`. Reading `shapeshifter-core` 3.2.2 (and confirmed identical in 3.5.0), the outbound send path is:

1. `GOPACSHandler.notifyNewOutgoingMessage` → `UftpSendMessageService.attemptToSendMessage`
2. `UftpSendMessageService.doSend` (line 124–133 in 3.2.2, 135–144 in 3.5.0):
   ```java
   UftpParticipantInformation participantInformation = participantService.getParticipantInformation(details.recipient());
   String url = participantInformation.endpoint();     // null after this migration
   ...
   send(signedXml, url, additionalHeaders, MAX_FOLLOW_REDIRECTS);
   ```
3. `UftpSendMessageService.send(...)`:
   ```java
   var requestBuilder = HttpRequest.newBuilder().uri(new URI(url)) ...   // new URI(null) → NullPointerException
   ```

The surrounding `try/catch` in `send` catches `URISyntaxException | IllegalArgumentException | IOException | InterruptedException`. It does not catch `NullPointerException`, so the NPE escapes `attemptToSendMessage` and is swallowed only by `GOPACSHandler.notifyNewOutgoingMessage`'s generic `catch (Exception e)` as a SEVERE log.

**Practical impact:** every outbound `FlexOffer`, `FlexRequestResponse`, and `FlexOrderResponse` will fail on the first send attempt after this PR is deployed. Inbound messaging (signature verification, asset updates) is unaffected.

**Why V2 worked:** V2 returned a per-participant `endpoint` URL in the address-book response. In a broker model all of those almost certainly pointed at the GOPACS broker itself (the broker forwards to the real DSO backend); the V3 spec removing the field is consistent with "stop pretending per-participant endpoints exist, just POST to the broker".

**Proposed follow-up fix** (a separate small PR):
1. Add a new config `GOPACS_BROKER_URL` to `GOPACSHandler`, with a sensible default (the broker URL used for message submission — to be confirmed from the GOPACS testing documentation or by asking `servicedesk@gopacs.eu`).
2. Pass this value as the `endpoint` argument when constructing `UftpParticipantInformation` in `getParticipantInformation`.
3. Verify against acceptance by sending a `FlexOffer` in response to a test `FlexRequest`.

Options to determine the correct broker URL before writing that PR:
- Check the GOPACS testing doc linked from `ems/README.md` (`GOPACS-Testing-receiving-and-sending-flex-messages-by-UFTP-testing-functionality-04-12-2025.pdf`) — it likely names the submit endpoint.
- Email `servicedesk@gopacs.eu`: "V3 ParticipantView drops the `endpoint` field; which URL should outbound UFTP messages be POSTed to?"
- While V2 is still live, hit the current V2 endpoint and inspect the `endpoint` field returned per DSO; if all DSOs share a URL, that is the broker URL.

## Timeline

- **25 October 2026** — last day GOPACS guarantees V2 is available.
- **26 October 2026** — GOPACS begins removing V2, starting with the acceptance environment.

Aim to have this merged and deployed to the acceptance environment well ahead of 26 October 2026 so we can validate against acceptance before production removal.
