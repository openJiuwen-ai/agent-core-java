# Java Translation Fix Report

Date: 2026-03-07

## Scope

This round fixed the issues listed in `docs/needtofix.md` for the already implemented Java modules by comparing behavior with the Python implementation.

Fix strategy:

- Do not keep placeholder behavior.
- If a dependency capability was required by the fixed feature, implement the needed real behavior first.
- Validate with a clean build instead of relying on incremental compilation artifacts.

## Fixed Items

### 1. `graph`

- `CompiledGraph`
  - Fixed `PregelConfig` construction to match the Java `PregelConfig` API.
  - Aligned main-run checkpoint handling with Python semantics.
  - Allowed graph invocation to handle both normal `Map<String, Object>` inputs and `InteractiveInput`.
  - Avoided committing non-map interactive payloads into workflow state.

- `stream_actor.StreamProcessor`
  - Updated the implementation to the current Java `DictUtils.extractLeafNodes(...)` and `DictUtils.rebuildDict(...)` API.
  - Restored schema-shaped stream input reconstruction instead of using stale `Object[]` assumptions.
  - Fixed nested-path extraction so non-map stream messages are handled safely.

- `Vertex`
  - Restored Python-equivalent `pre_stream` semantics: stream-in abilities now receive schema-shaped stream input objects instead of an incorrect iterator type.
  - Fixed `COLLECT`/`TRANSFORM` execution to pass the correct stream input shape into `onCollect(...)` and `onTransform(...)`.
  - Replaced invalid `send(...)` calls on sub-workflow queues with real blocking queue delivery.
  - Added `GraphInterrupt` unwrap logic for the existing Java runtime wrapper path used by `WorkflowInteraction`.

### 2. `session`

- `checkpointer`
  - Replaced the placeholder `graphStore()` return type with the real `Store` abstraction.
  - Replaced the placeholder object in `InMemoryCheckpointer` with a real `InMemoryStore`.

- `interaction`
  - `AgentInteraction.waitUserInputs(...)` now actually calls `checkpointer.interruptAgentExecute(...)` before throwing `AgentInterrupt`.
  - `SimpleAgentInteraction.waitUserInputs(...)` now does the same instead of leaving a placeholder branch.

- `stream`
  - Added a blocking iterator view in `StreamWriterManager`, matching the Python `stream_output()` incremental consumption model.
  - `AgentSessionApi.streamIterator()` now returns an iterator instead of eagerly collecting all output into a list.

### 3. `sys_operation`

- `SysOperationToolAdapter`
  - Removed the placeholder string-return implementation.
  - Added real reflection-based method delegation to the underlying operation instance.
  - Added argument conversion for primitive values, arrays, lists, maps, and iterables.

- `BaseOperation`
  - Added basic tool input schema generation for `listTools()`, so extracted tools now carry usable parameter metadata instead of empty schemas.

- Build support
  - Enabled Java compiler `-parameters` metadata in Maven so reflected tool method parameter names are available at runtime.

### 4. `foundation.tool.service_api`

- `RestfulApi`
  - Fixed GET request URL assembly so path params, default query params, and GET body params are merged correctly.
  - Added `reason` to formatted responses to align with Python output shape.
  - Returned human-readable HTTP reason phrases for non-2xx responses.
  - Preserved `raise_for_status=false` behavior so error responses can be returned instead of always throwing.

- `common.security.UrlUtils`
  - Updated URL validation to accept Python-style path placeholders such as `/users/{id}`.
  - Added JVM system-property fallback for `SSRF_PROTECT_ENABLED`, which makes local deterministic tests possible without changing production env-var behavior.

## Test Repairs And Additions

- Fixed the wrong Java expectation in `LocalFunctionTest`: Python requires generator-style streaming behavior, so non-streaming functions should fail in `stream(...)`.
- Replaced the invalid `RestfulApi` URL-building test with a local HTTP server test that verifies:
  - path substitution,
  - query merging,
  - header propagation,
  - `reason` / `message` handling.
- Fixed `StreamOutputTest.testWriterAfterEmitterClosed()` so it performs a real write instead of empty-lambda fake coverage.
- Added stream iterator regression coverage in:
  - `StreamOutputTest`
  - `AgentSessionApiTest`
- Added `SysOperationToolAdapterTest` to verify:
  - extracted tools call the real underlying operation,
  - parameter-name mapping works on clean compilation,
  - generated tool schema is non-empty.

## Verification

Full clean verification passed:

```bash
mvn -q clean test
```

Result:

- Main source compilation passed.
- Updated tests passed.
- Full project unit test suite passed on a clean build.

## Notes

- `docs/needtofix.md` was kept as the working checklist for this repair round.
- No placeholder implementation was introduced during the fixes.
- Unrelated existing dirty worktree changes were not reverted.
