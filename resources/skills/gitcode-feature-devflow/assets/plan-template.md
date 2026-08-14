# Feature Execution Plan

## Resume Header

| Field | Value |
| --- | --- |
| Job / Issue | |
| Component root | |
| Artifact root | |
| Mode | `automatic independent reviews` |
| Current revision | |
| Single next action | |

## Gate Table

| Gate | Author artifact/version | Review record | Verdict | Receipt | Round |
| --- | --- | --- | --- | --- | --- |
| R1 | | | pending | pending | 0 |
| R2 | | | pending | pending | 0 |
| R3 | | | pending | pending | 0 |
| System Test | | | pending | N/A | 0 |

## Scope and Permanent Exclusions

## Tasks

List only source-behavior TDD tasks here. Put documentation promotion,
release notes, and post-merge system tests under `Deferred Delivery`; they must
not enter the source RED/GREEN/REFACTOR loop. Every TDD task must carry one or
more exact Java class selectors in `-Dtest=Class[,Class]` form. Do not use
method selectors, globs, Maven flags, profiles, or goals as selector values.
RED test sources must compile and reach a JUnit failure/error; compilation
failure is never valid RED. For a missing new API, plan a compile-safe probe
such as reflection.

### T-001 — <name>

- Status: `pending`
- Status contract: keep exactly one controller-owned `Status` line per task; preserve its supplied value, and put all qualifiers under `Depends on` or `Readiness condition`
- Depends on:
- Requirements / cases:
- Writable paths:
- RED: [ ] `mvn test -Dtest=<exact-new-test-class>`
- GREEN: [ ] `mvn test -Dtest=<exact-new-test-class>,<exact-regression-class>`
- REFACTOR: [ ] quality checks; optionally repeat exact `-Dtest=` classes
- Completion definition:
- RED evidence:
- GREEN evidence:
- REFACTOR evidence:
- Commit anchor:

## Deferred Delivery

- SHIP documentation/promotion paths:
- Post-merge system-test requirements/cases:

## Review Rework Queue

| Finding | Owner stage / task | Required outcome | Evidence | Resolution status |
| --- | --- | --- | --- | --- |

## Dependency and Debt Register

## Recovery Instructions

Reconcile the gate table with persisted controller state, then continue only the `Single next action`. If no unique action exists, stop as blocked.
