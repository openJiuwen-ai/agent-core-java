---
description: Test location, style, mocking, coverage, and running conventions for agent-core-java.
language: english
paths:
  - "src/test/java/**/*.java"
alwaysApply: false
---

# Testing Rules

## Test Location

- Prefer targeted unit tests that mirror the source path.
  Example: `src/main/java/com/openjiuwen/harness/DeepAgent.java` → `src/test/java/com/openjiuwen/harness/DeepAgentTest.java`
- `src/test/java/`: all tests. Unit tests are the default.
- `src/test/java/com/openjiuwen/core/systemtest/`: system/E2E tests — excluded from `mvn test` by surefire `<excludedGroups>`.

## Choosing Test Patterns

| Pattern | When to Use | Characteristics |
|---|---|---|
| **Unit test** | Isolated logic, no I/O, fast feedback | Mock all dependencies; `*Test.java` |
| **Compatibility test** | Java-Python API parity contract | Enforces cross-language consistency; `*CompatibilityTest.java` |
| **System/E2E test** | Full workflow, end-to-end correctness | Requires credentials/Docker; `*SystemTest.java`; `@Tag("system-test")` |

**Decision rules**:
- If it touches the filesystem, network, or external service → integration or system test
- If it tests a single class in isolation → unit test
- If the harness subsystem changes → ensure both unit and compatibility coverage
- Coverage gate: 80% minimum for `com.openjiuwen.core` and `com.openjiuwen.harness`

## Test Framework and Style

- **JUnit 5** (Jupiter 5.10+) + **Mockito** (5.2+) + **AssertJ** (3.25+).
- Test class naming: `*Test.java`, `*CompatibilityTest.java`, `*SystemTest.java`.
- Test method naming: `methodName_scenario_expected` (e.g., `executeCmd_whenAllowlistMismatch_throwsValidationError`).
- Use `@Nested` for grouping related test cases within a class.
- Use `@TempDir` for file-based tests (the project uses this heavily — 80+ occurrences).
- Use `@DisplayName` for complex test scenarios where the method name is not sufficient.
- Structure tests as **Given / When / Then** (per `X.TST.06`).

## Credentials and Mocks

- Use mock defaults for credentials in tests. Never hard-code real API keys in test files.
- For env-var-based config, use `System.getenv(...)` behind an injectable wrapper, then mock the wrapper in tests.
- Mark tests that require real credentials with `@Disabled("requires real credentials")` and move them to system tests.

## Mocking Conventions

The project uses **programmatic Mockito** — no `@Mock`, `@InjectMocks`, or `@ExtendWith(MockitoExtension.class)`.

```java
// Preferred pattern: inline mock creation
Model model = mock(Model.class);
when(model.chat(any())).thenReturn(response);
verify(model).chat(any());

// For static methods (use sparingly)
try (var mocked = mockStatic(AutoHarnessFactory.class)) {
    mocked.when(() -> AutoHarnessFactory.createAssessAgent(any())).thenReturn(agent);
    // ...
}
```

**Rules**:
- Do not introduce `@Mock` / `@InjectMocks` annotations — keep the existing inline style.
- `mockStatic()` is allowed in auto-harness and integration tests; avoid in unit tests.
- Prefer injecting dependencies via constructor over using `mockStatic()` or reflection.

## Test Tags and CI

| Tag | Annotation | Run by `mvn test`? | Purpose |
|---|---|---|---|
| (none) | — | Yes | Unit tests |
| `system-test` | `@Tag("system-test")` | **No** (excluded) | E2E with real infra |
| `agent-teams-*-slice` | `@Tag("agent-teams-config-slice")` etc. | Yes (not excluded) | Slice-specific compatibility tests |

- Default surefire config: `<excludedGroups>system-test</excludedGroups>`.
- To run system tests: `mvn test -Dsurefire.groups=system-test`.
- To run a specific slice: `mvn test -Dsurefire.groups=agent-teams-config-slice`.
- To run a single test class: `mvn test -Dtest=ClassName` or `-Dtest=ClassName#methodName`.

## Reactive and Async Tests

- For `Flux`/`Mono` testing, use `reactor-test` (`StepVerifier`).
- For `CompletableFuture` testing, use `CompletableFuture.join()` with timeout or JUnit 5 `assertTimeout`.
- Do not use `Thread.sleep()` in tests — use `awaitility` or `CompletableFuture.get(timeout)`.

## Assertions

- Use **AssertJ** for fluent assertions: `assertThat(actual).isEqualTo(expected)`.
- Use descriptive messages for non-obvious conditions: `assertThat(result).as("should return empty list when no items found").isEmpty()`.
- For exception assertions, prefer AssertJ's `assertThatThrownBy()`:
  ```java
  assertThatThrownBy(() -> errorHelper.raiseError(StatusCode.INVALID_PARAM))
      .isInstanceOf(ValidationError.class)
      .hasMessageContaining("invalid");
  ```

## Coverage

- JaCoCo 0.8.11 is configured. Run: `mvn test jacoco:report`.
- Report location: `target/site/jacoco/index.html`.
- 80% line coverage minimum for `core` and `harness` modules.
- No coverage threshold is enforced by the build yet — rely on review discipline.

## Compatibility Tests

`*CompatibilityTest.java` files enforce Java-Python API parity. Treat them as
contract tests:
- When a Python API changes, the corresponding Java compatibility test must be updated.
- When adding a new Java public API, add a compatibility test if a Python equivalent exists.
- Compatibility tests verify class existence, method signatures, and behavioral parity.

## Running Tests

| Command | What it does |
|---|---|
| `mvn test` | Run all unit + compatibility tests (excludes system-test) |
| `mvn test -Dtest=ClassName` | Run a single test class |
| `mvn test -Dtest=ClassName#methodName` | Run a single test method |
| `mvn test -Dsurefire.groups=system-test` | Run system/E2E tests only |
| `mvn test jacoco:report` | Run tests + generate coverage report |
| `mvn compile -DskipTests` | Compile without running tests |