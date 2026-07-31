---
description: Shared instructions for AI coding assistants in agent-core-java.
---

# AGENTS.md

Shared instructions for AI coding assistants working in `agent-core-java`.
Keep this file specific, factual, and cross-tool. Prefer nearby code and
tests over assumptions.

`pom.xml` is the canonical source of truth for build/tooling settings.

## What This Repo Is

- `com.openjiuwen.core/`: public SDK/runtime for agents, workflows, sessions,
  memory, retrieval, security, and system operations.
- `com.openjiuwen.harness/`: coding-agent framework built on core
  primitives; includes prompts, rails, tools, subagents, task loop, and
  workspace handling. Tool permission engine lives in `harness/security/`;
  prompt/tool security rails live in `harness/rails/security/`.
- `com.openjiuwen.agentteams/`: multi-agent team framework (Leader-Teammate
  model). `LeaderTeammateAgentTeam` is the declarative entry point,
  `TeamAgent` is the runtime host, `TeamBackend` is the data/message hub.
- `com.openjiuwen.agentevolving/`: RL-based agent optimization, evaluation,
  trajectory management, and checkpointing.
- `com.openjiuwen.autoharness/`: automated testing/evaluation infrastructure
  — pipelines, orchestrators, stages, and experience management.
- `com.openjiuwen.extensions/`: optional integrations such as storage
  (db/kv), checkpointer (redis), context evolver, message queue, sandbox
  providers, and vendor-specific adapters.
- `com.openjiuwen.dev_tools/`: developer tooling — agent builder, prompt
  builder, skill creator/evaluator, tune.
- `com.openjiuwen.deepagents/`: deep agent framework with middlewares,
  subagents, and tools.
- `com.openjiuwen.spi/`: Service Provider Interface for store (kv/object/
  query/vector) extensions.
- `src/test/java/`: JUnit 5 tests; `system-test` tagged tests are excluded
  from normal surefire runs.
- `examples/` and `documents/`: user-facing usage references. Update them
  when public behavior changes.

## Instruction Priority

- Follow system, tool, and user instructions first, then this file, then
  module-local docs.
- Before changing behavior, inspect the touched module, its exported
  surface, and nearby tests/examples.
- Prefer small, targeted diffs. Do not refactor unrelated areas
  opportunistically.

## Core Architecture

- Treat documented public APIs, examples, and README snippets as public API.
- Preserve the Card/Config split: cards define identity/metadata
  (`AgentCard`, `ToolCard`, `WorkflowCard`, `SysOperationCard`); runtime
  behavior belongs in configs, agents, managers, or resource instances.
- `com.openjiuwen.core.singleagent` is the current single-agent API.
  `com.openjiuwen.core.singleagent.legacy` exists for compatibility only.
- `Runner.resourceMgr` is shared process-global state. Use stable IDs
  and keep tests isolated.
- `TeamAgent` is a single implementation serving both Leader and Teammate
  roles (switched by `TeamRole`). Do not split into two classes.
- `CoordinatorLoop` does not make decisions; it only handles wake-up and
  periodic polling. All business logic is in `EventDispatcher` + team tools.

## Commands

- Compile: `mvn compile`
- Run all unit tests: `mvn test`
- Run system tests: `mvn test -Dsurefire.groups=system-test`
- Run targeted test: `mvn test -Dtest=ClassName` or `-Dtest=ClassName#methodName`
- Skip tests: `mvn compile -DskipTests`
- Code coverage report: `mvn test jacoco:report`
- Lint / code check: follow
  `.claude/skills/coding-standard-full/SKILL.md` (Huawei CodeArts Check,
  144 rules)

`mvn test` excludes `system-test` tagged tests by default (configured in
surefire plugin `<excludedGroups>`). To run them, pass
`-Dsurefire.groups=system-test`.

## Java & JDK 17 Notes

- Source and target: Java 17. Use JDK 17 features (records, sealed classes,
  pattern matching for instanceof, text blocks) where appropriate.
- Lombok is used (`provided` scope). Do not add Lombok annotations to code
  that doesn't already use them; prefer explicit code for new files.
- Jackson 2.17 for JSON. Reuse `ObjectMapper` as singleton; never create
  per-call.
- SLF4J 2.0 + Logback 1.5 for logging. Logger must be
  `private static final` (see `G.LOG.01`/`G.LOG.02`).
