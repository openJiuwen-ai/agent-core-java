# com.openjiuwen.agent_evolving.UpdateExecution

`UpdateExecution` mirrors Python's `openjiuwen/agent_evolving/update_execution.py`.

Core behavior:

- `executeUpdates` filters out null update values before calling `EvolutionTypes.normalizeUpdates`.
- Normalized updates are applied by operator id and target through `Operator.applyUpdate`.
- Missing operators produce failed `ApplyResult` values with the normalized update mode, effect, payload, change type, and metadata.
- Null update values produce failed `ApplyResult` values with `value=null` and `update value is None`.
- `applyUpdates` is a compatibility alias for `executeUpdates`.
- `summarizeApplyResults` returns `total`, `applied`, and `failed` counts.

This helper does not persist results and does not perform approval flow decisions.
