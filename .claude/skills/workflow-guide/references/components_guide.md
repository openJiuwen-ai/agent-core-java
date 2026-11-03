# Component Configuration Guide

This file supplements the component section of SKILL.md, providing configuration details for each built-in component and an in-depth explanation of the 4 ability types. Read on demand when users ask "how to configure a certain component".

## Component Ability Types (ComponentAbility)

The four abilities are essentially four input/output patterns:

| Ability | Input | Output | Corresponding Method | Suitable Scenario |
| --- | --- | --- | --- | --- |
| `INVOKE` | batch | batch | `invoke(...)` | Normal nodes: Start, Questioner, Tool |
| `STREAM` | batch | stream | `stream(...)` | LLM chunk-by-chunk output, ProducerNode |
| `COLLECT` | stream | batch | `collect(...)` | Aggregate upstream multi-frame, End receives stream then returns batch |
| `TRANSFORM` | stream | stream | `transform(...)` | Per-frame rewriting, filtering, field supplementation |

The criterion is simply whether the input and output are streams. There are no additional "semi-streaming" or "async batch" ability terms.

### INVOKE
The most common batch node: receives complete input, executes once, returns complete output.
- `Start` passes through top-level input
- `QuestionerComponent` reads input and returns fields or interrupts with a request
- `ToolComponent` executes tool calls
- Most custom nodes that only need to return results in one shot

### STREAM
Receives complete input, produces output frames incrementally.
- Upstream sends batch in via `addConnection(...)`
- Downstream consumes the stream via `addStreamConnection(...)`
- LLM node continuously pushes tokens/chunks

### COLLECT
The opposite of STREAM: consumes a stream, produces a one-shot result.
- Aggregates upstream multi-frame into a final value
- Aggregates multiple stream chunks into a string, array, or statistics
- End node receives stream then returns batch uniformly

### TRANSFORM
Consumes a stream, continues outputting a stream. Suitable for per-frame rewriting, filtering, field supplementation, or repackaging.

### Edge Pairing Rules
- Upstream INVOKE -> Downstream INVOKE: `addConnection`
- Upstream STREAM/TRANSFORM -> Downstream TRANSFORM/COLLECT: `addStreamConnection`
- Conditional branching: `addConditionalConnection` + router

## Start Component

Entry node, passes through input as-is.

```java
workflow.setStartComp(
    "start",                              // Component id
    new Start(),                           // Component instance
    Map.of("query", "${query}"),           // inputsSchema: read from top-level input
    null                                   // outputsSchema (optional)
);
```

**Schema reference**:
- `${query}` reads from workflow top-level input
- Subsequent nodes read Start output using `${start.query}`

## End Component

Exit node, produces the final result. Two modes:

### Batch Mode (default)
```java
workflow.setEndComp(
    "end",
    new End(Map.of("responseTemplate", "Transfer amount is {{amount}}")),
    Map.of("amount", "${questioner.amount}"),  // inputsSchema
    null
);
```

### Streaming Mode
```java
workflow.setEndComp(
    "end",
    new End(Map.of("responseTemplate", "{{answer}}")),
    null,                                        // inputsSchema (batch)
    null,                                        // outputsSchema
    Map.of("answer", "${llm.answer}"),           // streamInputsSchema (streaming)
    null,
    "streaming"                                  // responseMode
);
```

**Key Points**:
- Batch mode uses the 3rd parameter `inputsSchema`
- Streaming mode uses the 5th parameter `streamInputsSchema`
- `responseTemplate` uses `{{var}}` for rendering; variables must have their source declared in upstream schema

## QuestionerComponent

Used for scenarios where "fields may be missing and need runtime follow-up questions".

```java
QuestionerConfig config = new QuestionerConfig();
config.setModelClientConfig(clientConfig);        // LLM client configuration
config.setModelConfig(requestConfig);             // Request parameter configuration
config.setQuestionContent("Please provide the transfer amount");  // Follow-up prompt text
config.setExtractFieldsFromResponse(true);        // Extract fields from model response
config.setFieldNames(List.of(                     // Fields to extract
    FieldInfo.builder()
        .fieldName("amount")
        .description("Transfer amount")
        .required(true)
        .build()
));
config.setWithChatHistory(false);                  // Whether to include chat history
config.setMaxResponse(10);                         // Maximum reply rounds
```

**Triggers `INPUT_REQUIRED`**: When fields are missing, the workflow returns `INPUT_REQUIRED`; the caller constructs `InteractiveInput` to resume.

