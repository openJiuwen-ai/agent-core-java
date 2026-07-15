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
- Lint / code check: follow `.claude/rules/coding-standard.md` (Huawei CodeArts Check 102 rules)

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
  (see `X.CON.06` in coding-standard).

## More Detail

- Coding standards (102 rules): `.claude/rules/coding-standard.md`
- Performance tuning (JDK 17 baseline): `.claude/rules/performance-tuning.md`
- Agent Team quick build guide: `.claude/rules/agent-team-guide.md`
- Workflow application guide: `.claude/rules/workflow-guide.md`
- Deep operational guides: `.claude/skills/`
- Permissions and env vars: `.claude/settings.json`
