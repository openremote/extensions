# OpenRemote + hawkBit demo

This demo runs an isolated OpenRemote stack with the local hawkBit extension,
a hawkBit update server, the
[OpenRemote Firmware Management UI](https://github.com/openremote/service-hawkbit-ui),
and a PostgreSQL database for each application. It then creates one OpenRemote
asset, turns it into a hawkBit update target, uploads a small demonstration
firmware artifact through the extension API, assigns it, and simulates the
device completing the update.

## The idea in one minute

OpenRemote knows **what the device is**: its asset, realm, attributes, and
business context. hawkBit knows **how to safely roll software out**: artifacts,
versions, target assignments, rollout groups, device polling, and status.

The extension connects these two roles:

1. An OpenRemote asset with `firmwareTarget: true` becomes a hawkBit target.
2. Attributes with `firmwareMetadata: true` become searchable target metadata.
3. Firmware-management calls go through OpenRemote's authenticated API to
   hawkBit's Management API.
4. The Firmware Management UI provides the catalog, target-filter, assignment,
   and rollout workflows exposed by that API.
5. Devices use hawkBit's DDI API to poll, download, install, and report status.

## Run the complete demo

From the repository root:

```bash
./hawkbit/demo/start.sh
./hawkbit/demo/prepare-demo.sh
./hawkbit/demo/status.sh
./hawkbit/demo/simulate-device.sh
./hawkbit/demo/status.sh
```

Open the applications at any point:

- OpenRemote: <https://localhost:9443/manager/> (`admin` / `secret`)
- Firmware Management:
  <http://localhost:8004/services/hawkbit/ui/master> (`admin` / `secret`)
- hawkBit API explorer: <http://localhost:8083/hawkbit/> (`hawkbit` / `hawkbit`)

The OpenRemote URL uses a local self-signed certificate, so your browser will
ask you to accept it once.

In OpenRemote, inspect the **OTA Demo Sensor** asset. The
`firmwareTargetInfo` attribute holds its hawkBit controller ID and device
security token. The `deviceModel` and `region` attributes are synchronized as
hawkBit metadata.

In Firmware Management, inspect **Targets**, **Software modules**,
**Distribution sets**, **Target filters**, and **Rollouts**. This application
logs users in through OpenRemote and calls only the extension's
`/firmware/*` API; it does not bypass OpenRemote to call hawkBit directly. It
also registers itself as the **Firmware Management** external service in
OpenRemote, using a dedicated service account created by `start.sh`.

The presentation-friendly command-line view is
`./hawkbit/demo/status.sh`: before the simulator runs it shows an assigned
update, and afterwards it shows the new installed distribution and a finished
action. The raw hawkBit API explorer remains useful when you want to inspect
the underlying protocol objects.

To stage another update after completing the first one, choose a new version:

```bash
FIRMWARE_VERSION=1.0.1 ./hawkbit/demo/prepare-demo.sh
./hawkbit/demo/simulate-device.sh
```

## Demo narration

1. "OpenRemote is the source of device identity and context. Marking an asset
   for firmware management automatically provisions the matching target."
2. "Firmware metadata lets us segment fleets by model, region, hardware
   revision, customer, or any other OpenRemote attribute."
3. "A software module contains one kind of software and its binary artifacts.
   A distribution set combines compatible modules into one deployable release."
4. "The assignment does not push bytes blindly. The device polls hawkBit,
   receives a deployment action, downloads the artifact, verifies it, installs
   it, and reports progress and the final result."
5. "For large fleets, target filters and rollouts add staged groups, pause/start
   control, and failure thresholds."

## Guided UI tour

The prepared demo exercises a direct assignment to one target. Use the UI to
see the same objects without writing API requests:

1. Open **Targets** and select `hawkbitDemoDevice00001`. Its `deviceModel` and
   `region` metadata came from the OpenRemote asset.
2. Open **Software modules**. `Demo sensor application` contains the uploaded
   text artifact and represents one independently versioned software unit.
3. Open **Distribution sets**. `Demo firmware` combines compatible modules into
   the release assigned to the device.
4. Run `simulate-device.sh`, refresh **Targets**, and inspect the finished
   action and installed distribution.
5. Stage version `1.0.1`, then inspect the new module, artifact, distribution,
   and action before simulating the second update.

Rollouts are deliberately a separate workflow from this one-device assignment.
In **Target filters**, create a reusable fleet query. Start with the simple
hawkBit RSQL query
`controllerId==hawkbitDemoDevice00001`, which selects the prepared device. In
**Rollouts**, choose that filter and `Demo firmware`, select the number of
deployment groups, and open **Advanced** to configure success and error
thresholds. You can then start or pause the rollout and inspect each deployment
group's progress. For a meaningful multi-group presentation, create several
OpenRemote assets with the same `firmwareTarget` marker and matching metadata
before creating the rollout.

## Stop or reset

Stop the containers but preserve all demo data:

```bash
./hawkbit/demo/stop.sh
```

To delete the isolated demo databases and start again from empty data:

```bash
docker compose -f hawkbit/demo/compose.yaml down --volumes
```

The credentials and ports are intentionally simple and local-only. The demo
image adds a tiny server-side configuration shim because containers use private
OpenRemote hostnames while the browser uses `localhost`; the Firmware
Management frontend itself comes from the official service image. Do not use
this Compose file as a production deployment.
