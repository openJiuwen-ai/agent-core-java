# Auto Harness Infra Baseline

The Java infra baseline currently exposes:

- `SessionBudgetController`
- `FixLoopController`
- `FixLoopResult`
- `CIGateRunner`
- `CIGateResult`
- `GitOperations`
- `WorktreeManager`

This first layer gives Java a minimal control-plane for budget, CI, fix loops, and worktree path planning.

The current workspace/helper slice now also mirrors the narrow Python path-planning helpers for:

- topic slug generation
- `auto-harness/<slug>` branch naming
- `<timestamp>-<slug>` worktree naming
- readonly snapshot path derivation
- managed worktree root checks
- `dataDir -> worktrees/cache repo` path derivation with local-repo fallback
