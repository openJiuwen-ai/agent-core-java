# Design and Plan Stage

## Inputs and scope

Start only from a passed R1 specification. Read the existing component baseline, impacted code, interfaces, tests, build configuration, and dependency policies. Update the specification through its own rework stage if design exposes a requirement defect.

## Design artifact

Create `design.md` from `assets/design-template.md`. Cover:

- context and affected modules;
- chosen architecture and at least one meaningful alternative;
- responsibilities, data/control flow, state and failure transitions;
- public/internal interfaces, serialization, persistence, concurrency, and compatibility;
- validation, security, observability, rollout and rollback considerations;
- test design table with stable `CASE-*` IDs, mapped requirement IDs, level, fixture, and expected evidence;
- path-level implementation boundary and permanent exclusions.

The service permanently excludes build-lifecycle control (`pom.xml`, `.mvn/`,
Maven wrappers), CI/release control, deployment paths, trusted Skills, Agent
instructions, Git metadata, and credential material. A feature that truly needs
one of these paths requires a separate human-owned change; do not list it in the
Implementation Boundary.

When public interfaces, dependencies, component state, or component responsibilities change, create `component-design-draft.md`. Do not overwrite long-term component documentation until SHIP promotion.

## Executable plan

Expand `plan.md` only after the design is coherent. Each task must be independently resumable and contain:

- stable `T-*` ID and mapped `CASE-*`/requirement IDs;
- exact writable production and test paths;
- dependencies and one unambiguous readiness condition;
- RED action and one or more exact Java class selectors written as
  `-Dtest=Class[,Class]` (no methods, globs, profiles, goals, or extra flags);
- a compile-safe RED mechanism that reaches JUnit; compilation failure is never
  accepted, so a missing new Java API requires a probe such as reflection;
- GREEN behavior boundary and exact new/regression test classes;
- REFACTOR quality checks;
- completion definition and evidence slots.

The set of CASE IDs across tasks must equal the design test table's in-scope CASE IDs. Do not use “same as above” or conversation memory.

Keep only source-behavior TDD tasks under `## Tasks`, with all such tasks before
any deferred entry. Record documentation promotion/release notes under
`Deferred Delivery` for SHIP. Record post-merge system-test requirements there
as well; the separate `SYSTEM_TEST` stage owns their code and evidence. Never
invent a RED result for documentation or another artifact-only change.

## R2 author check

Verify feasibility against actual code and build structure, not names guessed from the Issue. Validate failure behavior, upgrade/rollback, security boundaries, and testability. Update `traceability.md` with design sections and CASE IDs. The next action is independent `REVIEW_R2`.
