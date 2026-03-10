package com.openjiuwen.core.application;

import com.openjiuwen.core.application.llm.LlmEventHandler;
import com.openjiuwen.core.application.schema.LlmAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowAgentConfig;
import com.openjiuwen.core.application.schema.WorkflowSchema;
import com.openjiuwen.core.application.workflow.WorkflowEventHandler;
import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.schema.ContextEngineConfig;
import com.openjiuwen.core.controller.schema.InputEvent;
import com.openjiuwen.core.controller.schema.Task;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

class ApplicationTranslationRegressionTest {

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
}
