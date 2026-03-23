# GOPACS Integration

## What is GOPACS?

[GOPACS](https://www.gopacs.eu/) (Grid Operators Platform for Congestion Solutions) is a platform operated by Dutch grid operators (DSOs and TSO) to resolve grid congestion through flexibility trading. When the electricity grid is at risk of overloading, GOPACS sends flexibility requests to market participants (aggregators) who can adjust their energy consumption or production to relieve congestion.

The communication between GOPACS and market participants uses the **UFTP** (Universal Flexibility Trading Protocol), part of the [USEF](https://www.usef.energy/) framework, implemented via the [Shapeshifter](https://github.com/shapeshifter/shapeshifter-library-java) library.

For detailed documentation, see: [GOPACS documents and manuals](https://www.gopacs.eu/en/documents-and-manuals/)

## Getting Started

To participate in GOPACS flex trading through OpenRemote, you need:

1. **A GOPACS account** — Register as a Trading Company at [gopacs.eu](https://www.gopacs.eu/)
2. **OAuth2 client credentials** (`client_id` and `client_secret`) — See [OAuth2 Client Credentials for API Clients](https://www.gopacs.eu/wp-content/uploads/2025/12/GOPACS-OAuth2-Client-credentials-for-API-Clients-03-12-2025.pdf)
3. **A signing key pair** — An Ed25519 private key file for signing UFTP messages. The corresponding public key must be registered with GOPACS
4. **A contracted EAN** — The EAN (European Article Number) identifying your grid connection point, as agreed with your DSO

### Configuration

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

### Asset Setup

In OpenRemote, create an **EMS GOPACS Asset** as a child of an **EMS Energy Optimisation Asset** and set the `contractedEAN` attribute to your grid connection's EAN.

Alternatively, when creating a new **EMS Energy Optimisation Asset**, you can enable the "Include GOPACS" attribute to have the GOPACS child asset created automatically. Note that this only works during initial asset creation — if the **EMS Energy Optimisation Asset** already exists, you need to manually create the **EMS GOPACS Asset** as a child.

## Developer Guide

### Components

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

### Data Flow

OpenRemote acts as an **AGR (Aggregator)** in the UFTP protocol. The message exchange with the DSO (Distribution System Operator) follows this flow:

```
DSO (Grid Operator)                         OpenRemote (AGR)
     |                                            |
     |──── 1. FlexRequest ───────────────────────>|  DSO requests flexibility for a congestion point
     |                                            |  (contains ISPs with max/min power limits)
     |                                            |
     |<─── 2. FlexRequestResponse ────────────────|  Auto-response after configurable delay
     |                                            |
     |<─── 3. FlexOffer ──────────────────────────|  Sent after flex offer delay
     |                                            |  (mirrors request, price EUR 0.00)
     |                                            |
     |──── 4. FlexOfferResponse ─────────────────>|  DSO accepts or rejects the offer
     |                                            |
     |──── 5. FlexOrder ─────────────────────────>|  DSO orders the accepted flexibility
     |                                            |  (updates predicted data points on asset)
     |                                            |
     |<─── 6. FlexOrderResponse ──────────────────|  Auto-response confirming the order
     |                                            |
```

**How flex orders feed into optimisation:**

1. `FlexOrder` power values are written as predicted data points on the `EmsGOPACSAsset` attributes (`powerLimitMaximumProfileFlexOrder`, `powerLimitMinimumProfileFlexOrder`)
2. `EmsOptimisationService.updatePowerLimitProfileTotalForecasts()` merges these GOPACS constraints with manual power limits from the parent `EmsEnergyOptimisationAsset`
3. The combined limits are used by the optimisation methods to constrain energy scheduling

### Inbound Endpoint

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

### Authentication

- **Inbound messages**: Verified using the DSO's public key, fetched from the GOPACS address book (`GET /v2/participants/DSO?contractedEan=<EAN>`) and cached in memory
- **Outbound messages**: Signed with the private key from `GOPACS_PRIVATE_KEY_FILE`, delivered with an OAuth2 Bearer token obtained via client credentials flow from the GOPACS Keycloak instance

### ISP Handling

ISPs (Imbalance Settlement Periods) are 15-minute intervals. `FlexRequestISPTypeHelper` converts ISP numbers to timestamps and includes special handling for European DST transitions (CET/CEST) on the last Sundays of March and October.

## Testing

GOPACS provides a dedicated testing environment. See [Testing UFTP API Flex Messages](https://www.gopacs.eu/wp-content/uploads/2025/12/GOPACS-Testing-receiving-and-sending-flex-messages-by-UFTP-testing-functionality-04-12-2025.pdf) for their guide on sending and receiving flex messages via the UFTP testing functionality.

For additional context on the protocol and contract types, see [Flex Trading with CSC and ATR (UFTP Messages)](https://www.gopacs.eu/wp-content/uploads/2026/02/GOPACS-Flex-trading-with-Capacity-Limiting-Contracts-using-UFTP-messages-11-02-2026.pdf).

### Company Setup for Testing

To configure your Trading Company for testing Capacity Steering Contracts, follow: [Company Settings for CSC Participation](https://www.gopacs.eu/wp-content/uploads/2025/06/GOPACS-Company-settings-for-participating-in-CSC-Capacity-Steering-Contracts.pdf)
