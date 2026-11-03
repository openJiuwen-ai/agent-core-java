# Test Scenarios — Error Path & Boundary Condition Library

This directory archives error paths, boundary conditions, and
concurrency scenarios that must be covered by tests. Claude automatically
references these when writing or modifying tests to ensure no critical
path is missed.

## Directory structure

```
test-scenarios/
  README.md                    ← this file
  001-null-input.md            ← sample: null input
  002-concurrent-access.md     ← sample: concurrent access
  003-object-mapper-reuse.md   ← sample: resource reuse
```

## Scenario file format

```markdown
---
id: 001
title: Short title
module: com.openjiuwen.xxx
priority: P0 | P1 | P2
type: null | boundary | concurrency | exception | resource
tags: [npe, validation, ...]
---

## Description

When this scenario is triggered and what the expected behavior is.

## Input Conditions

- Input values / state / concurrency conditions

## Expected Behavior

- Exception type to throw (`BaseError` + specific `StatusCode`)
- Return value or state

## Test Location

- `src/test/java/...` — corresponding test method names

## Coverage

- [ ] not covered
- [x] covered
```

## Scenario types

| Type | Description | Example |
|---|---|---|
| `null` | null, empty string, empty collection | `invoke(null, session)` |
| `boundary` | boundary values: 0, -1, MAX_VALUE, empty array | `tokenCount = Integer.MAX_VALUE` |
| `concurrency` | concurrent read/write, race conditions | multiple threads writing to `ConcurrentHashMap` |
| `exception` | exception propagation, error code mapping | `BaseError` passthrough vs `RuntimeException` wrapping |
| `resource` | resource leak, IO close, timeout | `HttpClient` connection pool exhaustion |

## Rules

- Scenario IDs increment; never reuse deleted IDs.
- `priority`: P0 = must cover, P1 = should cover, P2 = nice to have.
- Test location must include the full path.
- Coverage uses `[ ]` / `[x]; Claude updates it after writing tests.
- When adding a new feature, Claude checks for uncovered matching
  scenarios and reminds you to add tests.

## Case vs Test-Scenario

| When to use Case (`.claude/cases/`) | When to use Test-Scenario (this directory) |
|---|---|
| An incident or tricky bug that already happened | An error path or boundary condition to cover |
| Has root-cause analysis and a fix | Only needs input and expected behavior |
| Post-hoc archive, looking back | Upfront design, preventing future |
| Template has `severity` / `date` | Template has `priority` / `type` |

Rule of thumb: **Something broke → log a Case; something might break → log a Test-Scenario.**

The two can cross-reference: a Case's `Related Files` links to the
corresponding Test-Scenario, and the Test-Scenario's coverage status is
updated when the Case's fix lands.
