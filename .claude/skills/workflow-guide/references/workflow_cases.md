# Workflow Real-World Cases

This file provides 4 end-to-end cases, each containing "scenario -> workflow graph -> key code -> execution flow -> notes". Read on demand when users ask "give me a workflow case" or "how to build a certain type of workflow".

## Case 1: Financial Assistant Multi-Workflow (WorkflowAgent + Intent Routing)

**Corresponds to**: `examples/workflow_agent/WorkflowAgentExampleSupport.java`

### Scenario

A financial assistant hosts 3 workflows: transfer / investment / balance inquiry. When the user says "I want to transfer", the transfer flow is entered; when they say "check balance", the balance flow is entered. Each workflow has a follow-up question step (asking for amount/product/account when missing).

### Workflow Graph

```
User Input
   |
   v
WorkflowAgent (intent recognition)
   |-- "transfer" --> transfer_flow_multi: Start -> Questioner(amount) -> End
   |-- "investment" --> invest_flow_multi:    Start -> Questioner(product) -> End
   |-- "check balance" -> balance_flow:        Start -> Questioner(account) -> End
   +-- other --> defaultResponse
```

### Key Code

**Create agent + register 3 workflows**:

```java
WorkflowAgentConfig config = WorkflowAgentConfig.builder()
        .id("workflow_agent_java_example")
        .description("Java multi-workflow financial assistant example")
        .model(modelConfig)
        .promptTemplate(List.of(Map.of("role", "system", "content",
            "You are a financial business assistant. When users request transfers, "
            + "investments, or balance inquiries, you must select the most appropriate "
            + "workflow to handle it. If information is incomplete, use the questioning "
            + "node within the workflow to collect missing fields. If the user's request "
            + "does not belong to these three business categories, return the default reply directly.")))
        .defaultResponse(DefaultResponse.builder()
            .text("I currently only support three financial processes: transfers, investments, and balance inquiries. Please clearly state your request.")
            .build())
        .build();

WorkflowAgent agent = new WorkflowAgent(config);
agent.addWorkflows(List.of(
        buildFinancialWorkflow("transfer_flow_multi", "Transfer Service",
                "Handle user transfer, remittance, payment requests...", "amount",
                "Transfer amount", "Transfer service completed, amount is {{amount}}."),
        buildFinancialWorkflow("invest_flow_multi", "Investment Service",
                "Handle investment requests...", "product",
                "Investment product name", "Investment service completed, product is {{product}}."),
        buildFinancialWorkflow("balance_flow", "Balance Inquiry",
                "Handle account balance inquiry requests...", "account",
                "Account number", "Balance inquiry completed, account is {{account}}.")
));
```

**Building each workflow** (using a helper method for reuse):

```java
private static Workflow buildFinancialWorkflow(String id, String name,
        String desc, String fieldName, String fieldDesc, String template) {
    WorkflowCard card = WorkflowCard.builder()
            .id(id).name(name).version("1.0").description(desc)
            .inputParams(defaultInputSchema()).build();

    QuestionerConfig qConfig = new QuestionerConfig();
    qConfig.setModelClientConfig(clientConfig);
    qConfig.setModelConfig(requestConfig);
    qConfig.setQuestionContent("Please provide " + fieldDesc);
    qConfig.setExtractFieldsFromResponse(true);
    qConfig.setFieldNames(List.of(FieldInfo.builder()
            .fieldName(fieldName).description(fieldDesc).required(true).build()));
    qConfig.setWithChatHistory(false);
    qConfig.setMaxResponse(10);

    Workflow wf = new Workflow(card);
    wf.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
    wf.addWorkflowComp("questioner", new QuestionerComponent(qConfig),
            Map.of("query", "${start.query}"), null);
    wf.setEndComp("end", new End(Map.of("responseTemplate", template)),
            Map.of(fieldName, "${questioner." + fieldName + "}"), null);
    wf.addConnection("start", "questioner");
    wf.addConnection("questioner", "end");
    return wf;
}
```

### Execution Flow

1. User inputs "I want to transfer 2000 yuan"
2. `WorkflowEventHandler.intentDetection(...)` performs intent recognition -> matches `transfer_flow_multi`
3. Executes transfer workflow: Start -> Questioner (amount already in input, no follow-up needed) -> End
4. Returns `COMPLETED`, output "Transfer service completed, amount is 2000 yuan"

If the user only says "I want to transfer" (no amount):
1. Intent recognition matches transfer workflow
2. Start -> Questioner (amount missing) -> returns `INPUT_REQUIRED`
3. User answers "2000 yuan" -> `InteractiveInput.update("questioner", "2000 yuan")`
4. Resume execution in same session -> Questioner extracts amount -> End -> `COMPLETED`

### Notes

- **Write clear workflow descriptions**: Intent recognition relies on description matching; vague descriptions will lead to defaultResponse
- **defaultResponse must be set**: When intent is not matched, return a default reply rather than forcing a wrong workflow selection
- **Reuse the same session when resuming**: Switching sessions will lose follow-up context
- **`reply.update` component id must be `"questioner"`**: Must match the id used at registration