- OkHttp 4.12 for HTTP. Connection pool tuning matters for LLM call
  throughput (see `.claude/rules/performance-tuning.md`).
- Do not use `Executors.newCachedThreadPool()` / `newFixedThreadPool()` —
  create `ThreadPoolExecutor` explicitly with bounded queue and named threads
  (see `G.CON.12` in the full coding standard).

## Java Coding Standard (Mandatory)

These gates apply whenever Java code is added, deleted, moved, modified, or
reviewed, including tests and behavior-neutral bug-fix cleanup. Completing only
the requested line-level change is not sufficient.

### Rule-Loading Gate

Before the first Java patch, the main working agent must perform this sequence:

1. Inspect `git status`, the intended Java files, their complete enclosing
   methods or types, and nearby tests. Identify every construct the change will
   touch before selecting rule categories.
2. Read `.claude/skills/coding-standard-full/SKILL.md` completely. Do not rely
   on remembered rule text, excerpts, or another agent's summary.
3. Read the following baseline category files completely and in this order:
   `G.FMT` → `G.NAM` → `G.DCL` → `G.MET` → `G.CTL` → `G.EXP` → `G.ERR`
   → `G.CMT` → `G.OTH`.
4. After the baseline, read each applicable scenario group completely and in
   the listed order:

   - Classes, interfaces, inheritance, `equals`, or object lifecycle:
     `G.OBJ`.
   - Collections, generics, streams, or performance-sensitive allocation:
     `G.COL` → `G.PRM` → `G.TYP`.
   - Threads, executors, futures, locks, cancellation, or shared state:
     `G.CON` → `G.TYP` → `SEC_EXT`.
   - Logging changes: `G.LOG`.
   - Files, streams, paths, encodings, or other resources:
     `G.FIO` → `G.PRM` → `G.TYP`.
   - Serialization: `G.SER`.
   - External input, security-sensitive operations, XML, secrets, or command
     execution: `G.SEC` → `G.EDV` → `G.FIO` → `G.OTH` → `SEC_EXT`.
   - Any category explicitly named by the user or a CodeArts finding, even if
     it appears unrelated to the original task.

5. State the loaded categories in the working commentary or plan before
   editing. If the implementation expands into a new construct or module,
   pause editing and load the newly applicable categories first. A category
   only needs to be loaded once per task, but it must be read to the end.

### Edit and Review Gate

- Preserve existing observable behavior for code-standard-only changes.
  Prefer small, targeted diffs and do not refactor unrelated code merely to
  silence a checker.
- Treat severity 0 and 1 findings as mandatory fixes. Fix severity 2 findings
  in changed code unless doing so would alter required behavior. Resolve
  severity 3 findings in the edited scope when the correction is
  behavior-neutral. Do not suppress a finding merely to make a check pass.
- When CodeArts reports one line, inspect the entire enclosing method, all
  helpers extracted from it, and equivalent patterns in the touched class.
  Moved code and newly introduced helpers must be reviewed as new code.
- Review the completed diff in this exact order:

  1. Structure and declarations: `G.MET`, then `G.DCL`.
  2. Control flow and expressions: `G.CTL`, then `G.EXP`.
  3. Null and exception contracts: `G.MET`, then `G.ERR`.
  4. Naming, formatting, imports, comments, dead code, and logging:
     `G.NAM`, `G.FMT`, `G.CMT`, `G.OTH`, then `G.LOG` when applicable.
  5. Every scenario-specific group loaded for the task, in its loading order.

- Review both the diff and the complete changed Java files. Diff-only review
  can miss declaration distance, method size, nesting depth, import order, or
  interactions between moved code and existing branches.

### Recurring CodeArts Regression Checklist

The following rules have caused repeated cloud findings and are mandatory on
every Java review:

- `G.MET.01`: each method has at most 50 code lines, at most 5 parameters, and
  at most 4 nested code-block levels. The method itself counts as one level.
  Recalculate these limits after extracting helpers.
- `G.DCL.02`: declare and initialize each local variable close to its first
  use; declaration-to-first-use distance must not exceed 10 lines.
- `G.MET.05`/`G.MET.06`: return empty containers for empty results and use
  `Optional` for genuinely absent scalar results. Do not return or assign
  `null` to an `Optional`.
