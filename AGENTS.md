# Contributing with Coding Agents

## Project

Agent Core Java is the Java implementation of the openJiuwen agent framework.
The root Maven reactor contains three modules:

- `core/`: agent runtime, workflows, tools, sessions, and framework integrations.
- `retrieval/`: retrieval components and integrations.
- `memory/`: memory components and storage integrations.

Each module keeps Java sources in `src/main/java`, resources in
`src/main/resources`, and tests in `src/test/java`. Usage examples are in
`examples/`; user documentation is in `documents/`.

## Build and Test

The project targets Java 17. Build settings and dependency versions are defined
in the root and module `pom.xml` files. Run commands from the repository root:

- Compile: `mvn compile`
- Run default tests: `mvn test`
- Build and verify all modules: `mvn clean verify`
- Install without running tests: `mvn clean install -DskipTests`
- Run a specific core test: `mvn -pl core test -Dtest=ClassName`

Use `-pl retrieval` or `-pl memory` for tests in those modules; install required
sibling dependencies first when testing a module in isolation.

Default tests exclude `system-test`; core also excludes `perftest`. Run tests
requiring external services only when relevant and after configuring their
prerequisites. Read each module's POM and the affected tests for configuration.

## Working on Changes

- Follow the requested scope. Inspect the affected implementation, callers,
  and tests before editing; follow nearby code style.
- Preserve public API compatibility unless the task calls for a behavior change.
  Update examples or documentation when public behavior changes.
- Preserve unrelated working-tree changes. Keep developer-specific paths,
  credentials, tool settings, and personal workflows out of shared guidance.
- Release resources and isolate test state. In particular, `Runner.resourceMgr()`
  is shared across tests running in the same process.
- Verify affected behavior with relevant tests, adding regression coverage for
  defects where needed. Use `git diff --check` to check patch whitespace.
- Report what changed, checks performed, results, and remaining limitations.
  Distinguish unavailable checks from passing checks.
