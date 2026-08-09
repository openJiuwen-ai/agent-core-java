---
name: workflow-guide
description: Workflow application building guide. Based on the com.openjiuwen.core.workflow package, guides users to build, orchestrate, and execute a single workflow graph from scratch, as well as host multiple workflows with WorkflowAgent. Actively apply when users build workflows, use Workflow/WorkflowCard/WorkflowSessions, register Start/End/LLMComponent/QuestionerComponent, connect edges with addConnection/addConditionalConnection/addStreamConnection, call invoke/stream, handle INPUT_REQUIRED interactive input, or do WorkflowAgent multi-workflow routing. Related keywords: workflow, WorkflowCard, WorkflowSessions, WorkflowOutput, invoke, stream, QuestionerComponent, LLMComponent, addConnection, INPUT_REQUIRED, WorkflowAgent, SubWorkflowComponentImpl. Not applicable for: agent team assembly (use agent-team-guide), single ReAct agent, discussions unrelated to workflow.
---

# Workflow Application Building Guide

This skill guides building workflow applications based on the `com.openjiuwen.core.workflow` package. Core mental model: **a workflow = an executable graph composed of nodes and edges**, executed in batch with `invoke(...)` or in streaming mode with `stream(...)`.

## Core Mental Model

- `Workflow` is "an executable graph", not a workflow list, not an intent router.
- `WorkflowAgent` is the application-layer entry point that hosts multiple workflows and selects which one to run; `Workflow` is a single graph.
- Registering components defines "what the nodes are"; connecting edges defines "what path the nodes execute along". **Both are required**.
- `invoke(...)` focuses on complete results; `stream(...)` focuses on incremental output.
- `INPUT_REQUIRED` is not a failure; it is an intermediate state meaning "waiting for additional input", and execution resumes by reusing the same session.

## Key Concepts Quick Reference

| Term | Java Type | Meaning |
| --- | --- | --- |
| Workflow | `Workflow` | The main workflow class exposed to users; a directed graph |
| Workflow Card | `WorkflowCard` | Metadata: id/name/version/description/inputParams |
| Start/End Nodes | `Start` / `End` | Entry node passes through input / Exit node produces final result |
| Business Nodes | `QuestionerComponent` / `LLMComponent` / `BranchComponent` / `IntentDetectionComponent` | Actual business logic |
| Sub-Workflow | `SubWorkflowComponentImpl` | Wraps another Workflow as a node call |
| Normal Edge | `addConnection(...)` | Batch sequential dependency |
| Conditional Edge | `addConditionalConnection(...)` | Runtime decision for next node (`BranchRouter` or `Function`) |
| Streaming Edge | `addStreamConnection(...)` | Passes incremental data |
| Workflow Session | `WorkflowSessions` / `WorkflowSessionApi` | Saves execution state, shares intermediate results, handles interactive resumption |
| Workflow Output | `WorkflowOutput` | Return container from `invoke(...)`, contains `result` + `state` |
| Execution State | `WorkflowExecutionState` | `COMPLETED` / `INPUT_REQUIRED` / `ERROR` |
| Streaming Chunk | `WorkflowChunk` / `OutputSchema` | Incremental data chunk returned by `stream(...)` |
| Interactive Input | `InteractiveInput` | Input submitted to resume execution after `INPUT_REQUIRED` |
| Application Entry | `WorkflowAgent` | Hosts multiple workflows, performs intent recognition and routing |

## Scenario Quick Reference

Locate the corresponding section in this skill by task scenario:

| Scenario | Go To |
| --- | --- |
| Build a minimal workflow from scratch | "Quick Start: Minimal Workflow" |
| Build a streaming workflow | "Quick Start: Streaming Workflow" |
| Handle follow-up questions / interactive input | "INPUT_REQUIRED and Interactive Resumption" |
| Choose invoke vs stream | "invoke vs stream" |
| Implement conditional branching / loops | "Conditional Edges and Routing" |
| Nest sub-workflows | "Sub-Workflow" |
| Host multiple workflows | "WorkflowAgent Multi-Workflow Routing" |
| Choose component ability type | "Component Ability Types (INVOKE/STREAM/COLLECT/TRANSFORM)" |
| Understand schema reference expressions | "Schema Reference Syntax" |
| Troubleshoot workflow not executing | "Pitfall FAQ" |

