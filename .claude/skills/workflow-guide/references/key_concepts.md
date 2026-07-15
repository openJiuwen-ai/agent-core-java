# Workflow Key Concepts In-Depth

This file supplements the "Key Concepts Quick Reference" in SKILL.md, providing deeper explanations for each concept. Read on demand when users ask "what does a certain concept mean".

## Capability Layering Mental Model

Understand the Java workflow across four layers:

1. **Graph Definition Layer**: `Workflow` / `WorkflowCard` -- what this graph is
2. **Component Orchestration Layer**: `Start` / `End` / business nodes / edges -- what nodes are in the graph and how they connect
3. **Execution and Session Layer**: `WorkflowSessions` / `WorkflowOutput` / `WorkflowExecutionState` / `WorkflowChunk` -- how this execution runs and how state is carried
4. **Application Integration Layer**: `WorkflowAgent` / `Runner` -- how to embed into a more complete application

For building a single workflow, the first three layers are sufficient; the fourth layer is only needed for multi-workflow entry points.

## Workflow

`Workflow` is the central object, simultaneously responsible for three things:

1. **Graph composition**: Register nodes, declare input/output schemas, connect edges
2. **Execution**: `invoke(...)` batch / `stream(...)` incremental
3. **Compatibility wrapping**: Multiple overloads for different parameter orders, different schema writing styles, and legacy integration

Understand it as "an executable graph". It is not a workflow list, not an intent router -- those application-layer responsibilities belong to `WorkflowAgent`.

## WorkflowCard

The identity card of a workflow, most commonly containing: `id` / `name` / `version` / `description` / `inputParams`.

It is not just "for human reading":
- `Workflow` itself holds this card
- `WorkflowAgent.addWorkflows(...)` reads the `WorkflowCard` description information during registration
- `inputParams` is the input schema this workflow exposes externally

`WorkflowCard` represents "who this workflow is and what input it accepts", not runtime dynamic state.

## Nodes / Components

Each node in the workflow graph is a "component", divided into three categories:

### Start/End Nodes
- `Start`: Entry node, passes through input as-is
- `End`: Exit node, produces the final result; can return batch results or participate in `stream` / `transform` / `collect`

### Business Nodes
- `QuestionerComponent`: Asks follow-up questions for missing fields, triggers `INPUT_REQUIRED`
- `LLMComponent`: Calls the LLM, supports streaming output
- `BranchComponent`: Conditional branching
- `IntentDetectionComponent`: Intent recognition
- `SubWorkflowComponentImpl`: Sub-workflow

### Custom Nodes
Extend based on `WorkflowComponent` or `ComponentComposable`.

## Edges: Normal Edge / Conditional Edge / Streaming Edge

Registering components only places nodes; to make the graph run, edges must define execution relationships.

### Normal Edge `addConnection(...)`
Sequential dependency: the previous one finishes before the next one executes. Suitable for: linear processes, convergence after parallel branches, batch input/output passing.

### Conditional Edge `addConditionalConnection(...)`
The next node is decided at runtime. Supports two types of routing input:
- `BranchRouter`
- `Function<Object, Object>` routing function

Suitable for: conditional branching, dynamic routing, loops or back-jumps.

### Streaming Edge `addStreamConnection(...)`
Passes incremental output between nodes that support streaming capability. Typical pairing:
- Upstream `STREAM` or `TRANSFORM`
- Downstream `TRANSFORM` or `COLLECT`

Most common scenario: `LLMComponent` produces chunks -> `End` outputs with `responseMode = "streaming"`.

**Note**: Conditional routing does not correspond to an additional `ConnectionType` enum value; it is expressed through `addConditionalConnection(...)` + router.

## WorkflowSessions and Session

`WorkflowSessions` is the workflow package-level session creation facade, most commonly used entry points:
- `WorkflowSessions.createWorkflowSession()`
- `WorkflowSessions.createWorkflowSession(String sessionId)`

Returns `WorkflowSessionApi`, which handles:
- Saving execution state for this run
- Sharing intermediate results between nodes
- Handling interactive input resumption
- Managing streaming output, trace, and other runtime capabilities

**Key**: When a workflow execution is interrupted and needs to continue the same flow, the same session must be reused.

## WorkflowOutput

The return container from `invoke(...)`, containing two fields:
- `result`: Execution result
- `state`: `WorkflowExecutionState`

How to read:
```java
WorkflowOutput output = workflow.invoke(inputs, session, null);
Object result = output.getResult();
WorkflowExecutionState state = output.getState();
```

Typical return forms:
- `state == COMPLETED`: `result` is the business output organized by `End`
- `state == INPUT_REQUIRED`: `result` is a collection of interactive output blocks; the caller needs to submit `InteractiveInput` to continue
- `state == ERROR`: Execution failed

## WorkflowExecutionState

| State | Meaning | How Caller Should Handle |
| --- | --- | --- |
| `COMPLETED` | Normal completion | Read `WorkflowOutput.getResult()` |
| `INPUT_REQUIRED` | More user input needed | Read interaction prompt, construct `InteractiveInput`, then call `invoke` again |
| `ERROR` | Execution failed | Log exception or propagate upward |

`INPUT_REQUIRED` is not a failure; it is an intermediate state meaning "waiting for additional input".

## Streaming Output: WorkflowChunk / OutputSchema / StreamMode

Java workflow streaming execution uses a **synchronous `Iterator<WorkflowChunk>`**.

### WorkflowChunk
Top-level alias interface for streaming chunks. The concrete type callers most commonly receive is `OutputSchema`.

### OutputSchema
Standard output chunk, containing:
- `type`
- `index`
- `payload`

Callers can iterate the iterator and refresh the UI or logs chunk by chunk.

### StreamMode
`workflow.stream(...)` specifies which streams to subscribe to via `List<StreamMode>`:
- `StreamMode.OUTPUT` (most commonly used for beginners)
- `StreamMode.TRACE`
- `StreamMode.CUSTOM`

**Note**: `StreamMode` indicates "which type of stream to subscribe to"; `WorkflowChunkType` is the top-level naming for chunk categories. The two are related but not at the same level.

## InteractiveInput

When `QuestionerComponent` requires additional information, the workflow returns `INPUT_REQUIRED`. The caller:

1. Reads the current interaction prompt
2. Constructs `InteractiveInput`
3. Calls `workflow.invoke(...)` again using the **same session**

```java
InteractiveInput reply = new InteractiveInput();
reply.update("questioner", "2000 yuan");  // "questioner" = component id
WorkflowOutput resumed = workflow.invoke(reply, session, null);
```

`"questioner"` must match the component id actually registered in the workflow.

## SubWorkflowComponentImpl

Wraps another `Workflow` as a node in the current graph:
- Layered organization of complex processes
- Reusable common sub-processes
- Clearer visualization and structure

## Connecting Concepts: A Typical Execution

1. Use `WorkflowCard` to describe the workflow identity and input
2. Use `Workflow` to register `Start`, business nodes, and `End`
3. Use normal edges, conditional edges, or streaming edges to connect nodes into a graph
4. Use `WorkflowSessions` to create the execution session
5. Call `invoke(...)` to get `WorkflowOutput`, or `stream(...)` to get `Iterator<WorkflowChunk>`
6. If `INPUT_REQUIRED`, use `InteractiveInput` to resume execution in the same session

## Terminology Boundary Reminders

- `Workflow` != `WorkflowAgent`: the former is a single graph, the latter is the application-layer entry point
- `WorkflowCard` != session: the former is static description, the latter is runtime state
- `stream(...)` chunk output != `invoke(...)` final result: the former is incremental process, the latter is the return package after execution concludes
