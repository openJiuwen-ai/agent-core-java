# com.openjiuwen.agent_evolving.signal

`com.openjiuwen.agent_evolving.signal` provides the Java package facade for Python `openjiuwen.agent_evolving.signal`.

## class SignalPackage

`SignalPackage` mirrors Python's `openjiuwen/agent_evolving/signal/__init__.py`.

- `PYTHON_MODULE`: `openjiuwen/agent_evolving/signal/__init__.py`
- `DESCRIPTION`: `Signal module: evolution signal detection and conversion.`
- `EXPORTED_SYMBOLS`: the exported symbol list in the same order as Python `__all__`

Current exported symbols:

- `EvolutionSignal`
- `EvolutionCategory`
- `EvolutionTarget`
- `get_signal_source`
- `make_evolution_signal`
- `make_signal_fingerprint`
- `ConversationSignalDetector`
- `SignalDetector`
- `TeamSignalDetector`
- `TeamSignalType`
- `TrajectoryIssue`
- `UserIntent`
- `build_team_trajectory_summary`
- `get_team_signal_skill_content`
- `get_team_trajectory_issues`
- `from_evaluated_case`
- `from_evaluated_cases`
- `make_team_trajectory_signal`
- `make_team_user_intent_signal`
- `parse_team_model_json`

Concrete signal classes and helpers are implemented by the Java files mapped from their own Python modules. `SignalPackage` only records the package-level export contract.

## class FromEval

`FromEval` mirrors Python's `openjiuwen/agent_evolving/signal/from_eval.py`.

Core behavior:

- `fromEvaluatedCase` converts one `EvaluatedCase` into an `EvolutionSignal`.
- When `scoreThreshold` is provided and `case.score >= scoreThreshold`, no signal is produced.
- Score `0.0` produces signal type `low_score`; other unfiltered scores produce `evaluated`.
- The signal section is `Troubleshooting`, excerpt is formatted as `score=%.2f`, and an empty operator id becomes a null skill name.
- Context includes `question`, `label`, `answer`, `reason`, and `score`.
- Signal creation goes through `EvolutionSignals.makeEvolutionSignal` with `source=offline_evaluation`.
- `fromEvaluatedCases` batch-converts cases and drops filtered-out results.

## team signal helpers

`TeamSignalType`, `UserIntent`, `TrajectoryIssue`, `TeamSignals`, and `TeamSignalDetector` mirror Python's `openjiuwen/agent_evolving/signal/team.py`.

Core behavior:

- `TeamSignals.parseTeamModelJson` accepts direct JSON, fenced JSON blocks, lightly repaired JSON, and balanced embedded object/array substrings.
- `TeamSignals.buildTeamTrajectorySummary` records tool calls and LLM responses, with larger argument/result budgets for collaboration-critical tools.
- `TeamSignals.makeTeamUserIntentSignal` builds a `user_intent` signal with `source=explicit_request`.
- `TeamSignals.makeTeamTrajectorySignal` builds a `trajectory_issue` signal with `source=passive_trajectory`, trajectory issues, and skill content.
- `TeamSignals.getTeamTrajectoryIssues` and `getTeamSignalSkillContent` read normalized team-signal context.
- `TeamSignalDetector.detectUserIntent` extracts recent user messages, builds a language-specific prompt, calls `LlmResilience.invokeTextWithRetry`, and returns `UserIntent` only when the model response marks an improvement.
- `TeamSignalDetector.detectTrajectoryIssues` summarizes the trajectory, parses a model JSON array, normalizes issues, and keeps only medium/high severity issues.
- `TeamSignalDetector.detectTrajectorySignals` wraps non-empty detected issues into the canonical passive trajectory signal.