- `G.ERR.02`/`G.ERR.06`: do not catch `Throwable`, `Exception`, or
  `RuntimeException` directly, and preserve the original cause when wrapping
  an exception.
- `G.CON.10`: do not call `Thread.interrupt()` directly in business code.
  Prefer executor/future cancellation and cooperative cancellation state after
  reading the complete concurrency rules.
- `G.NAM.08`: boolean variables begin with an affirmative predicate such as
  `is`, `has`, `can`, or `should`.
- `G.FMT.04`: declare class members in the order static fields, static
  initializers, instance fields, instance initializers, constructors, then
  methods. Within fields and constructors, order access from `public` through
  `protected` and package-private to `private`.
- `G.CTL.02`: an `else if` chain ends with a meaningful `else`. Prefer guard
  clauses or independent checks when there is no meaningful final branch; do
  not add an empty `else` solely for compliance.
- `G.EXP.03`: the second and third operands of `?:` have the same declared
  type. When types differ or are unclear, use an explicitly typed local
  variable and an `if` statement instead of adding a cosmetic cast.
- `G.ERR.05`: throw an exception type that matches the method's abstraction
  level and includes enough context to identify the failed operation. Do not
  throw a raw `RuntimeException`, `Exception`, or `Throwable`.
- `G.FMT.02`/`G.FMT.03`/`G.FMT.08`/`G.FMT.10`: imports are explicit, grouped,
  and sorted; indentation uses four spaces without tabs; lines do not exceed
  120 narrow characters.

### Cloud Feedback Loop

- Treat every new CodeArts finding as both a code defect and a review-process
  signal. Before fixing it, read the complete category file named by the rule.
- Search the full touched class and the complete branch diff for the same
  syntax or semantic pattern. Fix equivalent findings in the edited scope
  together instead of waiting for another cloud round trip.
- If a broadly applicable finding is not represented above, update the
  loading matrix or recurring checklist in the same change. Keep one-off
  product behavior out of this file.
- Do not satisfy a checker with empty branches, meaningless casts, broad
  wrappers, suppressions, or behavior changes. Apply the rule's recommended
  semantic refactoring and rerun the complete review sequence.

### Verification Gate

Before declaring a Java change complete or creating a commit:

1. Run `git diff --check` and inspect `git status` to confirm the intended
   scope.
2. Identify every changed Java file and scan the complete files, not only
   added lines, for tabs, trailing whitespace, wildcard imports, lines longer
   than 120 characters, `TODO`/`FIXME`, unused imports, and dead code.
3. Re-scan changed methods for the recurring checklist above, including
   `return null`, broad catches, direct thread interruption, boolean names,
   incomplete `else if` chains, and mixed-type ternary expressions.
4. Run `mvn compile` and the narrowest affected tests. Run broader tests when
   shared runtime, concurrency, session, graph, or public API code changed.
5. Re-run the diff and scope checks after formatting or test-driven edits.
6. Run CodeArts Check when it is available. Local review is not proof that
   cloud CodeArts passed; the final report must explicitly say whether the
   cloud service was run and list any unavailable check.

If any gate fails, fix the issue or report the concrete blocker; do not mark
the task complete, commit, or push while silently leaving a failed gate.

New and changed Java code must use UTF-8, four-space indentation, no tabs or
trailing whitespace, and lines no longer than 120 narrow characters. Use
braces for control statements and keep one statement per line. Keep imports
explicit; wildcard imports are prohibited. Put static imports first, separate
import categories with one blank line, and sort each category alphabetically
as required by `G.FMT.02` and `G.FMT.03`.

Follow the declaration order, naming, Javadoc, exception handling, logging,
collection, concurrency, resource-management, serialization, and security
rules in the applicable category files. Public and protected APIs require
complete Javadoc, including ordered tags and `@since`.

## More Detail

- Full coding standards (144 rules):
  `.claude/skills/coding-standard-full/SKILL.md`
- Coding standards quick reference: `.claude/rules/coding-standard.md`
- Performance tuning (JDK 17 baseline): `.claude/rules/performance-tuning.md`
- Agent Team quick build guide: `.claude/rules/agent-team-guide.md`
- Workflow application guide: `.claude/rules/workflow-guide.md`
- Deep operational guides: `.claude/skills/`
- Permissions and env vars: `.claude/settings.json`