**When resuming**:
```java
InteractiveInput reply = new InteractiveInput();
reply.update("questioner", "2000 yuan");  // Component id must be "questioner"
```

## LLMComponent

Calls the LLM, supports streaming output.

```java
LLMCompConfig config = new LLMCompConfig();
config.setModelClientConfig(clientConfig);
config.setModelConfig(requestConfig);

// Template content (supports multi-turn)
config.setTemplateContent(List.of(
    Map.of("role", "system", "content", "You are a concise assistant."),
    Map.of("role", "user", "content", "{{query}}")  // {{}} renders variables
));

// Response format
config.setResponseFormat(Map.of("type", "text"));

// Output schema: declare which fields the LLM outputs
config.setOutputConfig(Map.of(
    "answer", Map.of(
        "type", "string",
        "description", "Model answer",
        "required", true
    )
));
```

**Streaming output**: LLMComponent has `STREAM` ability by default; use `addStreamConnection(...)` to connect incremental output to downstream.

## BranchComponent / Conditional Routing

There are two ways to implement conditional branching:

### Method 1: BranchComponent
A dedicated branching component where internal logic determines routing.

### Method 2: addConditionalConnection + router
Lighter weight, define routing directly on the edge:

```java
// BranchRouter
workflow.addConditionalConnection("src_id", new BranchRouter(...));

// Or Function routing
workflow.addConditionalConnection("src_id", (Function<Object, Object>) input -> {
    Map<String, String> map = (Map<String, String>) input;
    return switch (map.get("intent")) {
        case "transfer" -> "transfer_node";
        case "query" -> "query_node";
        default -> "default_node";
    };
});
```

**Router return value** must be a registered component id, otherwise routing fails.

## SubWorkflowComponentImpl

Wraps another Workflow as a node in the current graph:

```java
// Build sub-workflow
Workflow subWorkflow = new Workflow(subCard);
subWorkflow.setStartComp("sub_start", new Start(), Map.of("input", "${input}"), null);
// ... sub-workflow nodes and edges

// Wrap as parent workflow node
workflow.addWorkflowComp("sub", new SubWorkflowComponentImpl(subWorkflow),
        Map.of("input", "${start.input}"), null);
workflow.addConnection("start", "sub");
workflow.addConnection("sub", "end");
```

**Benefits**:
- Layered organization of complex processes
- Reusable common sub-processes
- Clearer visual structure

## Custom Components

When built-in components are insufficient, extend based on `WorkflowComponent` or `ComponentComposable`:

```java
public class MyComponent extends WorkflowComponent {
    public MyComponent() {
        super(ComponentAbility.INVOKE);  // Declare ability type
    }

    @Override
    public Object invoke(Map<String, Object> inputs, WorkflowSessionApi session) {
        // batch in, batch out
        String query = (String) inputs.get("query");
        return Map.of("result", process(query));
    }
}
```

**Implementation method depends on ability type**:
- `INVOKE` -> implement `invoke(...)`
- `STREAM` -> implement `stream(...)`, return `Iterator<WorkflowChunk>`
- `COLLECT` -> implement `collect(...)`, consume stream, return batch
- `TRANSFORM` -> implement `transform(...)`, consume stream, return stream

## Schema Reference Syntax Summary

| Syntax | Meaning | Used In |
| --- | --- | --- |
| `${query}` | Read from workflow top-level input | Any node's inputsSchema |
| `${start.query}` | Read from Start output | Subsequent nodes' inputsSchema |
| `${questioner.amount}` | Read from questioner output | Subsequent nodes' inputsSchema |
| `{{query}}` | Render variable | LLMComponent's templateContent / End's responseTemplate |

**Key**: `inputsSchema` only describes "where to get values" and **does not form execution order**; execution order is determined by `addConnection(...)`.

## Three Steps of Orchestration

Writing a Java workflow requires at least three things:

1. **Register nodes**: `setStartComp(...)` / `addWorkflowComp(...)` / `setEndComp(...)`
2. **Configure schema**: Let nodes know where to get values and what fields to expose downstream
3. **Establish edge relationships**: Let the executor know what path to schedule nodes along

Many beginners confuse step 2 with step 3. How to distinguish:
- `Map.of("amount", "${questioner.amount}")` resolves "which value to read"
- `addConnection("questioner", "end")` resolves "when to execute to end"

**Both are required**.