## Quick Start: Minimal Workflow

Minimal example: `Start -> QuestionerComponent -> End`, demonstrating follow-up questioning + execution resumption.

```java
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.*;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;

// 1. Define card
WorkflowCard card = WorkflowCard.builder()
        .id("transfer_flow")
        .name("Transfer Service")
        .version("1.0")
        .description("Collect missing transfer amount and return final result")
        .inputParams(Map.of("type", "object",
                "properties", Map.of("query", Map.of("type", "string")),
                "required", List.of("query")))
        .build();

// 2. Create Workflow and register nodes
Workflow workflow = new Workflow(card);
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);

QuestionerConfig qConfig = new QuestionerConfig();
qConfig.setModelClientConfig(clientConfig);
qConfig.setModelConfig(requestConfig);
qConfig.setQuestionContent("Please provide the transfer amount");
qConfig.setExtractFieldsFromResponse(true);
qConfig.setFieldNames(List.of(FieldInfo.builder()
        .fieldName("amount").description("Transfer amount").required(true).build()));
qConfig.setWithChatHistory(false);
qConfig.setMaxResponse(10);
workflow.addWorkflowComp("questioner", new QuestionerComponent(qConfig),
        Map.of("query", "${start.query}"), null);

workflow.setEndComp("end",
        new End(Map.of("responseTemplate", "Transfer amount is {{amount}}")),
        Map.of("amount", "${questioner.amount}"), null);

// 3. Connect edges (required, cannot omit)
workflow.addConnection("start", "questioner");
workflow.addConnection("questioner", "end");

// 4. Create session and execute
WorkflowSessionApi session = WorkflowSessions.createWorkflowSession("conversation-001");
WorkflowOutput output = workflow.invoke(Map.of("query", "I want to transfer"), session, null);

// 5. Handle INPUT_REQUIRED
if (WorkflowExecutionState.INPUT_REQUIRED.equals(output.getState())) {
    InteractiveInput reply = new InteractiveInput();
    reply.update("questioner", "2000 yuan");
    output = workflow.invoke(reply, session, null);
}

if (WorkflowExecutionState.COMPLETED.equals(output.getState())) {
    System.out.println(output.getResult());
}
```

**Key Reminders**:
- `${query}` reads from the workflow top-level input; `${start.query}` reads from Start output; `${questioner.amount}` reads from questioner output.
- `addWorkflowComp(...)` only places nodes and **does not form execution order**; order is determined by `addConnection(...)`.
- To resume execution, you must **reuse the same session**; switching sessions will lose previous state.

## Quick Start: Streaming Workflow

Connect `LLMComponent` incremental output to `End` via `addStreamConnection(...)`.

```java
LLMCompConfig llmConfig = new LLMCompConfig();
llmConfig.setModelClientConfig(clientConfig);
llmConfig.setModelConfig(requestConfig);
llmConfig.setTemplateContent(List.of(
        Map.of("role", "system", "content", "You are a concise assistant."),
        Map.of("role", "user", "content", "{{query}}")));
llmConfig.setResponseFormat(Map.of("type", "text"));
llmConfig.setOutputConfig(Map.of("answer", Map.of("type", "string", "required", true)));

Workflow streamWorkflow = new Workflow();
streamWorkflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
streamWorkflow.addWorkflowComp("llm", new LLMComponent(llmConfig),
        Map.of("query", "${start.query}"), null);

// End uses streaming mode; the key is streamInputsSchema
streamWorkflow.setEndComp("end",
        new End(Map.of("responseTemplate", "{{answer}}")),
        null, null,
        Map.of("answer", "${llm.answer}"), null,
        "streaming");

streamWorkflow.addConnection("start", "llm");
streamWorkflow.addStreamConnection("llm", "end");  // Streaming edge

// Execute: returns synchronous Iterator
Iterator<WorkflowChunk> chunks = streamWorkflow.stream(
        Map.of("query", "Introduce Java workflows"),
        WorkflowSessions.createWorkflowSession(),
        null,
        List.of(StreamMode.OUTPUT));

while (chunks.hasNext()) {
    WorkflowChunk chunk = chunks.next();
    if (chunk instanceof OutputSchema output) {
        System.out.println(output.getPayload());
    }
}
```