---

## Case 2: Streaming Q&A Assistant (LLMComponent + End streaming)

**Corresponds to**: Simplified version of `examples/interact/WeatherAssistantInteractExampleSupport.java`

### Scenario

User asks a question, LLM streams the answer, frontend displays output incrementally.

### Workflow Graph

```
Start --batch--> LLMComponent --stream--> End(streaming)
                    |                          |
                    | LLM produces chunks      | End forwards chunks
                    v                          v
               Iterator<WorkflowChunk> <- caller consumes
```

### Key Code

```java
LLMCompConfig llmConfig = new LLMCompConfig();
llmConfig.setModelClientConfig(clientConfig);
llmConfig.setModelConfig(requestConfig);
llmConfig.setTemplateContent(List.of(
        Map.of("role", "system", "content", "You are a concise assistant."),
        Map.of("role", "user", "content", "{{query}}")  // {{}} renders
));
llmConfig.setResponseFormat(Map.of("type", "text"));
llmConfig.setOutputConfig(Map.of(
        "answer", Map.of("type", "string", "description", "Model answer", "required", true)
));

Workflow workflow = new Workflow();
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
workflow.addWorkflowComp("llm", new LLMComponent(llmConfig),
        Map.of("query", "${start.query}"), null);

// End uses streaming mode; the 5th parameter is streamInputsSchema
workflow.setEndComp("end",
        new End(Map.of("responseTemplate", "{{answer}}")),
        null,                                    // inputsSchema (batch, not used here)
        null,                                    // outputsSchema
        Map.of("answer", "${llm.answer}"),       // streamInputsSchema (streaming)
        null,
        "streaming");                            // responseMode

// Connect edges: start->llm uses normal edge, llm->end uses streaming edge
workflow.addConnection("start", "llm");
workflow.addStreamConnection("llm", "end");

// Execute
Iterator<WorkflowChunk> chunks = workflow.stream(
        Map.of("query", "Introduce Java workflows"),
        WorkflowSessions.createWorkflowSession(),
        null,
        List.of(StreamMode.OUTPUT));

while (chunks.hasNext()) {
    WorkflowChunk chunk = chunks.next();
    if (chunk instanceof OutputSchema output) {
        System.out.print(output.getPayload());  // Print incrementally
    }
}
```

### Execution Flow

1. `stream(...)` returns a synchronous Iterator
2. LLMComponent produces tokens/chunks incrementally
3. Streaming edge passes chunks to End
4. End outputs chunks incrementally in streaming mode
5. Caller iterates the Iterator, consuming chunks

### Notes

- **End streaming mode must use `streamInputsSchema`** (5th parameter), not `inputsSchema` (3rd parameter)
- **`addStreamConnection` must not be omitted**: Only `addConnection` will not make streaming data flow
- **`StreamMode.OUTPUT` must be passed**: Not passing or passing wrong will result in no data
- **Iterator is synchronous**: Not an async stream; `hasNext()` blocks waiting for the next chunk

---

## Case 3: Form Filling with Follow-up Questions (Multi-field QuestionerComponent)

### Scenario

User wants to apply for a credit card, needs to fill in 4 fields: name, ID number, phone, and email. The user may only provide some fields; QuestionerComponent asks follow-up questions for missing fields.

### Workflow Graph

```
Start --> Questioner(multi-field) --> End
              |
              | Fields missing -> INPUT_REQUIRED
              | All fields present -> continue execution
              v
         User reply -> resume in same session
```

### Key Code

```java
QuestionerConfig config = new QuestionerConfig();
config.setModelClientConfig(clientConfig);
config.setModelConfig(requestConfig);
config.setQuestionContent("Please provide the following information");
config.setExtractFieldsFromResponse(true);
config.setFieldNames(List.of(
    FieldInfo.builder().fieldName("name")
        .description("Name").required(true).build(),
    FieldInfo.builder().fieldName("idCard")
        .description("ID number").required(true).build(),
    FieldInfo.builder().fieldName("phone")
        .description("Phone number").required(true).build(),
    FieldInfo.builder().fieldName("email")
        .description("Email").required(false).build()  // Optional
));
config.setWithChatHistory(true);   // Multi-turn follow-up requires chat history
config.setMaxResponse(10);         // Maximum 10 follow-up rounds

Workflow workflow = new Workflow(card);
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);
workflow.addWorkflowComp("questioner", new QuestionerComponent(config),
        Map.of("query", "${start.query}"), null);
workflow.setEndComp("end",
        new End(Map.of("responseTemplate",
            "Credit card application submitted: {{name}} / {{idCard}} / {{phone}} / {{email}}")),
        Map.of(
            "name", "${questioner.name}",
            "idCard", "${questioner.idCard}",
            "phone", "${questioner.phone}",
            "email", "${questioner.email}"
        ),
        null);
workflow.addConnection("start", "questioner");
workflow.addConnection("questioner", "end");
```

### Execution Flow

