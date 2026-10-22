## Distro Energy Integration

### What is Distro Energy?

[Distro Energy](https://developers.distro.energy) trades on the day-ahead electricity market on behalf of its
participants. Within Distro each participant is a **portfolio**. A portfolio submits day-ahead orders (volumes per
15-minute interval, optionally with a price range) and Distro places them on the market.

OpenRemote uses two Distro APIs, each optional and independent of the other:

- **Day-Ahead Order Submissions** submits the net power forecast of an EMS site as day-ahead market orders (this
  section)
- **GOPACS Connector** places redispatch orders in response to GOPACS congestion announcements (see
  [GOPACS Orders](#gopacs-orders))

The day-ahead API rules that shape the integration:

- Orders may be submitted up to five days in advance
- A new submission replaces any earlier submission for the same delivery day
- For next-day delivery Distro uses the most recent submission received before **09:00 Europe/Amsterdam** on the
  preceding day

### Getting Started

To submit day-ahead orders through OpenRemote, you need the following:

1. **Portfolio identifier** — The participant identifier agreed with Distro. It is a pre-defined prefix, or for
   submissions at individual-meter level the prefix followed by the meter's EAN-18
2. **Client key** — The API key for the Distro API, sent as the `x-client-key` header
3. **Net power forecast** — An `Ems Energy Optimisation Asset` whose `powerNet` attribute has predicted data points
   for the coming days. These predicted values are what gets submitted

#### Configuration

The following environment variables must be set on the OpenRemote manager:

| Variable                         | Required | Description                                                                                                      |
| -------------------------------- | -------- | ---------------------------------------------------------------------------------------------------------------- |
| `DISTRO_ENERGY_CLIENT_KEY`       | Yes      | Client key for the Distro API, sent as the `x-client-key` header. The handler does not start without it.         |
| `DISTRO_ENERGY_BASE_URL`         | No       | Base URL of the Distro API (default: `https://ibt.prod.distro.energy/api/v1`)                                    |
| `DISTRO_ENERGY_TIMEZONE`         | No       | Market time zone, used for delivery days, ISP positions and the 09:00 gate closure (default: `Europe/Amsterdam`) |
| `DISTRO_ENERGY_REQUEST_INTERVAL` | No       | Minutes between submission runs (default: `60`)                                                                  |

#### Asset Setup

In OpenRemote, create an `Ems Distro Energy Asset` as a child of an `Ems Energy Optimisation Asset` and set the
`portfolio` attribute to your Distro portfolio identifier. The parent's `powerNet` forecast is submitted for this
portfolio.

| Attribute        | Value Type       | Read-only | Purpose                                                                                                                     |
| ---------------- | ---------------- | --------- | --------------------------------------------------------------------------------------------------------------------------- |
| `portfolio`      | Text             |           | Distro portfolio identifier. Setting or changing it (re)starts the handler.                                                 |
| `lastSubmission` | Timestamp        | ✓         | When the last run that submitted at least one delivery day finished.                                                        |
| `daysSubmitted`  | Positive integer | ✓         | Number of delivery days submitted by the last run, which in practice is the forecast horizon in days. Written on every run. |

A run that finds no forecast at all sets `daysSubmitted` to `0` and leaves `lastSubmission` untouched, so a stalled
forecast shows up as a fresh `0` next to an old `lastSubmission`.

### Developer Guide

#### Components

```
distroenergy/
  DistroEnergyHandler.java        Schedules the submission runs, reads the powerNet forecast,
                                  builds the payload and posts it per delivery day
  DayAheadResource.java           RESTEasy client proxy for POST /trader/{portfolio}/day-ahead/data
  dto/DayAheadSubmission.java     DTO for one delivery day (data, day, creationTimestamp)
  dto/SubmissionData.java         DTO for one ISP entry (position, priceLow, priceHigh, volume)
```

`EmsOptimisationService` owns the handler lifecycle. It starts a handler at boot for every `Ems Distro Energy Asset`
with a `portfolio`, restarts it when the asset or its `portfolio` changes, and stops it when the asset is deleted.
Handlers are keyed by asset id, so editing the portfolio replaces the handler instead of leaving the old one running.

A handler is not deployed, and a warning is logged, when:

- `portfolio` is blank
- the asset has no parent, or the parent is not an `Ems Energy Optimisation Asset`
- `DISTRO_ENERGY_CLIENT_KEY` is not set, or the client cannot be created

These failures only affect that asset. The rest of the EMS service keeps running.

#### Data Flow

```mermaid
sequenceDiagram
    participant OR as OpenRemote (EMS)
    participant DB as Predicted datapoints
    participant API as Distro Day-Ahead API

    loop Every DISTRO_ENERGY_REQUEST_INTERVAL minutes (first run on the next :30)
        Note right of OR: Delivery days: tomorrow (before 09:00)<br/>or the day after (from 09:00) up to today + 5

        loop Per delivery day, until the first day without a forecast
            OR->>DB: Query parent powerNet (15-min AVG)
            DB-->>OR: Predicted datapoints
            Note right of OR: Build one entry per ISP<br/>(92, 96 or 100), fill gaps
            OR->>API: POST /trader/{portfolio}/day-ahead/data
            API-->>OR: 200 OK
        end

        OR->>OR: Update daysSubmitted and lastSubmission
    end
```

#### Schedule

The first run starts at the next half hour after deploy, then repeats every `DISTRO_ENERGY_REQUEST_INTERVAL` minutes. Each run submits
the delivery days from the first open day up to five days after today, market time:

- Before 09:00 the first open day is tomorrow
- From 09:00 tomorrow is closed, and the first open day is the day after tomorrow

The run stops at the first day without any forecast, since the days after it are beyond the forecast horizon too. There
is no catch-up state: because Distro replaces a day on every submission, the next run simply sends the latest forecast
again. With the default interval the 08:30 run is the last one that reaches tomorrow.

#### Payload

One request is sent per delivery day:

```json
{
  "day": 20260602,
  "creationTimestamp": 1780300800,
  "data": [
    { "position": 1, "volume": -10.5 },
    { "position": 2, "volume": -9.75 }
  ]
}
```

| Field                   | Source                                                                                                                  |
| ----------------------- | ----------------------------------------------------------------------------------------------------------------------- |
| `day`                   | Delivery day in `YYYYMMDD` format, in market time                                                                       |
| `creationTimestamp`     | Current time in Unix seconds                                                                                            |
| `position`              | ISP number within the day, starting at `1` for 00:00–00:15. A day has 96 positions, 92 on the switch to DST, 100 off it |
| `volume`                | Average forecast `powerNet` over the ISP converted to kWh: `-powerNet × 0.25`, rounded to 0.01 kWh                      |
| `priceLow`, `priceHigh` | Not sent, so Distro submits market orders                                                                               |

Distro signs buys negative and sells positive. Positive `powerNet` is import, so a forecast of 42 kW import over an ISP
becomes a volume of `-10.5` kWh.

#### Gap Handling

Distro requires every position of the day to carry a volume, so gaps in the forecast are filled before submitting:

- **No forecast for the whole day** — The day is not submitted. Sending it would post a zero trading position for a day
  without a forecast
- **Gaps inside the forecast** — Submitted as `0.0` and logged as a warning, because `0.0` is a real trading position
- **Gaps after the forecast ends** — Submitted as `0.0` up to midnight, logged at fine level. This is expected when the
  forecast horizon ends partway through a day
- **Repeated hour on the switch off DST** — A forecast producer that writes a fixed 96-slot day has no values for the
  four extra ISPs. They reuse the next forecast value of the day instead of trading the hour away, and a warning is
  logged

#### Resilience

- A failure for one delivery day (HTTP error or exception) is logged as `Failed to submit day-ahead forecast for
portfolio … and day …` and the run continues with the next day. The schedule itself keeps running
- Stopping the handler cancels the schedule, interrupts any request in flight and closes the HTTP client
- Use `daysSubmitted` and `lastSubmission` to spot a stalled integration without reading the logs

### GOPACS Orders

Distro relays orders placed through its GOPACS Connector API to ETPA, where GOPACS can match them to solve grid
congestion. OpenRemote does not poll GOPACS for this: the announcement comes from an `Ems GOPACS Asset` with
redispatch enabled (see [GOPACS Integration](GopacsIntegration.md)) and reaches the Distro asset through **attribute
links**. Neither handler references the other, so the Distro asset needs no portfolio or day-ahead setup.

#### Configuration

| Variable                      | Required | Description                                                                                                                           |
| ----------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------- |
| `DISTRO_GOPACS_CLIENT_ID`     | Yes      | OAuth client ID provided by Distro                                                                                                    |
| `DISTRO_GOPACS_CLIENT_SECRET` | Yes      | OAuth client secret provided by Distro                                                                                                |
| `DISTRO_GOPACS_TENANT_ID`     | Yes      | Distro tenant, e.g. `slimstrandnet`                                                                                                   |
| `DISTRO_GOPACS_URL`           | No       | Base URL of the API (default: `https://api.distro.energy/v1`, development: `https://api.dev.distro.energy/v1`)                        |
| `DISTRO_GOPACS_TOKEN_URL`     | No       | OAuth token endpoint (default: `https://auth.distro.energy/oauth2/token`, development: `https://auth.dev.distro.energy/oauth2/token`) |

The handler does not start without the three required variables. Distro must also whitelist the manager's outbound IP
address. Tokens are requested with client credentials and scope `distro-api/gopacs.access`, cached and refreshed
shortly before they expire.

#### Asset Setup

1. Set `gopacsEnabled` to `true` on the `Ems Distro Energy Asset`. For pre-filled quantities, the asset must be a child
   of the `Ems Energy Optimisation Asset` whose batteries will deliver the order
2. On the `Ems GOPACS Asset`, add an `attributeLinks` meta item to each of these attributes, linking to the Distro
   asset:

| `Ems GOPACS Asset` attribute | Linked `Ems Distro Energy Asset` attribute |
| ---------------------------- | ------------------------------------------ |
| `contractedEAN`              | `gopacsEan`                                |
| `redispatchAnnouncementId`   | `gopacsAnnouncementId`                     |
| `redispatchStartTime`        | `gopacsStartTime`                          |
| `redispatchEndTime`          | `gopacsEndTime`                            |
| `redispatchBidValidityEnd`   | `gopacsBidValidityEnd`                     |
| `redispatchEanEffectivity`   | `gopacsEanEffectivity`                     |

For example, the meta item on `redispatchAnnouncementId`:

```json
"attributeLinks": [{ "ref": { "id": "<distro asset id>", "name": "gopacsAnnouncementId" } }]
```

A link forwards every update of its source, including the unchanged values the redispatch poller rewrites on each poll.
The handler only acts when a value actually changes.

| Attribute                            | Value Type       | Read-only | Purpose                                                                                |
| ------------------------------------ | ---------------- | --------- | -------------------------------------------------------------------------------------- |
| `gopacsEnabled`                      | Boolean          |           | Starts or stops the GOPACS order handler                                               |
| `gopacsEan` … `gopacsEanEffectivity` | Linked           | ✓         | Announcement inputs, see the table above                                               |
| `gopacsOrderType`                    | Text             |           | `BUY` or `SELL`. Pre-filled from the announcement, may be overridden                   |
| `gopacsOrderQuantity`                | Number (kW)      |           | Constant power over the whole window. Pre-filled from the batteries, may be overridden |
| `gopacsOrderPrice`                   | Number (EUR/MWh) |           | Set by the operator. Changing it replaces the open orders of the current announcement  |
| `gopacsConfirmOrder`                 | Boolean          |           | Set to `true` to place the order; reset by the handler                                 |
| `gopacsCancelOrders`                 | Boolean          |           | Set to `true` to cancel every open order; reset by the handler                         |
| `gopacsOrders`                       | JSON object      | ✓         | Orders still being tracked, by reference, as last returned by Distro                   |
| `gopacsOrderHistory`                 | JSON object      | ✓         | One data point per order action, status change or refused confirmation                 |
| `gopacsSettlementHistory`            | JSON object      | ✓         | One data point per settled trade                                                       |
| `gopacsLastSync`                     | Timestamp        | ✓         | When the tracked orders were last refreshed                                            |

#### Workflow

```mermaid
sequenceDiagram
    participant G as Ems GOPACS Asset
    participant D as Ems Distro Energy Asset
    actor Op as Operator
    participant API as Distro GOPACS API

    G->>D: attribute links (announcement, window, effectivity)
    D->>D: Pre-fill gopacsOrderType and gopacsOrderQuantity
    Op->>D: Set gopacsOrderPrice, toggle gopacsConfirmOrder
    D->>API: POST /gopacs/{tenant}/orders
    loop Every 5 minutes while orders are tracked
        D->>API: GET /gopacs/{tenant}/orders/{reference}
        D->>API: GET /settlements/gopacs/{tenant} (once SETTLED)
    end
    Op->>D: Change gopacsOrderPrice
    D->>API: PUT /gopacs/{tenant}/orders/{reference}
    G->>D: announcement closes or changes
    D->>API: DELETE /gopacs/{tenant}/orders/{reference}
```

#### Order Sizing

**Order type** comes from the GOPACS EAN effectivity category, `BUY_<effectivity>_SELL_<effectivity>`, ranked
`NONE` < `VERY_LOW` < `LOW` < `MODERATE` < `HIGH` < `VERY_HIGH` < `MAXIMUM`. The more effective side wins.
`BUY_NONE_SELL_NONE` suggests nothing. On `BUY_MODERATE_SELL_MODERATE` the side the batteries can offer more of wins,
`BUY` on a draw. `BUY` means consume more (charge) and `SELL` means consume less (discharge).

**Quantity** is the constant power the batteries under the parent optimisation asset can hold for the whole window:

- Only batteries allowed to charge (`BUY`) or discharge (`SELL`) and not `disconnected` count
- Power: the sum of `chargePowerMaximum` or `dischargePowerMaximum`
- Energy: the sum of the headroom to `energyLevelPercentageMaximum` (`BUY`, divided by `chargeEfficiency`) or to
  `energyLevelPercentageMinimum` (`SELL`, multiplied by `dischargeEfficiency`), divided by the window duration
- The lower of the two, rounded down to 100 kW steps. Distro's minimum order is 0.1 MW

#### Placing an Order

On `gopacsConfirmOrder` the handler checks that there is an announcement, an 18-digit EAN, a `BUY` or `SELL` type, a
quantity of at least 100 kW, a price, and a quarter-hour window that has not started. A failed check is logged and
recorded as `REFUSED` in `gopacsOrderHistory`. The order is sent with:

| Field                  | Source                                                                                                                                    |
| ---------------------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| `reference`            | A new random UUID                                                                                                                         |
| `quantity`             | `gopacsOrderQuantity` in MW                                                                                                               |
| `start`, `end`         | `gopacsStartTime`, `gopacsEndTime` in Unix seconds                                                                                        |
| `metadata`             | `{"announcementId": <gopacsAnnouncementId>}`, which links the order to its announcement                                                   |
| `customExpirationTime` | `gopacsBidValidityEnd` rounded down to a quarter-hour, unless it is unknown, passed, or after Distro's default of 15 minutes before start |

The order is tracked as `PENDING` before it is posted. When Distro rejects it with a `4xx` it is untracked again. On a
timeout or a `5xx` it stays tracked, because Distro may have accepted it: the next poll picks up its real status, or
untracks it when Distro answers `404`. Either way the failure is recorded as `CREATE_FAILED`.

#### Order Lifecycle

`PENDING` and `SUBMITTED` orders are open: a price change replaces them, and they are cancelled when their announcement
closes or is replaced by another, or when the operator sets `gopacsCancelOrders`. A `LOCKED` order cannot be changed
(Distro answers `423`) and keeps its old price. `MATCHED` orders stay tracked until they are `SETTLED`, at which point
all their trades are recorded in `gopacsSettlementHistory`; if that fetch fails, the next poll tries again. Orders that are `SETTLED`, `REVERTED`, `REJECTED` or `CANCELED`
are no longer tracked. The tracked orders are saved on the asset, so a restart picks them up again.

#### Components

```
distroenergy/
  DistroGopacsHandler.java        Pre-fills, places, replaces, cancels and polls orders for one asset
  DistroGopacsResource.java       RESTEasy client proxy for the GOPACS Connector API
  dto/GopacsOrderRequest.java     Create and replace body
  dto/GopacsOrder.java            Order as stored by Distro
  dto/GopacsSettlement*.java      Settled trades
```

### Testing

To test against Distro's development environment, set `DISTRO_ENERGY_BASE_URL` to
`https://ibt.dev.distro.energy/api/v1`. The Distro spec also defines a `GET /healthz` endpoint for checking that the API is reachable.

Automated tests:

- `DistroEnergyHandlerTest` — payload building, volume conversion, DST days and gap filling
- `DistroEnergyHandlerStatusTest` — status attributes written after a run and the gate closure boundary
- `DistroGopacsHandlerTest` — order type and quantity rules, expiration, and the create, replace, cancel and poll flows
- `DistroGopacsHandlerHttpTest` — OAuth token request, bearer reuse and the JSON wire format against WireMock
- `EmsOptimisationServiceDistroEnergyTest` — day-ahead and GOPACS handler lifecycle in `EmsOptimisationService`