**Key Reminders**:
- `End` in streaming mode must declare `streamInputsSchema` (the 5th parameter), not `inputsSchema`.
- `stream(...)` returns a **synchronous Iterator**, not an async stream.
- `StreamMode.OUTPUT` is the most common subscription mode; there are also `TRACE` / `CUSTOM`.

## invoke vs stream

## invoke vs stream

| Invocation | Suitable Scenario | Return |
| --- | --- | --- |
| `invoke(...)` | Get complete result in one call; includes interactive pause and resumption | `WorkflowOutput` (result + state) |
| `stream(...)` | Consume output incrementally while executing | `Iterator<WorkflowChunk>` |

## INPUT_REQUIRED and Interactive Resumption

When `QuestionerComponent` needs to ask a follow-up question, the workflow returns `INPUT_REQUIRED`:

1. Read the current interaction prompt
2. Construct `InteractiveInput`, `reply.update("component_id", "user answer")`
3. **Reuse the same session** and call `workflow.invoke(reply, session, null)` again

```java
InteractiveInput reply = new InteractiveInput();
reply.update("questioner", "2000 yuan");  // "questioner" must match the component id used at registration
WorkflowOutput resumed = workflow.invoke(reply, session, null);
```

**Key**: The first parameter of `reply.update` is the **component id** (e.g., `"questioner"`), not the node type name. Switching sessions will lose previously accumulated state.

## Conditional Edges and Routing

Use `addConditionalConnection(...)` to implement runtime branching:

```java
// Method 1: BranchRouter
workflow.addConditionalConnection("branch_node", new BranchRouter(...));

// Method 2: Function routing
workflow.addConditionalConnection("branch_node", (Function<Object, Object>) input -> {
    String intent = ((Map<String, String>) input).get("intent");
    return switch (intent) {
        case "transfer" -> "transfer_node";
        case "query" -> "query_node";
        default -> "default_node";
    };
});
```

Suitable for: conditional branching, dynamic routing, loops or back-jump scenarios.

## Sub-Workflow

Use `SubWorkflowComponentImpl` to wrap another `Workflow` as a node in the current graph:

```java
Workflow subWorkflow = new Workflow(subCard);
// ... build sub-workflow

workflow.addWorkflowComp("sub", new SubWorkflowComponentImpl(subWorkflow),
        Map.of("input", "${start.input}"), null);
workflow.addConnection("start", "sub");
workflow.addConnection("sub", "end");
```

Benefits: layered organization of complex processes, reusable common sub-processes, clearer visual structure.

## WorkflowAgent Multi-Workflow Routing

`WorkflowAgent` hosts multiple workflows at the application layer and selects which one to execute based on user intent.

```java
WorkflowAgentConfig config = WorkflowAgentConfig.builder()
        .id("workflow_agent_java_example")
        .description("Java multi-workflow financial assistant example")
        .model(modelConfig)
        .promptTemplate(List.of(Map.of("role", "system", "content", systemPrompt)))
        .defaultResponse(DefaultResponse.builder().text(defaultText).build())
        .build();

WorkflowAgent agent = new WorkflowAgent(config);
agent.addWorkflows(List.of(transferWorkflow, investWorkflow, balanceWorkflow));
```

**`WorkflowEventHandler` intent recognition priority**:
1. If the input is an `InteractiveInput` with a node id -> directly resume the interrupted workflow (no LLM intent recognition)
2. If only one workflow is configured -> go directly to that one
3. Multiple workflows -> LLM intent recognition to select
4. None matched -> return `defaultResponse`

`defaultResponse` is important: when intent recognition does not match, return a default reply rather than forcing a wrong business flow selection.

## Component Ability Types (INVOKE/STREAM/COLLECT/TRANSFORM)

| Ability | Input | Output | Corresponding Method | Suitable Scenario |
| --- | --- | --- | --- | --- |
| `INVOKE` | batch | batch | `invoke(...)` | Normal nodes: Start, Questioner, Tool |
| `STREAM` | batch | stream | `stream(...)` | LLM chunk-by-chunk output, ProducerNode |
| `COLLECT` | stream | batch | `collect(...)` | Aggregate upstream multi-frame, End receives stream then returns batch |
| `TRANSFORM` | stream | stream | `transform(...)` | Per-frame rewriting, filtering, field supplementation |

