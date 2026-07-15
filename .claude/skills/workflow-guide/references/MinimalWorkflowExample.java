/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package references;

import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.session.WorkflowSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.workflow.Workflow;
import com.openjiuwen.core.workflow.WorkflowCard;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;
import com.openjiuwen.core.workflow.WorkflowSessions;
import com.openjiuwen.core.workflow.component.End;
import com.openjiuwen.core.workflow.component.Start;
import com.openjiuwen.core.workflow.component.llm.FieldInfo;
import com.openjiuwen.core.workflow.component.llm.QuestionerComponent;
import com.openjiuwen.core.workflow.component.llm.QuestionerConfig;

import java.util.List;
import java.util.Map;

/**
 * Minimal runnable workflow building example.
 *
 * Prerequisites (all required, otherwise it will not run):
 *   1. Move this file or the entire examples/ directory under src/main/java/
 *      so it enters the Maven compilation path.
 *   2. Fill in real values in src/main/resources/apiconfig.json
 *      (API_BASE / API_KEY / MODEL_PROVIDER / MODEL_NAME).
 *      You can also override via -Dopenjiuwen.example.config=<path>
 *      or the OPENJIUWEN_API_CONFIG environment variable.
 *
 * Run command:
 *   mvn exec:java -Dexec.mainClass=references.MinimalWorkflowExample
 *
 * Or right-click the main method in your IDE to run directly.
 *
 * This example aligns with the single-workflow path in
 * examples/workflow_agent/WorkflowAgentExampleSupport.java,
 * but removes WorkflowAgent hosting, multi-workflow routing,
 * console interaction loop, and other distracting logic,
 * keeping only the main line of "define card -> register nodes ->
 * connect edges -> invoke -> handle INPUT_REQUIRED -> finish",
 * making it easy for beginners to understand the minimal runnable
 * form of a single workflow.
 *
 * Workflow graph:
 *   Start -> QuestionerComponent(ask for amount) -> End
 *
 * Execution flow:
 *   1. User inputs "I want to transfer" (amount missing)
 *   2. Start -> Questioner: amount missing, returns INPUT_REQUIRED
 *   3. Code auto-replies "2000 yuan" -> resume in same session
 *   4. Questioner extracts amount -> End -> COMPLETED
 *   5. Output "Transfer service completed, amount is 2000 yuan."
 */
public final class MinimalWorkflowExample {

    private MinimalWorkflowExample() {
    }

    public static void main(String[] args) throws Exception {
        // 1. Model configuration (in real projects, typically use SharedExampleApiConfigLoader to read apiconfig.json)
        ModelClientConfig clientConfig = createModelClientConfig();
        ModelRequestConfig requestConfig = createModelRequestConfig();

        // 2. Define WorkflowCard (workflow identity and input schema)
        WorkflowCard card = WorkflowCard.builder()
                .id("transfer_flow")
                .name("Transfer Service")
                .version("1.0")
                .description("Collect missing transfer amount and return final result")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "query", Map.of("type", "string", "description", "User input")
                        ),
                        "required", List.of("query")
                ))
                .build();

        // 3. Create Workflow and register nodes
        Workflow workflow = new Workflow(card);

        // 3.1 Start node: pass through top-level input
        workflow.setStartComp(
                "start",
                new Start(),
                Map.of("query", "${query}"),   // ${query} reads from top-level input
                null
        );

        // 3.2 QuestionerComponent: ask follow-up for missing amount field
        QuestionerConfig questionerConfig = new QuestionerConfig();
        questionerConfig.setModelClientConfig(clientConfig);
        questionerConfig.setModelConfig(requestConfig);
        questionerConfig.setQuestionContent("Please provide the transfer amount. It must be a number or an amount description with a currency unit.");
        questionerConfig.setExtractFieldsFromResponse(true);
        questionerConfig.setFieldNames(List.of(
                FieldInfo.builder()
                        .fieldName("amount")
                        .description("Transfer amount, must be a number or an amount description with a currency unit.")
                        .required(true)
                        .build()
        ));
        questionerConfig.setWithChatHistory(false);
        questionerConfig.setMaxResponse(10);

        workflow.addWorkflowComp(
                "questioner",                                       // Component id, must be consistent when resuming interaction
                new QuestionerComponent(questionerConfig),
                Map.of("query", "${start.query}"),                  // ${start.query} reads from Start output
                null
        );

        // 3.3 End node: render final output using responseTemplate
        workflow.setEndComp(
                "end",
                new End(Map.of("responseTemplate", "Transfer service completed, recorded transfer amount is {{amount}}.")),
                Map.of("amount", "${questioner.amount}"),           // ${questioner.amount} reads from questioner output
                null
        );

        // 4. Connect edges (registering nodes does not form execution order; edges must be connected)
        workflow.addConnection("start", "questioner");
        workflow.addConnection("questioner", "end");

        // 5. Create session and execute
        //    Note: When resuming execution, you must reuse the same session;
        //    switching sessions will lose state
        WorkflowSessionApi session = WorkflowSessions.createWorkflowSession("conversation-001");

        // 6. First invoke: user inputs "I want to transfer" (amount missing)
        String userQuery = args.length > 0 ? args[0] : "I want to transfer";
        System.out.println(">>> User input: " + userQuery);

        WorkflowOutput output = workflow.invoke(
                Map.of("query", userQuery),
                session,
                null
        );

        // 7. Handle INPUT_REQUIRED: Questioner asks for the amount
        if (WorkflowExecutionState.INPUT_REQUIRED.equals(output.getState())) {
            System.out.println(">>> Workflow requires additional input (state=INPUT_REQUIRED)");

            String reply = args.length > 1 ? args[1] : "2000 yuan";
            System.out.println(">>> Reply: " + reply);

            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("questioner", reply);   // "questioner" must match the component id used at registration

            // Resume execution in the same session
            output = workflow.invoke(interactiveInput, session, null);
        }

        // 8. Check final state
        if (WorkflowExecutionState.COMPLETED.equals(output.getState())) {
            System.out.println(">>> Workflow completed (state=COMPLETED)");
            System.out.println(">>> Result: " + output.getResult());
        } else if (WorkflowExecutionState.ERROR.equals(output.getState())) {
            System.out.println(">>> Workflow error (state=ERROR)");
            System.out.println(">>> Result: " + output.getResult());
        } else {
            System.out.println(">>> Workflow state: " + output.getState());
            System.out.println(">>> Result: " + output.getResult());
        }
    }

    /**
     * Model client configuration.
     * In real projects, use SharedExampleApiConfigLoader to read from apiconfig.json.
     * Here parameters are passed directly for a self-contained example.
     */
    private static ModelClientConfig createModelClientConfig() {
        // Replace with your real values
        return ModelClientConfig.builder()
                .provider("your-provider")           // MODEL_PROVIDER, e.g. openai / azure / qwen
                .apiKey("your-api-key")              // API_KEY
                .apiBaseUrl("https://your-api-base") // API_BASE
                .build();
    }

    /**
     * Model request parameter configuration.
     */
    private static ModelRequestConfig createModelRequestConfig() {
        ModelRequestConfig config = new ModelRequestConfig();
        config.setModelName("your-model-name");     // MODEL_NAME
        config.setTemperature(0.2);
        config.setTopP(0.9);
        return config;
    }
}
