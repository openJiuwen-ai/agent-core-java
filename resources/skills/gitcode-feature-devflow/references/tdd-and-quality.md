# TDD and Quality Stage

## Select one task

Use `plan.md` to select the first uniquely ready non-done task, or one explicit R3 rework item. If selection is ambiguous, return `BLOCKED`. Confirm every target path is in the controller's writable set and outside the permanent denylist.

## RED

The controller first runs its fixed full profile to prove the incoming task tree is green. Add or strengthen the smallest test that expresses the assigned case. Do not alter production behavior. The controller then runs its fixed RED unit-test profile in the credential-free container; neither the Issue nor the model selects a command.

A valid RED result:

- exits nonzero for the expected missing/wrong behavior;
- reaches the relevant assertion or failure point;
- is not a compile typo, missing dependency, network failure, timeout, or broken environment.

Record only the actual command, key failure, timestamp/revision, and evidence handle returned by the controller. If the test passes immediately, explain whether behavior already exists or the test is weak; do not fabricate RED.

## GREEN

Make the smallest production change that satisfies the test and mapped requirements. The controller runs its fixed full verification profile. It must pass on the current tree before GREEN is recorded.

Never weaken assertions, ignore failures, remove tests, widen timeouts, or substitute mocks solely to turn the gate green.

## REFACTOR

With all approved tests green, review touched code for:

- simplicity and duplication;
- reliability and explicit failure behavior;
- maintainability and naming;
- testability and deterministic seams;
- performance proportional to expected scale;
- strict feature scope and compatibility.

Run the controller's fixed verification again after any behavior-preserving change. If no refactor is warranted, record `REFACTOR: N/A` with the completed quality check; omission is not allowed.

## Evidence and completion

Update the task checkboxes, evidence lines, and traceability code/test columns. Evidence must come from controller-returned container output and a controller-returned commit anchor. The model cannot run or choose arbitrary commands.

Return `DONE` only for this one task or rework item. Return `BLOCKED` when:

- a test requires network, credentials, privileged runtime, or an unapproved dependency;
- dependency resolution is unavailable in the isolated cache;
- the implementation needs paths outside the granted scope;
- an upstream specification/design defect is discovered;
- actual RED/GREEN/REFACTOR evidence is missing.

Dependency cache misses route to the service's human-approved prefetch state. Never enable container networking as a workaround.
