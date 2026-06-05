# OpenRemote Extensions

[![CI/CD](https://github.com/openremote/extensions/actions/workflows/ci_cd.yml/badge.svg?branch=main&event=push)](https://github.com/openremote/extensions/actions/workflows/ci_cd.yml?query=event%3Apush+branch%3Amain)
[![Open Source? Yes!](https://badgen.net/badge/Open%20Source%20%3F/Yes%21/blue?icon=github)](https://github.com/Naereen/badges/)

This repository contains official OpenRemote extensions.

Extensions provide optional, domain-specific functionality for the OpenRemote platform, such as protocol agents, asset model definitions, setup tasks, container services, rules, and UI-related resources.
They are kept separate from the OpenRemote Core repository so functionality can be developed, tested, and packaged in a modular way.

## Repository structure

This is a Gradle monorepo.
Each extension lives in its own subproject directory, for example:

```text
extensions/
├── ems/
├── entsoe/
├── build.gradle
└── settings.gradle
```

Subprojects are included automatically when they contain a `build.gradle` file, unless the directory contains a `.buildignore` file.

## Available extensions

| Extension | Description                                                                               |
| --------- | ----------------------------------------------------------------------------------------- |
| `ems`     | A new extension-based implementation of the Energy Management System with GOPACS support. |
| `entsoe`  | Agent for retrieving ENTSO-E energy price data and storing it as predicted datapoints.    |

## Building

From the repository root:

```bash
./gradlew build
```

To build a single extension:

```bash
./gradlew :entsoe:build
```

Replace `entsoe` with the name of the extension subproject you want to build.

## Versioning and publishing

The repository uses Gradle-based release/version management.
Extension artifacts are published under the `io.openremote.extension` group.

Official extensions are intended to be built and released in sync with compatible OpenRemote platform versions.

## Using extensions

Currently, extensions are used by adding them as dependencies to a custom project.

For example, the ENTSO-E extension can be added to the [dependencies block](https://github.com/openremote/custom-project/blob/d48cd93a21873e62df7fe5f79dce0624f3cfc972/manager/build.gradle#L3-L8) of the manager project in your custom project:

```groovy
dependencies {
    api "io.openremote.extension:openremote-entsoe-extension:0.5.1"
}
```

After the custom project is rebuilt and the OpenRemote Manager is restarted, the extension is available as part of that deployment.

## Developing an extension

When adding or updating an extension:

* Keep extension code in its own Gradle subproject.
* Use the package namespace `org.openremote.extension.<extension-name>`.
* Register extension components using the appropriate OpenRemote SPI files under `META-INF/services`.
* Keep extension-specific resources under an extension-specific resource path.
* Add tests for extension behaviour, preferably following the existing OpenRemote testing conventions.

Typical extension integration points include:

* `AssetModelProvider`
* `ContainerService`
* `SetupTasks`
