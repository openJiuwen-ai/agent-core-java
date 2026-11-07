# agent-core-designer

Design-mode graph authoring, execution, and multimodal media pipeline.

This module is a **product module inside the agent-core-java repository**. It
is intentionally separate from `core`: `core` stays a vendor-neutral framework,
while `designer` owns the `designer-execution-graph.v1` contract, the director
chain, node handlers, MiniMax media calls, and ffmpeg composition.

## Dependency direction

```text
agent-core-designer  ->  agent-core-java (core)
jiuwenswarm-java     ->  agent-core-designer
```

`designer` must not import `jiuwenswarm-java` or `agent-runtime-java`.

## Current state

- `com.openjiuwen.designer.schema` — graph/run records, constants, normalizers.
- `com.openjiuwen.designer.store` — normalized JSON graph/run store with atomic
  writes and in-process graph locks.
- `com.openjiuwen.designer.execution` — Pregel graph prototype. Raw Pregel
  nodes receive an empty `inputs` map, so designer nodes read upstream results
  from a per-run context (blackboard) while the graph supplies ordering and
  fan-out.

## Build

```bash
mvn -o -B -f designer/pom.xml test
mvn -o -B -f designer/pom.xml -DskipTests install
```

The module targets Java 17 so it stays consumable by `core`; Swarm runs on
Java 21. Jackson and SLF4J versions follow `core` so the two artifacts do not
split dependency families.
