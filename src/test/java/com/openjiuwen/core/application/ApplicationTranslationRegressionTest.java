package com.openjiuwen.core.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.application.llm.LlmAgent;
import com.openjiuwen.core.application.llm.LlmController;
import com.openjiuwen.core.application.llm.LlmEventHandler;
import com.openjiuwen.core.application.schema.ConstrainConfig;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.PluginSchema;
import com.openjiuwen.core.application.schema.ReActAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.application.workflow.WorkflowAgent;
import com.openjiuwen.core.application.workflow.WorkflowController;
import com.openjiuwen.core.application.workflow.WorkflowEventHandler;
import com.openjiuwen.core.application.workflow.WorkflowIntent;
import com.openjiuwen.core.common.constants.ControllerType;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.schema.Event;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationTranslationRegressionTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Test
    void workflowHandlerUsesStructuredQueryAndKeepsNamedArguments() throws Exception {
        WorkflowSchema workflow = WorkflowSchema.builder()
                .id("weather_flow")
                .name("WeatherFlow")
                .version("1.0")
                .inputParams(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string"))
                ))
                .build();
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("workflow-agent")
                .workflows(List.of(workflow))
                .build();
        WorkflowEventHandler handler = new WorkflowEventHandler(
                config,
                new ContextEngine(ContextEngineConfig.builder().build())
        );
        InputEvent event = InputEvent.fromUserInput(Map.of("query", "weather in Shanghai"));
        AgentSessionApi session = new AgentSessionApi("conversation-1");

        Method getDisplayContent = WorkflowEventHandler.class.getDeclaredMethod("getDisplayContent",
                com.openjiuwen.core.controller.schema.Event.class);
        getDisplayContent.setAccessible(true);
        assertEquals("weather in Shanghai", getDisplayContent.invoke(handler, event));

        Method createNewTask = WorkflowEventHandler.class.getDeclaredMethod(
                "createNewTask",
                com.openjiuwen.core.controller.schema.Event.class,
                WorkflowSchema.class,
                AgentSessionApi.class
        );
        createNewTask.setAccessible(true);

        Task task = (Task) createNewTask.invoke(handler, event, workflow, session);
        @SuppressWarnings("unchecked")
        Map<String, Object> arguments = (Map<String, Object>) task.getMetadata().get("arguments");

        assertEquals("conversation-1", task.getSessionId());
        assertEquals("weather in Shanghai", arguments.get("query"));
    }

    @Test
    void handlersExtractInteractiveInputFromStructuredMapInput() throws Exception {
        InteractiveInput interactiveInput = new InteractiveInput();
        interactiveInput.update("questioner", "yes");
        InputEvent event = InputEvent.fromUserInput(Map.of("query", interactiveInput));

        WorkflowEventHandler workflowHandler = new WorkflowEventHandler(
                WorkflowAgentConfig.builder()
                        .id("workflow-agent")
                        .workflows(List.of(WorkflowSchema.builder().id("wf").name("Workflow").build()))
                        .build(),
                new ContextEngine(ContextEngineConfig.builder().build())
        );
        LlmEventHandler llmHandler = new LlmEventHandler(
                LlmAgentConfig.builder().id("llm-agent").build(),
                new ContextEngine(ContextEngineConfig.builder().build())
        );

        Method workflowExtract = WorkflowEventHandler.class.getDeclaredMethod("extractInteractiveInput",
                com.openjiuwen.core.controller.schema.Event.class);
        workflowExtract.setAccessible(true);
        Method llmExtract = LlmEventHandler.class.getDeclaredMethod("extractInteractiveInput",
                com.openjiuwen.core.controller.schema.Event.class);
        llmExtract.setAccessible(true);

        assertSame(interactiveInput, workflowExtract.invoke(workflowHandler, event));
        assertSame(interactiveInput, llmExtract.invoke(llmHandler, event));
    }

    @Test
    void llmHandlerParsesToolArgumentsIntoTaskMetadata() throws Exception {
        WorkflowSchema workflow = WorkflowSchema.builder()
                .id("weather_flow")
                .name("WeatherFlow")
                .version("1.0")
                .build();
        LlmAgentConfig config = LlmAgentConfig.builder()
                .id("llm-agent")
                .workflows(List.of(workflow))
                .build();
        LlmEventHandler handler = new LlmEventHandler(
                config,
                new ContextEngine(ContextEngineConfig.builder().build())
        );
        AssistantMessage assistantMessage = AssistantMessage.builder()
                .content("")
                .toolCalls(List.of(ToolCall.builder()
                        .id("call-1")
                        .name("WeatherFlow")
                        .arguments("{\"city\":\"Shanghai\"}")
                        .build()))
                .build();

        Method parseLlmOutputToTasks = LlmEventHandler.class.getDeclaredMethod(
                "parseLlmOutputToTasks",
                AssistantMessage.class,
                AgentSessionApi.class
        );
        parseLlmOutputToTasks.setAccessible(true);

        @SuppressWarnings("unchecked")
        List<Task> tasks = (List<Task>) parseLlmOutputToTasks.invoke(handler, assistantMessage, new AgentSessionApi("s"));
        Object arguments = tasks.get(0).getMetadata().get("arguments");

        assertInstanceOf(Map.class, arguments);
        assertEquals("Shanghai", ((Map<?, ?>) arguments).get("city"));
    }

    @Test
    void schemaCompatibilitySupportsPythonStyleFieldsAndAliases() {
        LlmAgentConfig llmConfig = OBJECT_MAPPER.convertValue(Map.of(
                "id", "llm-agent",
                "controller_type", "react",
                "prompt_template_name", "react_system_prompt",
                "memory_scope_id", "memory-scope",
                "constrain", Map.of(
                        "reserved_max_chat_rounds", 12,
                        "max_iteration", 7
                )
        ), LlmAgentConfig.class);

        assertEquals(ControllerType.REACT_CONTROLLER, llmConfig.getControllerType());
        assertEquals("react_system_prompt", llmConfig.getPromptTemplateName());
        assertEquals("memory-scope", llmConfig.getMemoryScopeId());
        assertEquals(12, llmConfig.getConstrain().getReservedMaxChatRounds());
        assertEquals(7, llmConfig.getConstrain().getMaxIteration());
        assertEquals(12, llmConfig.getContextWindowLimit());

        WorkflowSchema workflowSchema = OBJECT_MAPPER.convertValue(Map.of(
                "id", "weather_flow",
                "name", "WeatherFlow",
                "inputs", Map.of("query", Map.of("type", "string"))
        ), WorkflowSchema.class);
        assertEquals("string", ((Map<?, ?>) workflowSchema.getInputs().get("query")).get("type"));

        String workflowJson;
        try {
            workflowJson = OBJECT_MAPPER.writeValueAsString(workflowSchema);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertTrue(workflowJson.contains("\"inputs\""));
        assertFalse(workflowJson.contains("inputParams"));

        PluginSchema pluginSchema = OBJECT_MAPPER.convertValue(Map.of(
                "id", "weather-tool",
                "version", "1.0",
                "plugin_id", "weather.tool",
                "inputs", Map.of("city", Map.of("type", "string"))
        ), PluginSchema.class);
        assertEquals("weather.tool", pluginSchema.getPluginId());
        assertTrue(pluginSchema.getInputs().containsKey("city"));

        WorkflowAgentConfig workflowConfig = OBJECT_MAPPER.convertValue(Map.of(
                "id", "workflow-agent",
                "controller_type", "workflow",
                "start_workflow", Map.of("id", "start"),
                "end_workflow", Map.of("id", "end"),
                "global_variables", List.of(Map.of("name", "language")),
                "global_params", Map.of("lang", "zh"),
                "constrain", Map.of(
                        "reserved_max_chat_rounds", 6,
                        "max_iteration", 2
                ),
                "default_response", Map.of(
                        "type", "text",
                        "text", "fallback"
                )
        ), WorkflowAgentConfig.class);

        assertEquals(ControllerType.WORKFLOW_CONTROLLER, workflowConfig.getControllerType());
        assertEquals("start", workflowConfig.getStartWorkflow().getId());
        assertEquals("end", workflowConfig.getEndWorkflow().getId());
        assertEquals("language", workflowConfig.getGlobalVariables().get(0).get("name"));
        assertEquals("zh", workflowConfig.getGlobalParams().get("lang"));
        assertEquals(6, workflowConfig.getConstrain().getReservedMaxChatRounds());
        assertEquals("text", workflowConfig.getDefaultResponse().getType());
        assertEquals("fallback", workflowConfig.getDefaultResponse().getText());

        ReActAgentConfig aliasConfig = ReActAgentConfig.builder()
                .id("alias-agent")
                .constrain(ConstrainConfig.builder().reservedMaxChatRounds(9).build())
                .build();
        assertEquals(ControllerType.REACT_CONTROLLER, aliasConfig.getControllerType());
        assertEquals(9, aliasConfig.getContextWindowLimit());
    }

    @Test
    void llmCompatibilityFacadeExposesLegacyHelpers() {
        LlmAgentConfig config = LlmAgent.createLlmAgentConfig(
                "llm-agent",
                "1.0",
                "legacy helper",
                List.of(),
                List.of(),
                null,
                List.of(Map.of("role", "system", "content", "initial prompt")),
                List.of("weather_tool")
        );

        assertEquals("llm-agent", config.getId());
        assertEquals(List.of("weather_tool"), config.getTools());

        LlmController controller = new LlmController(config, new ContextEngine(ContextEngineConfig.builder().build()));
        Event event = controller.createMessage(Map.of("content", "hello"));

        assertInstanceOf(InputEvent.class, event);

        controller.setLlmControllerPromptTemplate(List.of(Map.of("role", "system", "content", "updated prompt")));
        assertEquals("updated prompt", config.getPromptTemplate().get(0).get("content"));

        String utcTimestamp = "2026-03-16 00:00:00";
        String expected = LocalDateTime.parse(utcTimestamp, TIMESTAMP_FORMATTER)
                .atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(TIMESTAMP_FORMATTER);
        assertEquals(expected, LlmController.convertTimestamp(utcTimestamp));
        assertEquals("invalid", LlmController.convertTimestamp("invalid"));
    }

    @Test
    void workflowControllerSupportsSetupAndIntentDetection() {
        WorkflowSchema workflow = WorkflowSchema.builder()
                .id("weather_flow")
                .name("WeatherFlow")
                .version("1.0")
                .inputParams(new LinkedHashMap<>(Map.of(
                        "type", "object",
                        "properties", Map.of("query", Map.of("type", "string"))
                )))
                .build();
        WorkflowAgentConfig config = WorkflowAgentConfig.builder()
                .id("workflow-agent")
                .workflows(List.of(workflow))
                .build();
        WorkflowAgent agent = new WorkflowAgent(config);
        WorkflowController controller = new WorkflowController();
        controller.setupFromAgent(agent);

        WorkflowIntent intent = controller.intentDetection(
                controller.createMessage(Map.of("query", "weather in Shanghai")),
                new AgentSessionApi("conversation-compat")
        );

        assertEquals(config, controller.getAgentConfig());
        assertEquals(WorkflowIntent.Type.EXEC_NEW_TASK, intent.intentType());
        assertEquals("WeatherFlow", intent.workflow().getName());
        assertEquals("weather in Shanghai", ((Map<?, ?>) intent.task().getMetadata().get("arguments")).get("query"));
    }
}
