# TDD and Quality Stage

## Select one task

Use `plan.md` to select the first uniquely ready non-done task. During
`IMPLEMENT_REWORK`, use only the latest R3 critical/important findings and the
completed task named by Controller evidence. If selection is ambiguous, return
`BLOCKED`. Confirm every target path is in the controller's writable set and
outside the permanent denylist.

## RED

The controller first runs a fixed sandbox-compatible baseline probe to verify
the offline JUnit runtime and incoming build boundary. This probe is not the
repository's full suite. Add or strengthen the smallest test that expresses the
assigned case. Do not alter production behavior. The controller then runs only
the exact RED test classes frozen in the R2-approved plan; neither the Issue nor
the model supplies Maven arguments.

Each RED invocation starts from the R2-approved stage snapshot. A normal test,
artifact, or protocol failure is returned as structured Controller Repair
Feedback in the same conversation, and in-scope changes remain available for
repair. A path-policy violation makes the Controller restore the complete owned
Worktree snapshot and end the stage as `FAILED_POLICY`.

A valid RED result:

- exits nonzero for the expected missing/wrong behavior;
- reaches the relevant assertion or failure point;
- is not a compile typo, missing dependency, network failure, timeout, or broken environment.

The selected test sources must compile. Never plan or claim Java compilation
failure as RED. If a new public symbol does not exist yet, use a compile-safe
behavioral probe such as reflection: fail through JUnit when the symbol is
absent, then invoke it and assert the required behavior once GREEN adds it.

Record only the actual command, key failure, timestamp/revision, and evidence handle returned by the controller. If the test passes immediately, explain whether behavior already exists or the test is weak; do not fabricate RED.

## GREEN

Make the smallest production change that satisfies the test and mapped
requirements. The controller runs the union of the R2-approved RED and GREEN
test classes. They must pass on the current tree before GREEN is recorded.

Never weaken assertions, ignore failures, remove tests, widen timeouts, or substitute mocks solely to turn the gate green.

## REFACTOR

With all approved tests green, review touched code for:

- simplicity and duplication;
- reliability and explicit failure behavior;
- maintainability and naming;
- testability and deterministic seams;
- performance proportional to expected scale;
- strict feature scope and compatibility.

Run the controller-approved exact test-class union again after any
behavior-preserving change. If no refactor is warranted, record `REFACTOR: N/A`
with the completed quality check; omission is not allowed.

## R3 REWORK

Treat an R3 finding as a bounded repair of the most recently completed task,
not as a new RED/GREEN/REFACTOR task. Preserve its `done` status and exact
selector contract. Do not add a replacement plan task merely to make a
selector discoverable. Fix the reviewed code, tests, or evidence within the
Controller-approved paths, update the rework queue and resolution evidence,
and run the Controller-bound TARGETED Gate. A passing repair is published and
sent to a new independent R3 review round.

## Evidence and completion

Update the task checkboxes, evidence lines, and traceability code/test columns.
Evidence must come from controller-returned container output and a
controller-returned commit anchor. The model cannot run or choose arbitrary
commands, alter the approved selector contract, or claim repository-wide test
coverage from a targeted result.

Preserve the task `Status` token supplied by the controller. The controller,
not the model, records `red` after trustworthy RED, `green` after the approved
GREEN selectors pass, `refactor` while the quality pass is active, and `done`
only after the final exact selector gate passes.

The service repeats all approved source test classes at SHIP. Repository-wide
`mvn verify` remains mandatory in target CI or the human merge gate, outside the
hardened runtime container. Treat that external result as separate evidence;
never state that the service ran or passed it.

Return `DONE` only for this one task or rework item. Return `BLOCKED` with a
stable `failure.code`, precise `requestedInputs`, and bounded evidence only when:

- a test requires network, credentials, privileged runtime, or an unapproved dependency;
- a declared dependency remains unavailable after Controller-managed prefetch;
- the implementation needs paths outside the granted scope;
- an upstream specification/design defect is discovered;
- actual RED/GREEN/REFACTOR evidence is missing.

An ordinary offline dependency-cache miss routes automatically to the
Controller's credential-free `DEPENDENCY_PREFETCH` stage. The resumed approved
Gate still runs with `network=none`; never enable network in a stage Agent or
Gate as a workaround.
