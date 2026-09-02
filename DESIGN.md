## Introduction

The OpenRemote platform is designed for versatility, supporting diverse domains such as energy management, smart cities, and fleet management.
As the platform scales, it is no longer practical or efficient for the Core repository ([openremote/openremote](https://github.com/openremote/openremote/)) to natively support every specific protocol, UI widget or business logic requirement.

## Purpose

The OpenRemote extension mechanism decouples domain-specific functionality from the platform Core.
This mechanism allows developers to package resources such as Protocol Agents, Asset Types, UI Components, and Logic (Container Services, Rules) into a single, deployable unit called an extension.

By standardizing how these extensions are built and integrated, we aim to achieve the following:

- **Enhanced Maintainability**: Create a leaner Core codebase while supporting all existing and future domains.
- **Simplified User Experience**: Making functionality optional ensures users are not overwhelmed by Agents, Asset Types, or menu items that are irrelevant to their specific project.
- **System Resource Efficiency**: A monolithic architecture forces the system to load all libraries and background processes regardless of use. Modularization allows for a smaller runtime which reduces resource consumption (RAM, CPU) on edge gateways and industrial hardware.
- **Architectural Rigor & API Maturity**: Formalizing the boundary between the Core and Extensions forces the development of stable, well-documented APIs. It encourages developers to think in terms of reusability and clean contracts rather than making ad-hoc changes to the Core.

## Conceptual Definition

To establish a clear framework, we must define what constitutes an Extension within OpenRemote.
An extension is not merely a piece of code; it is a self-contained package that introduces new capabilities or modifies existing behavior without requiring a rebuild of the OpenRemote Core.

### What is an Extension?

Technically, an extension is a versioned artifact (such as a JAR file) that the OpenRemote Manager can discover, load, and initialize at runtime.
Extensions are not dynamically installed, loaded, or unloaded at runtime.
The Extension communicates with the Core through predefined extension points.
These extension points are stable APIs that allow the Extension to register its own Container Services, UI elements or Asset Types into the Manager.

### Extension Types

In this section, Extensions are categorized by the nature of the resources they provide to the platform.
There is currently no immediate need for each of these types; however, having a comprehensive list of possible future types helps with thinking about implementation requirements for future extensions.

#### Functional Extensions

These add backend logic, connectivity, or processing power to the system.
They typically run as background processes or service providers.

- **Connectivity**: Protocol agents such as Artnet, ChirpStack, KNX, and ZWave.
- **Logic & Rules**: Support for Groovy/Flow rules, Container Services (Forecasting, Simulators), Rule languages (similar to JavaScript and JSON).
- **Identity**: Pluggable Identity providers (similar to the Keycloak and Basic IdPs).
- **Storage**: Infrastructure drivers for Operational data (JDBC) or Historical data (TimescaleDB).

Extensions may depend on external infrastructure or services.
Provisioning and configuring such infrastructure is outside the scope of the extension mechanism and remains the responsibility of the deployment or project configuration.

#### Data Extensions

These define how data is structured and transformed within the system.

- **Model Definitions**: Domain-specific Asset Types (Energy, HVAC, Smart City) and Setup Types (Demo, Load testing).
- **Data Integrity**:
  - **Value Types**: Defining custom data structures or units of measurement.
  - **Value Validation**: Logic to ensure incoming data meets specific criteria (range checks, schema validation, or mandatory fields).
- **Processing & Presentation**:
  - **Value Filters**: Data transformation logic (JsonPath, RegEx).
  - **Value Formatting**: Rules for how data is rendered in the UI or exported (unit conversion, decimal rounding, or localized date strings).

#### Visual Extensions

These enhance the Manager UI or provide end-user applications.
They are primarily client-side assets that extend the frontend.

- **Widgets**: Custom Insights widgets for dashboards and data visualization (e.g. a specialized gauge for energy consumption).
- **Custom Applications**: Project-specific UIs developed to meet unique domain requirements (e.g. an Alarms App or a Fleet App).
- **System & Utility UIs**: Integration of technical or third-party interfaces directly into the platform, such as Swagger UI for API exploration.

#### Composite Extensions

A collection of Functional, Visual and Data extension used to implement functionality for a specific domain or end-user group.
For example an Energy Extension could include the Energy Asset types (Data), a Modbus Agent for meters (Functional), and Energy Dashboard widgets (Visual).

### Evolution & Prioritization

The transition to a more modular platform will be done iteratively.
We will focus on introducing extensions into the backend and the structural relationship between extensions before introducing the complexity of frontend extension points or new data processing APIs.

#### Phase 1: Building the Infrastructure

Existing OpenRemote functionality is first migrated into a modular structure:

- **Connectivity**: Decoupling Protocol Agents (KNX, ZWave, Artnet etc.) to allow for project-specific Agents and reduce the Core codebase complexity.
- **Logic & Rules**: Reusable Groovy/Flow rules, Container Services (Forecasting, Simulators).
- **Model Definitions**: Supporting optionally installing Asset Types (Energy, Smart City) and Setup Types (Demo, Load tests).
- **Composition**: Implementing the logic for extension dependencies and composite extensions. This allows extensions to build upon one another and be composed into domain-specific solutions.

#### Phase 2: User Interfaces

Once the initial infrastructure has been built, the scope will expand to include standalone UIs.

- **Custom Applications**: Enabling project or domain specific UIs to be deployed as extensions (Alarms or Fleet App).
- **System & Utility UIs**: Integrating technical tools directly into the platform, such as Swagger UI.

#### Future

These represent long-term goals that require the development of new internal APIs within the OpenRemote Core before they can be fully supported:

- **Widgets**: Custom Insights widgets for dashboards and data visualization (specialized energy gauges).
- **Data Integrity & Processing**: Advanced value types, validation, formatting, and specialized filters.
- **Rules**: Integration of entirely new Rule Engine languages (JavaScript, JSON).
- **Storage**: Pluggable infrastructure drivers for database management.

## Technical Specification

In the initial implementation, the extension mechanism leverages the existing Gradle build system and OpenRemote's established Service Provider Interfaces (SPI).
This ensures that extensions are discovered and initialized using the same robust patterns currently used by the Core and existing custom projects.

### Namespace Convention

To ensure architectural clarity and prevent classpath collisions, all extensions must adopt the following base package:
`org.openremote.extension.{extension-name}`

This convention applies to both the Java package structure and the internal resource directory layout.

### Dependency Management (Build-time)

Extensions are developed as independent Gradle projects.
To include an extension in an OpenRemote deployment, it is added as a standard dependency in the project's `build.gradle`.

- **Transitive Dependencies**: Gradle handles the resolution of shared libraries between the Core and multiple extensions, ensuring a consistent and conflict-free classpath.
- **Composition**: Composite Extensions define a collection of dependencies in their `build.gradle`, allowing a developer to include a single "Composite" artifact to pull in a complete suite of functional and data modules.

### Discovery and Registration (SPI)

Extensions register their components and initialization logic by implementing standard OpenRemote SPIs.
The Manager scans the classpath at startup to find and execute these implementations via the Java `ServiceLoader`.

- **Asset Types**: Extensions must implement the `org.openremote.model.AssetModelProvider` SPI. This allows the extension to register its domain-specific Asset Types (Energy, HVAC) in a Manager instance.
- **Container Services**: Background logic (such as forecasting or simulators) is registered via the `org.openremote.model.ContainerService` SPI. These services are managed by the platform container, allowing them to be started and stopped in coordination with the Manager's lifecycle.
- **Setup Tasks**: Extensions must implement the `org.openremote.model.setup.SetupTasks` SPI. This is the primary mechanism for initializing the extension within a project. Setup tasks are responsible for:
  - Creating default assets and system configurations.
  - **Rule Creation**: Programmatically creating and persisting Groovy and Flow rules into the platform.

### Database Migrations (Flyway)

Extensions requiring database migrations must use Flyway.

- **Path**: `src/main/resources/org/openremote/extension/{name}/setup/database/`
- **Execution**: Migrations are triggered during the extension activation sequence, before `SetupTasks` or `ContainerServices` are initialized.

### Testing Strategy (Spock & Groovy)

To ensure reliability, extensions should include a comprehensive test suite.
Following the OpenRemote Core standard, we use the Spock Framework with Groovy for integration and unit testing.

Test-specific setups (like Keycloak or Manager configurations) can be isolated within the `src/test` directory, ensuring that the production artifact remains lean while the development cycle remains robust.

### Artifact Structure

The following directory layout represents an example Energy extension.
Note the separation of concerns between production code (`src/main`) and testing logic (`src/test`).

```text
energy/
├── build.gradle                       # Extension build and dependencies
├── src/main/java/org/openremote/extension/energy/
│   ├── model/                         # Java Asset implementations
│   │   ├── ElectricityAsset.java
│   │   └── EnergyModelProvider.java   # Implements AssetModelProvider SPI
│   ├── manager/                       # Core logic & Container services
│   │   └── EnergyOptimisationService.java # Implements ContainerService SPI
├── src/main/resources/
│   ├── org/openremote/extension/energy/setup/database/ # Flyway scripts
│   │   └── V20260131_01__RenameEnumValues.sql
│   └── META-INF/services/             # SPI Registration
│       ├── org.openremote.model.AssetModelProvider
│       ├── org.openremote.model.ContainerService
│       ├── org.openremote.model.ExtensionMetadata
│       └── org.openremote.model.setup.SetupTasks
├── src/test/groovy/org/openremote/extension/energy/
│   ├── EnergyOptimisationTest.groovy  # Spock Integration Tests
│   ├── ForecastSolarServiceTest.groovy
│   └── ManagerTestSetup.groovy        # Test-specific environment logic
└── src/test/resources/
    └── META-INF/services/
        └── org.openremote.model.setup.SetupTasks # Test-only setup tasks
```

## Deployment & Lifecycle Management

This section defines how extensions are packaged, discovered, and activated using a programmatic metadata provider.

### Deployment & Packaging

In the initial implementation, extensions are treated as static modules bundled into the OpenRemote Manager's classpath.

- **Build-time Integration**: Extensions are added as `implementation` dependencies in the deployment project's Gradle configuration.
- **The Artifact**: Every extension must include an implementation of the `ExtensionMetadata` SPI.
- **Registration**: The implementation is registered in `src/main/resources/META-INF/services/org.openremote.model.ExtensionMetadata`.
- **Artifact Inclusion**: During the Docker build, JARs are placed in the Manager's library directory for automatic JVM loading.

### The Metadata SPI (`ExtensionMetadata`)

To manage dependencies and activation, the Manager uses a dedicated SPI.
This interface acts as the "identity card" for the extension, providing the Manager with the logical information needed to build the dependency graph.

**Example Implementation:**

```java
package org.openremote.extension.energy;

import org.openremote.model.ExtensionMetadata;
import java.util.Set;

public class EnergyExtensionMetadata implements ExtensionMetadata {
    @Override
    public String getId() { return "org.openremote.extension.energy"; }

    @Override
    public String getVersion() { return "1.0.0"; }

    @Override
    public String getDescription() {
        return "A composite suite providing Energy Asset types, optimization logic, and Modbus connectivity.";
    }

    @Override
    public ExtensionType getType() { return ExtensionType.COMPOSITE; }

    @Override
    public Set<String> getDependencies() {
        return Set.of("org.openremote.extension.ems", "org.openremote.extension.modbus");
    }
}
```

### Composite Extension & Dependency Logic

The use of a Metadata SPI allows the Manager to handle Composite Extensions as logical bundles.

- **Dependency Resolution**: On startup, the Manager uses `ServiceLoader` to load all `ExtensionMetadata` instances. It builds a directed graph of the extensions on the classpath.
- **Circular Dependency Protection**: The Manager's dependency resolver checks for circular references (A → B → A). If a cycle is detected, the Manager aborts the boot sequence to prevent recursive initialization loops.
- **Activation Safety**: The Manager will refuse to initialize an extension if its declared dependencies are missing from the classpath or are explicitly disabled.

### The Activation Roadmap

#### Stage 1: Implicit Activation (Current)

All extension components available on the Manager classpath are implicitly active and continue to be discovered through the existing `ServiceLoader` mechanisms for `AssetModelProvider`, `SetupTasks`, and `ContainerService`.

`ExtensionMetadata` is not required for activation in this stage, but extensions may already provide it in preparation for the next stage.

#### Stage 2: Environment-Driven Whitelisting

The `OR_ENABLED_EXTENSIONS` environment variable is introduced and `ExtensionMetadata` becomes mandatory for extensions.

- **Discovery**: The Manager discovers the available extensions through the `ExtensionMetadata` SPI.
- **Activation**: The Manager determines the active extension set from `OR_ENABLED_EXTENSIONS` and automatically includes transitive extension dependencies declared in the metadata.
- **Component Filtering**: `AssetModelProvider`, `SetupTasks`, `ContainerService`, and other extension components belonging to inactive extensions are not initialized or registered. The existing SPI discovery mechanisms therefore need to become extension-aware.
- **Validation**: The Manager validates that required extension dependencies are present and detects invalid or circular dependency configurations before initialization.
- **Persistence**: The list of active extension IDs is persisted for later configuration through the Manager UI.

#### Stage 3: UI-Driven Configuration

An Extensions Page allows users to toggle extensions and shows metadata such as descriptions, versions, and dependencies.

* **Validation**: The UI prevents saving configurations that violate extension dependency rules.
* **Lifecycle**: Enabling or disabling extensions requires a Manager restart.
* **User Feedback**: Changes are shown as pending until the Manager is restarted.
* **Safety Check**: The Manager validates the effective extension configuration at startup, including changes made outside the UI.

### Resource Isolation & Conflict Management

- **Version Pinning**: Gradle remains the arbiter for build-time version conflicts.
- **Namespace Hygiene**: All resources must stay within the `org.openremote.extension.{name}` path to prevent accidental overwriting of Core or peer-extension assets.

## Versioning & Release Management (Monorepo)

Official extensions are initially managed in the `openremote/extensions` monorepo, separate from Core.

This separation keeps optional domain-specific functionality clearly outside Core, enforces a cleaner dependency boundary, and allows Core to be built and tested without unrelated extensions. As the number of extensions grows, this also helps prevent the Core build and test scope from growing with every optional feature.

Initially, Core and official extensions follow the same release train and are versioned in lockstep.
This keeps compatibility management simple while the extension APIs are still evolving, even though the source code is maintained in separate repositories.

The trade-off is that changes spanning Core and extensions require more coordination, particularly for refactoring, debugging, testing, and synchronized releases.

### Repository Structure

The monorepo uses Gradle subprojects to isolate each extension.
A shared `build.gradle` in the root provides common build logic, ensuring all extensions use the same compiler settings and dependency versions.

```text
openremote-extensions/ (Monorepo Root)
├── build.gradle        # Shared build logic
├── settings.gradle     # Defines shared Gradle settings
├── ems/                # Sub-project
├── energy/             # Sub-project
└── modbus/             # Sub-project
```

### Implicit Core Compatibility

In this initial phase, explicit core compatibility functions like `getMinCoreVersion()` are omitted from the `ExtensionMetadata` SPI.

- **The Docker Contract**: Compatibility is managed at the packaging level. Because the extensions and the Manager are bundled into the same Docker image during the CI/CD process, it is guaranteed that the extensions on the classpath have been built and tested against that specific Manager version.
- **Simplified Manifest**: This reduces boilerplate and prevents version mismatch errors during startup in a controlled environment.

### Release Tying

Extensions in the monorepo follow a Release Train model.

- When a new version of the OpenRemote stack is released, all extensions within the monorepo are typically tagged and released under the same version number as the platform (or a synchronized sub-version).
- This ensures that any internal API changes made in the Core are immediately reflected and tested across all extensions before the Docker image is published.

### Internal Dependency Management

The monorepo allows extensions to share internal code without exposing it as public APIs:

- **Shared Modules**: Shared utilities (e.g., energy calculation math) can still reside in a shared extension sub-project.
- **Gradle Linking**: Extensions could include these via `implementation project(':extensions:sharedmodule')`. These can be shaded or bundled into the extension JAR during the build, keeping the artifact self-contained.

### CI/CD Efficiency

The monorepo structure allows for a unified pipeline:

- **Atomic Commits**: A single Pull Request can update a dependency and all affected extensions simultaneously.
- **Unified Testing**: Integration tests can easily span multiple extensions to verify that a Composite Extension (e.g. `energy`) functions correctly with its sub-dependencies (`modbus`, `ems`) before the image is finalized.
