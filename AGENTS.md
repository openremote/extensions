# OpenRemote Extensions Agent Instructions

This repository hosts official extensions that add optional functionality to OpenRemote Manager. Extensions build against published Core artifacts and use the extension points provided by Core.

## Project references

- Start with the [README.md](README.md) for the repository layout, development workflows, and how to use extensions.
- Treat [DESIGN.md](DESIGN.md) as the target architecture and rollout plan. Some features are still planned. Verify APIs, configuration, and lifecycle behavior in the implementation before using them.
- Verify Core behavior against the version declared in the [Gradle version catalog](gradle/libs.versions.toml). Keep domain behavior in the owning extension and identify any shared API or runtime changes needed in Core. Consult the [Core agent instructions](https://github.com/openremote/core/blob/main/AGENTS.md) for shared conventions.

## Development and validation

Use JDK 21 and the Gradle wrapper. Run `./gradlew spotlessCheck` from the repository root to check formatting. Write code, comments, and documentation in US English.

Put Groovy Spock specs in the owning extension under `src/test/groovy`. Use fixtures or mocks for third-party services.

Tests that use Core's `ManagerContainerTrait` need PostgreSQL and Keycloak. Start them from the extensions repo using a compatible sibling Core checkout.

```bash
docker compose -f ../core/profile/dev-testing.yml up -d --no-build
```

Adjust the path if Core is elsewhere. Keep full Core checkouts outside `extensions/` so Gradle does not discover Core's build files. CI uses a sparse checkout of Core's `profile/`. [CI workflow](.github/workflows/ci_cd.yml)

## Database migrations

Core currently discovers extension migrations in `src/main/resources/org/openremote/manager/setup/database/` within each extension. Use this location for changes to the current implementation.

Name new Flyway SQL migrations `VYYYYMMDD_HHmm__Description.sql` using UTC. Check for version collisions with Core and other extensions. Make follow-up changes in a new migration if the original may already have run.
