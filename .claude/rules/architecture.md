---
description: Public API, Card/Config split, and module boundaries for agent-core-java.
language: english
paths:
  - "src/main/java/com/openjiuwen/**/*.java"
  - "src/test/java/com/openjiuwen/**/*.java"
---

# Architecture Rules

## Public API

- Treat public classes/methods documented in Javadoc, examples, and
  README snippets as public API.
- Keep public API changes additive: preserve names and parameter order.
- Prefer overloaded methods or `Optional` parameters for new optional
  parameters, not changing existing signatures.
- Compatibility tests (`*CompatibilityTest.java`) enforce Java-Python
  parity; do not break them without explicit justification.

## Card / Config Split

**Design intent**: Cards are static metadata, serializable and
transportable; Configs are runtime objects holding resources and
state. Cards cross process boundaries (e.g., A2A protocol); Configs
remain process-local.

**When to add a new Card type**:
- Metadata needs to cross process/service boundaries
- Metadata needs to be persisted or stored
- Capabilities need to be discovered and registered at runtime

**Anti-patterns**:
- Putting `sessionId`, `runner`, or other runtime data in a Card
- Defining static description fields in a Config (should be in a Card)
- Injecting dynamic computation into a Card's `toMap()` method

## Module Boundaries

| Package | Responsibility |
|---|---|
| `core.singleagent` | Current single-agent API. `BaseAgent` → `ReActAgent` is primary; `ControllerAgent` is event-driven variant |
| `harness` | Deep agent framework: prompts, rails, tools, factory/config, workspace. Changes here are tightly coupled — inspect tests |
| `agentteams` | Leader-Teammate team framework. `TeamAgent` serves both roles (switched by `TeamRole`). `CoordinatorLoop` only wakes up; business logic in `EventDispatcher` + team tools |
| `core.workflow` | DAG workflow engine. `Workflow` builds graph of `WorkflowComponent` instances, compiles to `PregelGraph` |
| `core.runner` | Singleton runtime facade. `ResourceMgr` manages `AgentMgr`, `ToolManager`, `ModelMgr`, `WorkflowMgr`, `TagMgr`. Use stable IDs, keep tests isolated |
| `core.sysop` | System operations (shell, fs, code). Security-sensitive — preserve validation around paths, shell, approvals, interrupts |
| `core.memory` | `LongTermMemory` interface. Graph memory + lite memory + external providers |
| `core.retrieval` | `KnowledgeBase` base class. Retrievers, embedding, indexing implementations |
| `autoharness` | CI/CD pipelines. `AutoHarnessOrchestrator` entry point. Security-sensitive |
| `agentevolving` | RL training: `Trainer`, `BaseOptimizer`, `BaseEvaluator`, checkpointing |
| `spi` | Storage backend SPI: `BaseKVStore` / `BaseVectorStore` / `BaseObjectStorageClient` |
| `extensions` | Optional integrations (A2A, Redis, sandbox, MQ, vendor adapters). Must not create circular deps with core |

**Rule**: Before changing a module, inspect the touched class, its
public surface, and nearby tests/examples. Do not refactor unrelated
areas opportunistically.

**Rule**: `TeamAgent` is a single implementation serving both Leader
and Teammate roles (switched by `TeamRole`). Do not split into two
classes.
