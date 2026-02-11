# Repository Guidelines

## Project Structure & Module Organization
MeshQL is a multi-module Maven build. Core modules live at the repo root:
`core/`, `api/` (with `api/graphlette/` and `api/restlette/`), `auth/`, `repos/`, `server/`, and `cert/`.
Examples are under `examples/farm/` and `examples/events/`. Diagrams are in `diagrams/`.
Most Java code follows the standard layout: `src/main/java/` and tests in `src/test/java/`
with resources in `src/test/resources/`.

## Build, Test, and Development Commands
- `mvn clean install` builds all modules and runs tests.
- `mvn test` runs the full test suite.
- `mvn test -Dtest=ClassName` runs a single test class.
- `mvn test -Dtest=ClassName#methodName` runs a single test method.
If working on a single module, run Maven from that module directory (for example, `server/`).

## Coding Style & Naming Conventions
- Java 21 (see `pom.xml`), 4-space indentation, braces on the same line.
- Classes use PascalCase, methods and fields use camelCase.
- Favor interface-based design, composition over inheritance, and immutable records when possible.
- Logging uses SLF4J; return clear error messages and appropriate HTTP status codes.
No automatic formatter is configured, so keep diffs tight and consistent.

## Testing Guidelines
- Unit and integration tests use JUnit 5; BDD coverage uses Cucumber.
- Testcontainers is available for database and Kafka integration tests.
- Name tests after the class under test (for example, `RepositoryTest`).
- Run tests with `mvn test`; generate coverage with `mvn verify` (JaCoCo).

## Commit & Pull Request Guidelines
Recent history uses short, imperative commit messages (for example, "Upgrade Testcontainers...").
Keep commits scoped and descriptive, and reference issues when applicable.
Pull requests should include a clear description, list any user-facing changes, and note test
commands run (or why tests were skipped). Add diagrams or README updates when APIs or
architecture change.

## Security & Configuration Notes
JWT auth is decode-only and expects upstream verification; do not add signature checks unless
the design is updated. Keep credentials and secrets out of source control.