**Edge Pairing**:
- Upstream INVOKE -> Downstream INVOKE: use `addConnection`
- Upstream STREAM/TRANSFORM -> Downstream TRANSFORM/COLLECT: use `addStreamConnection`

## Schema Reference Syntax

Use `${...}` in node configuration to reference other nodes' output:

| Syntax | Meaning |
| --- | --- |
| `${query}` | Read `query` from workflow top-level input |
| `${start.query}` | Read `query` from Start node output |
| `${questioner.amount}` | Read `amount` from questioner node output |
| `{{amount}}` | Render variable in End's `responseTemplate` |

**Note**: `inputsSchema` only describes "where to get values" and **does not form execution order**; execution order is determined by `addConnection(...)`.

## Pitfall FAQ

| Symptom | Cause | Solution |
| --- | --- | --- |
| Workflow does not run, nodes not executing | Only `addWorkflowComp` without `addConnection` | Add `addConnection(srcId, targetId)` |
| Resumption after `INPUT_REQUIRED` fails, state lost | Switched to a new session | Reuse the same `WorkflowSessionApi` |
| `reply.update("Questioner", ...)` has no effect | Wrong component id (case mismatch) | id must match `addWorkflowComp("questioner", ...)` |
| Streaming `End` receives no data | Used `inputsSchema` instead of `streamInputsSchema` | In streaming mode use the 5th parameter `streamInputsSchema` |
| `stream(...)` hangs with no data | Did not pass `StreamMode.OUTPUT` | `stream(inputs, session, null, List.of(StreamMode.OUTPUT))` |
| Conditional edge routing returns non-existent target id | Router return value does not match registered id | Check that the router returns a string that is a registered component id |
| `End` output template does not render | `responseTemplate` uses undefined variable | Template variables must have their source declared in `inputsSchema` |
| Multi-workflow routing always returns default reply | Intent recognition did not match | Check workflow `description` clarity; adjust system prompt |

## Usage

1. **Locate scenario**: First check "Scenario Quick Reference" to jump to the corresponding section for the current task.
2. **Copy template**: Code blocks in the Quick Start sections can be copied and modified directly.
3. **Understand concepts**: If terminology is unclear, check "Key Concepts Quick Reference" or Read `references/key_concepts.md`.
4. **Component details**: When component configuration details are needed, Read `references/components_guide.md`.
5. **Real cases**: When end-to-end reference is needed, Read `references/workflow_cases.md` (4 cases: multi-workflow financial assistant / streaming Q&A / multi-field follow-up / conditional branching).
6. **Multi-workflow**: When multi-workflow routing is needed, see the "WorkflowAgent" section.
7. **Troubleshoot**: First check "Pitfall FAQ", then check component ability types and schema syntax.
8. **Do not fabricate when uncertain**: API details should be verified against source code; this skill does not replace official API documentation.

## Reference Entries

- **Key Concepts In-Depth**: `references/key_concepts.md` (Workflow/WorkflowCard/WorkflowSessions/WorkflowOutput/execution state/streaming chunks/InteractiveInput detailed explanation)
- **Component Configuration Guide**: `references/components_guide.md` (Start/End/QuestionerComponent/LLMComponent/BranchComponent configuration details + 4 ability types)
- **Real Cases**: `references/workflow_cases.md` (4 end-to-end cases: multi-workflow financial assistant / streaming Q&A / multi-field follow-up / conditional branching)
- **Minimal Runnable Example**: `references/MinimalWorkflowExample.java` (self-contained, includes model configuration, copy and run)
- Full documentation: `documents/zh/2.开发指南/工作流/` (overview/key concepts/building workflows/using components)
- Example code: `examples/workflow_agent/WorkflowAgentExampleSupport.java` + `examples/interact/WeatherAssistantInteractExampleSupport.java`
- API documentation: `documents/zh/API文档/com.openjiuwen.core/workflow/`
- Agent team guide: `resources/skills/agent-team-guide/SKILL.md`
