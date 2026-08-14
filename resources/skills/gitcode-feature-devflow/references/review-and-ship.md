# Independent Reviews and Ship

## Reviewer rules

Review a frozen artifact version or commit supplied by the controller. Do not edit the reviewed target. Write a new record from `assets/review-template.md` in `reviews/`.

Classify each finding:

- `critical`: unsafe, incorrect, untestable, gate-bypassing, security/compatibility loss, or missing core requirement;
- `important`: material ambiguity, weak design/test coverage, maintainability risk, or evidence gap;
- `minor`: nonblocking clarity or polish.

Each finding needs a stable ID, evidence anchor, impact, required outcome, owning author stage, and empty Resolution cell. Any open critical/important finding yields `REWORK`. Minor findings may pass only when their disposition is explicit.

## R1 rubric

Check problem/value clarity, scope/non-goals, atomic testable requirements, Given/When/Then acceptance, NFR/interface/compatibility constraints, assumptions, decision ownership, and traceability completeness. Do not judge implementation style at R1.

## R2 rubric

Check consistency with passed specification and actual repository architecture, alternatives, interfaces, failure/concurrency/persistence behavior, compatibility, security, observability, rollback, CASE coverage, and a uniquely executable plan. Verify path scope and permanent exclusions.

## R3 rubric

Inspect tests before production code. Check that each claimed RED could fail for the intended reason, assertions prove observable behavior, boundary/failure/concurrency cases match design, and final container evidence covers the final tree. Then inspect implementation correctness, coding-standard compliance, simplicity, error handling, compatibility, security, and diff scope.

Sample or challenge suspicious evidence rather than trusting prose. Reviewer assertions are still not execution evidence.

## System-test review rubric

Review the test-repository diff against `references/system-test.md`. Confirm that
each scenario is a true end-to-end flow with exact acceptance assertions, does
not duplicate an existing longer scenario, and uses a publicly supportable
entry point. Reject tests that silently degrade to unit scope, rely on
sleeps/network/credentials, use `@Disabled` or assumptions, weaken acceptance
semantics, or hide an SDK gap. Verify the one exact
configured-smoke-plus-new-test Gate receipt belongs to the frozen merged feature
commit and current test-code fingerprint; do not require a separate compile or
repository-wide test run.

## Re-review

Create a new suffixed review record. Verify every prior critical/important finding has a concrete Resolution and current evidence; then look for regressions. Never overwrite history. Route remaining findings to the owning author stage.

## SHIP

SHIP starts automatically after a passing independent R3. It does not merge or deploy.

1. Verify gate records match `plan.md` and the database stage supplied by the controller.
2. Check every task and traceability row against real final-tree evidence.
3. Confirm the Controller's final exact-selector profile passed on the final
   tree. Do not request or claim a repository-wide Maven suite.
4. Promote confirmed specification/design content into existing long-term component docs with minimal edits. Preserve the feature artifacts in place.
5. Write `closeout.md` from `assets/closeout-template.md`, including DoD, promoted paths, debts with destinations, and one process learning.
6. Return a recommendation to update the same PR from Draft to ready for human review.

If evidence, promotion ownership, or debt disposition is unclear, return `BLOCKED`. When post-merge testing is enabled, SHIP completes feature delivery only through feature PR readiness; human merge starts the system-test stage. Human merge of the separate system-test PR is the successful terminal event.
