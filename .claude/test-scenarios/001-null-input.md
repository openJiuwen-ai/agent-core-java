---
id: 001
title: Public methods must throw BaseError on null input, not NullPointerException
module: com.openjiuwen.core.singleagent
priority: P0
type: null
tags: [npe, validation, error-helper, base-error]
---

## Description

The project uses `ErrorHelper.buildError(StatusCode.X, ...)` to raise
unified `BaseError` subclasses. All public methods that accept
parameters must throw a `BaseError` with a `StatusCode` when receiving
`null` input, rather than letting the JVM throw a bare
`NullPointerException`. This ensures error codes are traceable and
messages are formatted.

Scope: all public methods with parameters under `com.openjiuwen.core`
and `com.openjiuwen.harness`.

Exception: internal private methods may use `Objects.requireNonNull`
for assertions.

## Input Conditions

- `invoke(null, session)` — input is null
- `invoke("hello", null)` — session is null
- `invoke("", session)` — input is empty string
- `invoke("   ", session)` — input is whitespace only

## Expected Behavior

- Throws a `BaseError` subclass (not `NullPointerException`)
- Error code is the `StatusCode` matching the method's module
  (e.g., `AGENT_CONTROLLER_RUNTIME_ERROR` for ControllerAgent,
  `TOOL_EXECUTION_ERROR` for tools)
- Message includes the parameter name and "is required" or "cannot be null/blank"

## Test Location

- `src/test/java/com/openjiuwen/core/singleagent/ControllerAgentTest.java`
  - `testInvokeNullInputThrows` — input is null
  - `testInvokeNullSessionThrows` — session is null
  - `testInvokeBlankInputThrows` — input is empty/whitespace

- `src/test/java/com/openjiuwen/harness/tools/BashToolTest.java`
  - `testInvokeNullCommandReturnsFailedToolOutput` — Tool does not throw; returns `success=false`

## Coverage

- [x] ControllerAgent partially covered (`ControllerAgentTest.Invoke` nested class)
- [ ] BashTool null path not covered
