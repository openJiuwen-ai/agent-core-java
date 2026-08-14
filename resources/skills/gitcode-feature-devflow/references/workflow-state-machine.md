# Workflow State Machine

## Happy path

```text
ADMITTED
  -> SPECIFY -> REVIEW_R1 -> DRAFT_PR
  -> DESIGN -> REVIEW_R2
  -> IMPLEMENT_RED -> IMPLEMENT_GREEN -> IMPLEMENT_REFACTOR
  -> (controller publication checkpoint) -> (next task or REVIEW_R3)
  -> SHIP -> READY_FOR_REVIEW -> FEATURE_PR_MERGED
  -> SYSTEM_TEST -> REVIEW_SYSTEM_TEST -> PUBLISH_SYSTEM_TEST
  -> SYSTEM_TEST_READY_FOR_REVIEW -> MERGED
```

The service creates or reconciles one Draft feature PR after R1 author artifacts
exist. R1/R2/R3 remain independent Agent reviews but advance automatically.
`READY_FOR_REVIEW` and `SYSTEM_TEST_READY_FOR_REVIEW` are the only normal human
waits. Both merge events are observed from GitCode and are never set by the model.

## State invariants

- One repository and Issue IID have one lifetime admission and one active feature job.
- One job has one feature branch/artifact root and, after feature merge, one system-test branch/artifact root.
- The controller freezes one exact merged target-base source revision before system-test authoring; retries and reviews use that same revision.
- One job has at most one canonical feature PR and at most one canonical system-test PR; their bindings are persisted separately.
- Only the controller advances state after validating stage output and optimistic-lock version.
- A reviewer never shares the author invocation and never edits reviewed material.
- `plan.md` and the database stage must agree before work resumes.
- `runApprovedGate` has no model-controlled arguments and the Controller always
  force-runs it after the final Agent response.
- Deterministic Gate receipts are reused only for the same real input fingerprint.
- A retry repeats the same bounded operation; it does not skip a gate or create a second PR.

## Rework

A `REWORK` verdict routes back to the author stage that owns the finding:

- R1 -> `SPECIFY`;
- R2 -> `DESIGN`, or `SPECIFY` only when the specification is proven wrong;
- R3 -> `IMPLEMENT`, or an upstream stage only when the finding proves an upstream artifact wrong.
- System-test review -> `SYSTEM_TEST`; a proven SDK gap or non-isolatable
  environment need -> `BLOCKED_EXTERNAL`.

Record every finding in the review file and `plan.md` rework queue. The author
fills the review record's Resolution field using actual Controller evidence.
Re-review is always independent. Controller repair budgets end in
`FAILED_AUTOMATION`; they never create an approval wait.

## Failure states

- `RETRY_SCHEDULED`: classified model, GitCode, network, or container transient;
- `DEPENDENCY_PREFETCH`: isolated automatic dependency-cache refresh;
- `BLOCKED_EXTERNAL`: product decision, genuine SDK gap, or non-isolatable environment;
- `FAILED_AUTOMATION`: primary and diagnostic repair budgets exhausted;
- `FAILED_CONFIGURATION`, `FAILED_POLICY`, `FAILED_INTERNAL`: authoritative
  terminal classifications that must not be blindly replayed.

## Pause, resume, and cancellation

Pause prevents new stage leases after the current safe unit finishes. Resume revalidates the worktree, PR, gate table, and next task before leasing work.

Cancellation is cooperative:

1. set `CANCEL_REQUESTED`;
2. stop before another model call, container command, commit, or publish action;
3. allow a bounded in-flight process to terminate through the controller;
4. persist `CANCELLED` and retain artifacts, branch, PR, and audit events.

Never erase evidence as part of pause or cancellation.

## External terminal states

- An open Draft or ready PR remains nonterminal.
- A merged feature PR transitions to `SYSTEM_TEST` when post-merge testing is enabled; otherwise it transitions to `MERGED`.
- A merged system-test PR transitions to `MERGED` and completes the job.
- A closed, unmerged feature or system-test PR transitions to terminal `CLOSED`;
  this service version does not reopen it or process review-return commands.
- Unknown PR states do not advance the workflow.
