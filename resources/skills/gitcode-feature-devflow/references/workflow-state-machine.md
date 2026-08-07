# Workflow State Machine

## Happy path

```text
ADMITTED
  -> SPECIFY -> REVIEW_R1 -> DRAFT_PR -> [WAIT_R1_APPROVAL]
  -> DESIGN -> REVIEW_R2 -> [WAIT_R2_APPROVAL]
  -> IMPLEMENT_RED -> IMPLEMENT_GREEN -> IMPLEMENT_REFACTOR
  -> (controller publication checkpoint) -> (next task or REVIEW_R3)
  -> [WAIT_R3_APPROVAL]
  -> SHIP -> READY_FOR_REVIEW -> MERGED
```

Bracketed waits apply in attended mode. The service creates or reconciles one Draft PR after R1 author artifacts exist; a gate can still block later progress. `MERGED` is observed from GitCode and is never set by the model.

## State invariants

- One repository and Issue IID have one lifetime admission and one active feature job.
- One job has one branch, one artifact root, and at most one canonical PR.
- Only the controller advances state after validating stage output and optimistic-lock version.
- A reviewer never shares the author invocation and never edits reviewed material.
- `plan.md` and the database stage must agree before work resumes.
- A retry repeats the same bounded operation; it does not skip a gate or create a second PR.

## Rework

A `REWORK` verdict routes back to the author stage that owns the finding:

- R1 -> `SPECIFY`;
- R2 -> `DESIGN`, or `SPECIFY` only when the specification is proven wrong;
- R3 -> `IMPLEMENT`, or an upstream stage only when the finding proves an upstream artifact wrong.

Record every finding in the review file and `plan.md` rework queue. The author fills the review record's Resolution field using actual controller evidence. Re-review is always independent. Stop automation after three failed rounds at one gate.

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
- A merged PR transitions to `MERGED` and completes the job.
- A closed, unmerged PR transitions to `CLOSED` and requires human action to resume or supersede it.
- Unknown PR states do not advance the workflow.
