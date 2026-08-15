# Post-merge System Test

## Boundary

Start only after the controller observes the feature PR merged and supplies the immutable merged source revision. Treat the feature source as read-only. Write only the configured test repository paths, normally `src/test/java/`, task-owned `src/test/resources/`, and the work item's system-test artifact directory.

Create system/end-to-end coverage, not unit tests. A valid scenario starts from a supported public input, file, event, or API, crosses meaningful component boundaries, and asserts a final externally observable state. Reject isolated method returns, getters/models/configuration, construction-only checks, and single-component behavior.

## Select scenarios

1. Derive candidate flows from the Issue acceptance scenarios, merged implementation, design CASE IDs, and existing test-repository conventions.
2. Search existing system tests and SDK unit tests before adding coverage. Keep one representative scenario per capability; prefer the longer observable chain and remove subset duplicates.
3. Classify the entry API as standalone, owner-bound, or runtime-only by inspecting its constructors, fields, and initialization path.
4. Use a lower same-source engine only when it preserves the complete behavior chain. Never reduce the test to unit scope merely because the public API is hard to initialize.
5. Preserve the exact acceptance predicate. Do not replace a precise expected value, state transition, or failure with a wider assertion that can pass incorrect behavior.

## Author the test

Create or update `system-test.md` from `assets/system-test-template.md`. Keep one compact scenario table, API-testability decision, changed paths, and controller evidence. Add focused Java tests using the repository's established packages, fixtures, naming, and tags.

The Controller Evidence table must contain exactly one profile row named
`Configured smoke + new system tests`. When resuming an artifact created by an
older workflow, remove any `Compile (no tests executed)` row; that profile is
retired and fails the current artifact contract.

Automated tests must be deterministic and isolated:

- prefer explicit state probes, latches, clocks, or deterministic seams over sleeps;
- do not require external network, credentials, privileged runtime, or mutable shared services;
- do not add `@Disabled`, JUnit assumptions, `assumeTrue`, or `assumeFalse`;
- do not weaken assertions, swallow failures, or broaden timeouts to obtain a pass;
- keep fixtures task-owned and free of secrets.

The Controller runs exactly one selector union: a small operator-configured
smoke set plus every new Java test class derived from this task's changed paths.
There is no separate COMPILE baseline and no complete test-repository suite.
The Controller constrains Maven test compilation to the same exact selector
union with its own immutable POM overlay; unrelated repository test sources are
outside the Gate. Dependency prefetch resolves declared artifacts only and
must not compile the complete test tree or execute an online probe test;
dynamically selected Surefire provider artifacts are Controller concerns.
Review and publish reuse the passing receipt while the test-code fingerprint is
unchanged. The model cannot add Maven arguments, widen the smoke set, select a
tag, or remove either selector group.
Record only returned commands, revisions, outcomes, and evidence handles. A
model statement or skipped test is not evidence.

## SDK gaps and failure routing

First distinguish a test defect, unsupported initialization choice, dependency-cache miss, environment failure, and genuine SDK defect. Fix test defects inside the assigned scope. Return `NEEDS_CONTEXT` for a precise controller-providable input. Return `BLOCKED` with the smallest reproducible chain when the required public behavior cannot be exercised without an SDK change, network, credentials, or forbidden path.

Never hide a genuine SDK gap by disabling the test, substituting a weaker
assertion, or testing only an internal helper. The Controller records a genuine
SDK or non-isolatable environment gap as the auditable `BLOCKED_EXTERNAL`
terminal state; it does not publish a system-test PR or create a routine
approval gate.

## Completion

Call `runApprovedGate` with `{}` and return `DONE` only when:

- every added scenario is mapped to an acceptance/requirement ID and is non-duplicative;
- changed files remain in the system-test writable scope;
- the final exact assertions are present and no skip mechanism was added;
- configured-smoke-plus-new-test selector evidence is green on the final test tree;
- `system-test.md` contains the merged feature revision and evidence anchors.

The next action is independent `REVIEW_SYSTEM_TEST`. The author cannot approve or publish its own test.
