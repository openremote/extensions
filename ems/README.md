# Energy Management System (EMS)

## GOPACS Integration

### What is GOPACS?

[GOPACS](https://www.gopacs.eu/) (Grid Operators Platform for Congestion Solutions) is a platform operated by Dutch grid operators (DSOs and TSO) to resolve grid congestion through flexibility trading. When the electricity grid is at risk of overloading, GOPACS sends flexibility requests to market participants (aggregators) who can adjust their energy consumption or production to relieve congestion.

The communication between GOPACS and market participants uses the **UFTP** (Universal Flexibility Trading Protocol), part of the [USEF](https://www.usef.energy/) framework, implemented via the [Shapeshifter](https://github.com/shapeshifter/shapeshifter-library-java) library.

For detailed documentation, see: [GOPACS documents and manuals](https://www.gopacs.eu/en/documents-and-manuals/)

### Getting Started

To participate in GOPACS flex trading through OpenRemote, you need:

1. **A GOPACS account** — Register as a Trading Company at [gopacs.eu](https://www.gopacs.eu/)
2. **OAuth2 client credentials** (`client_id` and `client_secret`) — See [OAuth2 Client Credentials for API Clients](https://www.gopacs.eu/wp-content/uploads/2025/12/GOPACS-OAuth2-Client-credentials-for-API-Clients-03-12-2025.pdf)
3. **A signing key pair** — An Ed25519 private key file for signing UFTP messages. The corresponding public key must be registered with GOPACS
4. **A contracted EAN** — The EAN (European Article Number) identifying your grid connection point, as agreed with your DSO

#### Configuration

The following environment variables must be set on the OpenRemote manager:

| Variable | Required | Description |
|---|---|---|
| `GOPACS_PRIVATE_KEY_FILE` | Yes | File path to the Ed25519 private key for signing UFTP messages |
| `GOPACS_CLIENT_ID` | Yes | OAuth2 client ID from GOPACS |
| `GOPACS_CLIENT_SECRET` | Yes | OAuth2 client secret from GOPACS |
| `GOPACS_PARTICIPANT_URL` | No | Address book base URL (default: `https://clc-message-broker.gopacs-services.eu`) |
| `GOPACS_OAUTH2_URL` | No | OAuth2 token endpoint (default: `https://auth.gopacs-services.eu/realms/gopacs/protocol/openid-connect/token`) |
| `GOPACS_RESPONSE_DELAY_SECONDS` | No | Delay before auto-responding to messages (default: `10`) |
| `GOPACS_FLEX_OFFER_DELAY_SECONDS` | No | Delay before sending a flex offer (default: `30`) |

#### Asset Setup

In OpenRemote, create an **EMS GOPACS Asset** as a child of an **EMS Energy Optimisation Asset** and set the `contractedEAN` attribute to your grid connection's EAN.

Alternatively, when creating a new **EMS Energy Optimisation Asset**, you can enable the "Include GOPACS" attribute to have the GOPACS child asset created automatically. Note that this only works during initial asset creation — if the **EMS Energy Optimisation Asset** already exists, you need to manually create the **EMS GOPACS Asset** as a child.

### Developer Guide

#### Components

```
gopacs/
  GOPACSHandler.java              Core orchestrator — handles all UFTP message processing,
                                  signing, OAuth2 auth, and scheduling
  GOPACSServerResource.java       JAX-RS interface for the inbound endpoint (POST /gopacs/message)
  GOPACSServerResourceImpl.java   Delegates incoming XML to GOPACSHandler::processRawMessage
  GOPACSAuthResource.java         RESTEasy client proxy for OAuth2 token requests
  GOPACSAddressBookResource.java  RESTEasy client proxy for DSO participant lookup
  FlexRequestISPTypeHelper.java   Converts ISP numbers to timestamps (with DST handling)
  OAuth2TokenResponse.java        DTO for OAuth2 token responses
```

Related files outside this package:
- `agent/EmsGOPACSAsset.java` — JPA entity defining the GOPACS asset type (contracted EAN, power attributes)
- `manager/EmsOptimisationService.java` — Manages `GOPACSHandler` lifecycle (creates/destroys handlers when assets are added/removed)
- `manager/EmsOptimisationSetupService.java` — Setup class that optionally creates GOPACS assets

#### Data Flow

OpenRemote acts as an **AGR (Aggregator)** in the UFTP protocol. The message exchange with the DSO (Distribution System Operator) follows this flow:

```mermaid
sequenceDiagram
    participant DSO as DSO (Grid Operator)
    participant OR as OpenRemote (AGR)

    DSO->>OR: 1. FlexRequest
    Note right of OR: DSO requests flexibility for a congestion point<br/>(contains ISPs with max/min power limits)

    OR-->>DSO: 2. FlexRequestResponse
    Note left of DSO: Auto-response after configurable delay

    OR-->>DSO: 3. FlexOffer
    Note left of DSO: Sent after flex offer delay<br/>(mirrors request, price EUR 0.00)

    DSO->>OR: 4. FlexOfferResponse
    Note right of OR: DSO accepts or rejects the offer

    DSO->>OR: 5. FlexOrder
    Note right of OR: DSO orders the accepted flexibility<br/>(updates predicted data points on asset)

    OR-->>DSO: 6. FlexOrderResponse
    Note left of DSO: Auto-response confirming the order
```

**How flex orders feed into optimisation:**

1. `FlexOrder` power values are written as predicted data points on the `EmsGOPACSAsset` attributes (`powerLimitMaximumProfileFlexOrder`, `powerLimitMinimumProfileFlexOrder`)
2. `EmsOptimisationService.updatePowerLimitProfileTotalForecasts()` merges these GOPACS constraints with manual power limits from the parent `EmsEnergyOptimisationAsset`
3. The combined limits are used by the optimisation methods to constrain energy scheduling

#### Inbound Endpoint

The handler deploys a JAX-RS web application at `/gopacs`. Incoming signed UFTP XML messages are posted to:

```
POST /gopacs/message
Content-Type: application/xml
```

Processing steps:
1. Deserialize signed XML envelope
2. Verify cryptographic signature using the sender's public key (from address book)
3. Deserialize UFTP payload
4. Process business logic (update asset attributes, schedule data points)
5. After a delay, send the auto-response (ensures the HTTP response is returned first)

#### Authentication

- **Inbound messages**: Verified using the DSO's public key, fetched from the GOPACS address book (`GET /v2/participants/DSO?contractedEan=<EAN>`) and cached in memory
- **Outbound messages**: Signed with the private key from `GOPACS_PRIVATE_KEY_FILE`, delivered with an OAuth2 Bearer token obtained via client credentials flow from the GOPACS Keycloak instance

#### ISP Handling

ISPs (Imbalance Settlement Periods) are 15-minute intervals. `FlexRequestISPTypeHelper` converts ISP numbers to timestamps and includes special handling for European DST transitions (CET/CEST) on the last Sundays of March and October.

## Redispatch (Intraday Congestion Management)

### Overview

In addition to the UFTP day-ahead flex trading described above, GOPACS provides a **Redispatch** mechanism for intraday congestion management. When a congestion situation is expected today, grid operators publish announcements requesting flexibility from market participants.

> Prerequisites are the same as UFTP — see [Getting Started](#getting-started) for the GOPACS account and contracted EAN. Redispatch additionally requires an API key (see [Configuration](#configuration-1) below).

The Redispatch flow is different from the UFTP flow:

1. **Announcements** — GOPACS publishes congestion announcements via a REST API
2. **EAN effectivity** — CSPs check which of their EANs can help solve the congestion
3. **Bidding** — CSPs place buy/sell orders on connected trading platforms (ETPA, EPEX SPOT, NordPool)
4. **Matching** — The GOPACS algorithm matches orders across platforms
5. **Activation** — The trading platform notifies the CSP when an order is filled
6. **Delivery** — The CSP adjusts power as agreed

```mermaid
sequenceDiagram
    participant Op as Operator
    participant OR as OpenRemote (CSP)
    participant API as GOPACS Redispatch API
    participant TP as Trading Platform (future)

    loop every poll interval (≥ 5 min)
        OR->>API: GET /machineannouncements (CONGESTIONMANAGEMENT, ANNOUNCEMENT_OPEN)
        API-->>OR: announcements
        Note right of OR: Record every newly-seen announcement in history
        OR->>API: GET .../eansolvingeffectivity per announcement
        API-->>OR: EAN categories per announcement
        Note right of OR: Keep announcements where the contracted EAN<br/>is listed; prefer MANDATORY over VOLUNTARY
        OR->>OR: On a new selection: update redispatch* attributes,<br/>record a second history entry with effectivity,<br/>set redispatchBidStatus = PENDING_CONFIRMATION
    end

    Op->>OR: Set redispatchBidPrice, toggle redispatchConfirmBid = true
    OR->>OR: Log bid, set redispatchBidStatus = CONFIRMED
    OR-->>TP: Place order (not yet implemented)
```

**Selection rules:** per poll, the handler keeps only `CONGESTIONMANAGEMENT` / `ANNOUNCEMENT_OPEN` announcements where the contracted EAN appears in some EAN-effectivity category, then prefers `MANDATORY` over `VOLUNTARY` compliance type when more than one matches.

### Configuration

| Variable | Required | Description |
|---|---|---|
| `GOPACS_REDISPATCH_API_KEY` | Yes | API key from GOPACS UI (User Menu > Settings > Generate API-key); required to resolve EAN effectivity per announcement. Polling will not start without it. |
| `GOPACS_REDISPATCH_URL` | No | Base URL for the Redispatch API (default: `https://idcons.gopacs-services.eu`) |
| `GOPACS_REDISPATCH_POLL_INTERVAL_MINUTES` | No | Polling interval in minutes (default: `5`, minimum: `5`) |

### Asset Setup

On the **EMS GOPACS Asset**, configure:

- **`redispatchEnabled`** — Set to `true` to start polling for announcements

### Asset attributes

Every redispatch attribute on `EmsGOPACSAsset`. All status, bid-suggestion and history attributes are written by the handler and surfaced read-only in the UI; only `redispatchEnabled`, `redispatchBidPrice` and `redispatchConfirmBid` are operator-editable.

| Group | Attribute | Type | RO | Purpose |
|---|---|---|---|---|
| Configuration | `redispatchEnabled` | boolean | | Master switch — toggle off/on to (re)start the polling handler. |
| Status | `redispatchAnnouncementId` | text | ✓ | ID of the currently selected announcement, if any. |
| Status | `redispatchComplianceType` | text | ✓ | `MANDATORY` or `VOLUNTARY`. |
| Status | `redispatchAnnouncementMessage` | text (multiline) | ✓ | Free-text description from the DSO. |
| Status | `redispatchStartTime` | timestamp | ✓ | Start of the problem period. |
| Status | `redispatchEndTime` | timestamp | ✓ | End of the problem period. |
| Status | `redispatchBidValidityEnd` | timestamp | ✓ | Latest moment a bid can still be submitted for this announcement. |
| Status | `redispatchRequestedPower` | number (kW) | ✓ | Remaining problem profile, written as predicted data points (15-min ISP grid, 7-day retention). |
| Status | `redispatchEanEffectivity` | text | ✓ | Effectivity category in which the contracted EAN was matched (e.g. `THREE_PHASE_NETWORK_REDUCE`). |
| Status | `redispatchRequestAreaBuy` | text | ✓ | DSO-supplied area description for buy orders. |
| Status | `redispatchRequestAreaSell` | text | ✓ | DSO-supplied area description for sell orders. |
| Status | `redispatchLastPoll` | timestamp | ✓ | Timestamp of the last completed poll cycle (only updated when the API responded). |
| Bid | `redispatchSuggestedPower` | number (kW) | ✓ | _Not yet populated — pending bid pricing strategy follow-up._ |
| Bid | `redispatchSuggestedVolume` | number (kWh) | ✓ | _Not yet populated — pending bid pricing strategy follow-up._ |
| Bid | `redispatchBidPrice` | number (EUR/MWh) | | Operator-supplied bid price. |
| Workflow | `redispatchConfirmBid` | boolean | | Operator toggles to `true` to confirm the active bid; handler resets it after processing. |
| Workflow | `redispatchBidStatus` | text | ✓ | State machine — see below. |
| History | `redispatchAnnouncementHistory` | JSON object | ✓ | One data point on first sight of each polled announcement, plus a richer entry (with effectivity details) when one is selected (90-day retention). |
| History | `redispatchBidHistory` | JSON object | ✓ | One data point per confirmed bid (90-day retention). |

`redispatchBidStatus` values:

- `NONE` — no active announcement
- `PENDING_CONFIRMATION` — operator action required
- `CONFIRMED` — bid logged (and, in future, sent to the trading platform)

### Operator Workflow (Pilot Phase)

1. When a relevant congestion announcement is detected, the asset attributes are updated with the announcement details
2. The `redispatchBidStatus` is set to `PENDING_CONFIRMATION`
3. The operator reviews the announcement info and suggested bid values
4. The operator sets `redispatchBidPrice` (EUR/MWh) and toggles `redispatchConfirmBid` to `true`
5. The bid is confirmed and logged (trading platform integration is pending)

### Resilience and polling

- The polling interval is clamped to a minimum of 5 minutes because GOPACS recommends spacing requests at least that far apart.
- HTTP errors and exceptions on the announcements endpoint **skip the poll and preserve current attributes**, so transient API hiccups do not flap the bid status. Any *successful* poll (HTTP 200) that yields no announcement selected for the contracted EAN clears the active announcement and resets `redispatchBidStatus` to `NONE`. That covers three cases: the response is empty, the response has announcements but none are open `CONGESTIONMANAGEMENT`, or some are but the contracted EAN is not listed in their EAN-effectivity categories. Only a failed fetch (HTTP error / exception) leaves the previous announcement untouched.
- A *persistent* non-200 (e.g. an invalid API key returning 401, or a sustained outage) keeps the previously selected announcement on screen indefinitely. If `redispatchLastPoll` falls behind the configured interval, check the manager logs for `Failed to fetch announcements: HTTP …` (warning) or `Error fetching announcements` (severe).
- The handler **refuses to start** (logs `SEVERE`) when `GOPACS_REDISPATCH_API_KEY` is unset — without it there is no way to resolve EAN effectivity per announcement.
- Toggling `redispatchEnabled` off then on restarts the handler; the same applies when `contractedEAN` is changed. Useful when you need to force a clean state.

### Components

```
gopacs/
  GOPACSRedispatchHandler.java      Polls announcements, checks EAN effectivity, manages bid workflow
  GOPACSAnnouncementResource.java   RESTEasy client proxy for /machineannouncements (public, no auth)
  GOPACSEanEffectivityResource.java RESTEasy client proxy for EAN effectivity (API key auth)
  dto/AnnouncementDto.java          DTO for announcement JSON responses
  dto/TimeSpanDto.java              DTO for time span objects
  dto/EanSolvingEffectivityDto.java DTO for EAN effectivity responses
```

### History

Announcement and bid history are stored as time-series data points on `redispatchAnnouncementHistory` and `redispatchBidHistory`, retained for 90 days and viewable in the OpenRemote history panel.

`redispatchAnnouncementHistory` records **every** polled announcement on first sight (including ones that the EAN-effectivity check later rejects), so the audit trail captures everything GOPACS returned during the handler's lifetime — not just the announcements that became active. When an announcement is then *selected* on a poll, a second, richer history entry is recorded with the matched effectivity details, so an active announcement will appear twice in the timeline (once at first sight, once on selection). To keep memory bounded for long-running handlers, the running set of already-recorded announcement IDs is capped at 10 000 entries (oldest inserted IDs are evicted first — insertion-order/FIFO).

### Future

- **Trading platform integration** — When a platform is chosen (ETPA, EPEX SPOT, or NordPool), automated bid placement will be added
- **Automatic bidding** — After the pilot phase, the confirmation step will be optional
- **Bid pricing engine** — Dynamic bid pricing incorporating BRP imbalance costs, rebound costs, and opportunity costs

### Testing

GOPACS provides a dedicated testing environment. See [Testing UFTP API Flex Messages](https://www.gopacs.eu/wp-content/uploads/2025/12/GOPACS-Testing-receiving-and-sending-flex-messages-by-UFTP-testing-functionality-04-12-2025.pdf) for their guide on sending and receiving flex messages via the UFTP testing functionality.

For additional context on the protocol and contract types, see [Flex Trading with CSC and ATR (UFTP Messages)](https://www.gopacs.eu/wp-content/uploads/2026/02/GOPACS-Flex-trading-with-Capacity-Limiting-Contracts-using-UFTP-messages-11-02-2026.pdf).

#### Company Setup for Testing

To configure your Trading Company for testing Capacity Steering Contracts, follow: [Company Settings for CSC Participation](https://www.gopacs.eu/wp-content/uploads/2025/06/GOPACS-Company-settings-for-participating-in-CSC-Capacity-Steering-Contracts.pdf)