1. User: "I want to apply for a credit card, my name is Zhang San"
2. Start -> Questioner: extracts name=Zhang San, but idCard/phone/email are missing
3. Returns `INPUT_REQUIRED`, prompts user for ID number
4. User: "110101199001011234"
5. Resume in same session -> Questioner: extracts idCard, phone/email still missing
6. Returns `INPUT_REQUIRED`, prompts for phone number
7. User: "13800138000"
8. Resume -> extracts phone, email is optional -> all required fields present
9. -> End -> `COMPLETED`, output "Credit card application submitted: Zhang San / 110101... / 138... / null"

### Notes

- **`setWithChatHistory(true)`**: Multi-turn follow-up must include chat history, otherwise Questioner does not know what was previously asked
- **`setMaxResponse(10)`**: Limits maximum follow-up rounds to prevent infinite loops
- **Optional fields (`required=false`)**: Missing optional fields do not trigger follow-up, but `{{email}}` in the End template will render as null
- **Each `InteractiveInput.update` answers one question at a time**: Even for multi-field follow-up, one is answered at a time; Questioner decides what to ask next

---

## Case 4: Conditional Branching Workflow (BranchComponent + Multi-branch Routing)

### Scenario

Customer service bot: user says "refund" goes to refund flow, says "inquiry" goes to inquiry flow, says "complaint" goes to complaint flow.

### Workflow Graph

```
                Start
                  |
                  v
          IntentDetection
                  |
            (conditional edge routing)
              +---+---+
              v   v   v
          Refund  Inquiry  Complaint
          Node    Node     Node
              +---+---+
                  v
                 End
```

### Key Code

```java
Workflow workflow = new Workflow(card);
workflow.setStartComp("start", new Start(), Map.of("query", "${query}"), null);

// Intent detection node
IntentDetectionConfig intentConfig = new IntentDetectionConfig();
intentConfig.setModelClientConfig(clientConfig);
intentConfig.setModelConfig(requestConfig);
intentConfig.setIntents(List.of(
    IntentInfo.builder().name("refund").description("Refund, return, money back").build(),
    IntentInfo.builder().name("consult").description("Inquiry, ask, learn about").build(),
    IntentInfo.builder().name("complain").description("Complaint, report, dissatisfaction").build()
));
workflow.addWorkflowComp("intent", new IntentDetectionComponent(intentConfig),
        Map.of("query", "${start.query}"), null);

// Three business nodes
workflow.addWorkflowComp("refund", new RefundComponent(),
        Map.of("query", "${start.query}"), null);
workflow.addWorkflowComp("consult", new ConsultComponent(),
        Map.of("query", "${start.query}"), null);
workflow.addWorkflowComp("complain", new ComplainComponent(),
        Map.of("query", "${start.query}"), null);

// End
workflow.setEndComp("end", new End(Map.of("responseTemplate", "{{result}}")),
        Map.of("result", "${refund.result}",  // All three nodes map to result
                      "result", "${consult.result}",
                      "result", "${complain.result}"),
        null);

// Connect edges: start -> intent uses normal edge
workflow.addConnection("start", "intent");

// intent -> three branches use conditional edge (Function routing)
workflow.addConditionalConnection("intent", (Function<Object, Object>) input -> {
    String intentName = ((Map<String, String>) input).get("intentName");
    return switch (intentName) {
        case "refund" -> "refund";
        case "consult" -> "consult";
        case "complain" -> "complain";
        default -> "consult";  // Fallback
    };
});

// Three branches -> end use normal edges
workflow.addConnection("refund", "end");
workflow.addConnection("consult", "end");
workflow.addConnection("complain", "end");
```

### Execution Flow

1. User: "I want a refund"
2. Start -> IntentDetection: recognizes intent=refund
3. Conditional edge routing: intent=refund -> goes to refund node
4. RefundComponent executes refund logic
5. -> End -> `COMPLETED`

### Notes

- **Router return value must be a registered component id**: Returning `"refund"` requires `addWorkflowComp("refund", ...)`
- **Fallback branch must exist**: Intent recognition may not match; default should go to a safe branch
- **Multiple branches converge to End**: End's inputsSchema must map all three nodes' result to the `result` field
- **Conditional edge does not correspond to `ConnectionType`**: It is expressed via `addConditionalConnection` + router, not a separate edge type

## Common Flow Across Cases

Regardless of the workflow type, the building process is largely the same:

1. **Define card**: `WorkflowCard` describes identity and input schema
2. **Register nodes**: `setStartComp` / `addWorkflowComp` / `setEndComp`
3. **Configure schema**: Each node uses `${...}` to declare where to get values
4. **Connect edges**: `addConnection` (normal) / `addStreamConnection` (streaming) / `addConditionalConnection` (conditional)
5. **Create session**: `WorkflowSessions.createWorkflowSession(...)`
6. **Execute**: `invoke(...)` for batch or `stream(...)` for streaming
7. **Handle state**: `COMPLETED` read result; `INPUT_REQUIRED` reply and resume in same session; `ERROR` handle exception

After each `invoke` or `stream`, always check `WorkflowExecutionState` to determine the next action.
