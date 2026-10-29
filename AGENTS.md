---
description: Shared instructions for AI coding assistants in agent-core-java.
---

# AGENTS.md

Shared instructions for AI coding assistants working in `agent-core-java`.
Keep this file specific, factual, and cross-tool. Prefer nearby code and
tests over assumptions.

`pom.xml` is the canonical source of truth for build/tooling settings.

## What This Repo Is

Java packages below live under `core/src/main/java/` unless noted otherwise.

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
- `com.openjiuwen.auto_harness/`: automated testing/evaluation infrastructure
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
- Each module uses `src/test/java/` for JUnit 5 tests; `system-test` tagged tests are excluded
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

## Build Layout and Commands

The root Maven reactor contains `core/`, `retrieval/`, and `memory/`.
Run these commands from the repository root with JDK 17 or newer:

- Compile: `mvn compile`
- Run default tests: `mvn test`
- Build and verify all modules: `mvn clean verify`
- Install without running tests: `mvn clean install -DskipTests`
- Run a targeted core test: `mvn -pl core test -Dtest=ClassName`
  (or `-Dtest=ClassName#methodName`). Use `-pl retrieval` or `-pl memory`
  for those modules; install required sibling dependencies first.
- Coverage report: `mvn test jacoco:report`

Default tests exclude `system-test`; core also excludes `perftest`.
For configured system tests in a selected module, use
`mvn -pl core test -Dsurefire.groups=system-test -Dsurefire.excludedGroups=perftest`.
Check the selected module's POM and test prerequisites before running tests
that need external services.

## Java & JDK 17 Notes

- Source and target: Java 17. Use JDK 17 features (records, sealed classes,
  pattern matching for instanceof, text blocks) where appropriate.
- Lombok is used (`provided` scope). Do not add Lombok annotations to code
  that doesn't already use them; prefer explicit code for new files.
- Jackson for JSON; versions are defined in the POMs. Reuse `ObjectMapper` as singleton; never create
  per-call.
- SLF4J + Logback for logging. Logger must be
  `private static final` (see `G.LOG.01`/`G.LOG.02`).
- OkHttp for HTTP. Connection pool tuning matters for LLM call
  throughput (see `.claude/skills/performance-tuning/SKILL.md`).
- Do not use `Executors.newCachedThreadPool()` / `newFixedThreadPool()` —
  create `ThreadPoolExecutor` explicitly with bounded queue and named threads
  (see `G.CON.12` in the full coding standard).

## Coding Standards and Verification

- Use UTF-8, four-space indentation, explicit imports, and the surrounding
  project's Java style. Keep public API documentation current.
- Consult the relevant community rules and skills below for the task at hand.
  They do not require loading every category or following a fixed review sequence
  for every edit.
- Inspect the affected behavior and reuse existing test coverage. Add regression
  coverage when a meaningful gap exists; do not add tests mechanically.
- Run relevant tests and build checks for the affected scope, and use
  `git diff --check` to check whitespace. Investigate failures without weakening
  assertions, skipping checks, or changing duplicate-resource rejection to hide
  resource leaks.
- External integration and smoke tests require their documented services and
  configuration. Run them when relevant to the change; a separate checkout or
  developer-specific environment is not a prerequisite for every commit.
- Preserve unrelated changes and developer-local configuration. Report checks
  actually run, their results, and any unavailable verification.

## Reference Guides

- Full coding standards (144 rules):
  `.claude/skills/coding-standard-full/SKILL.md`
- Coding standards quick reference: `.claude/rules/coding-standard.md`
- Performance tuning (JDK 17 baseline): `.claude/skills/performance-tuning/SKILL.md`
- Agent Team quick build guide: `.claude/skills/agent-team-guide/SKILL.md`
- Workflow application guide: `.claude/skills/workflow-guide/SKILL.md`
- Deep operational guides: `.claude/skills/`
- Permissions and env vars: `.claude/settings.json`
