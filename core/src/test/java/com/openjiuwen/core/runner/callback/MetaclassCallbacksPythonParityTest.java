/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.ModelInvokeOptions;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessageChunk;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's metaclass-applied callback stacking tests in
 * {@code tests/unit_tests/core/runner/callback/test_metaclass_callbacks.py}.
 */
class MetaclassCallbacksPythonParityTest {

    @AfterEach
    void clearCallbackFrameworks() {
        Tool.clearCallbackFramework();
        Model.clearCallbackFramework();
    }

    @Test
    void triggerSkipsTransformCallbacks() {
        AsyncCallbackFramework framework = framework();
        registerTransform(framework, "test_event", "transform", kwargs -> str(kwargs.get("result")) + "-x");

        List<Object> results = framework.triggerResults("test_event", new Object[0], Map.of("result", "v"));

        assertThat(results).isEmpty();
    }

    @Test
    void toolInvokeEmitBeforeWithTransformedInput() throws Exception {
        AsyncCallbackFramework framework = framework();
        Tool.setCallbackFramework(framework);
        EchoTool tool = new EchoTool();
        List<Object> received = new ArrayList<>();
        registerTransform(framework, ToolCallEvents.TOOL_INVOKE_INPUT,
                "input_transform", kwargs -> new CallbackDecorators.BoundArgs(
                        new Object[]{input("transformed")},
                        Map.of()
                ));
        registerRegular(framework, ToolCallEvents.TOOL_INVOKE_INPUT,
                "record_input", kwargs -> received.add(valueArg(kwargs)));

        Object result = tool.invoke(input("original"));

        assertThat(received).containsExactly("transformed");
        assertThat(result).isEqualTo("transformed");
    }

    @Test
    void toolInvokeEmitAfterWithTransformedOutput() throws Exception {
        AsyncCallbackFramework framework = framework();
        Tool.setCallbackFramework(framework);
        EchoTool tool = new EchoTool();
        List<Object> received = new ArrayList<>();
        registerTransform(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "output_transform", kwargs -> str(kwargs.get("result")) + "-out");
        registerRegular(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "record_output", kwargs -> received.add(kwargs.get("result")));

        Object result = tool.invoke(input("hello"));

        assertThat(result).isEqualTo("hello-out");
        assertThat(received).containsExactly("hello-out");
    }

    @Test
    void toolInvokeBothTransformsApplied() throws Exception {
        AsyncCallbackFramework framework = framework();
        Tool.setCallbackFramework(framework);
        EchoTool tool = new EchoTool();
        List<Object> inputReceived = new ArrayList<>();
        List<Object> outputReceived = new ArrayList<>();
        registerTransform(framework, ToolCallEvents.TOOL_INVOKE_INPUT,
                "input_transform", kwargs -> new CallbackDecorators.BoundArgs(new Object[]{input("x")}, Map.of()));
        registerTransform(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "output_transform", kwargs -> str(kwargs.get("result")) + "!");
        registerRegular(framework, ToolCallEvents.TOOL_INVOKE_INPUT,
                "record_input", kwargs -> inputReceived.add(valueArg(kwargs)));
        registerRegular(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT,
                "record_output", kwargs -> outputReceived.add(kwargs.get("result")));

        Object result = tool.invoke(input("original"));

        assertThat(result).isEqualTo("x!");
        assertThat(inputReceived).containsExactly("x");
        assertThat(outputReceived).containsExactly("x!");
    }

    @Test
    void toolStreamEmitBeforeWithTransformedInput() throws Exception {
        AsyncCallbackFramework framework = framework();
        Tool.setCallbackFramework(framework);
        EchoTool tool = new EchoTool();
        List<Object> received = new ArrayList<>();
        registerTransform(framework, ToolCallEvents.TOOL_STREAM_INPUT,
                "input_transform", kwargs -> new CallbackDecorators.BoundArgs(
                        new Object[]{input("stream-transformed")},
                        Map.of()
                ));
        registerRegular(framework, ToolCallEvents.TOOL_STREAM_INPUT,
                "record_input", kwargs -> received.add(valueArg(kwargs)));

        List<Object> items = toList(tool.stream(input("original")));

        assertThat(received).containsExactly("stream-transformed");
        assertThat(items).containsExactly("stream-transformed");
    }

    @Test
    void toolStreamEmitAfterPerItemWithTransformedOutput() throws Exception {
        AsyncCallbackFramework framework = framework();
        Tool.setCallbackFramework(framework);
        EchoTool tool = new EchoTool();
        List<Object> received = new ArrayList<>();
        registerTransform(framework, ToolCallEvents.TOOL_STREAM_OUTPUT,
                "output_transform", kwargs -> str(kwargs.get("result")) + "-item");
        registerRegular(framework, ToolCallEvents.TOOL_STREAM_OUTPUT,
                "record_output", kwargs -> received.add(kwargs.get("result")));

        List<Object> items = toList(tool.stream(input("chunk")));

        assertThat(items).containsExactly("chunk-item");
        assertThat(received).containsExactly("chunk-item");
    }

    @Test
    void workflowInvokeCallbacks() {
        AsyncCallbackFramework framework = framework();
        EchoWorkflow workflow = new EchoWorkflow(framework);
        List<Object> received = new ArrayList<>();
        registerTransform(framework, WorkflowEvents.WORKFLOW_INVOKE_OUTPUT,
                "output_transform", kwargs -> str(kwargs.get("result")) + "-wf");
        registerRegular(framework, WorkflowEvents.WORKFLOW_INVOKE_OUTPUT,
                "record_output", kwargs -> received.add(kwargs.get("result")));

        Object result = workflow.invoke("data");

        assertThat(result).isEqualTo("data-wf");
        assertThat(received).containsExactly("data-wf");
    }

    @Test
    void workflowStreamCallbacks() {
        AsyncCallbackFramework framework = framework();
        EchoWorkflow workflow = new EchoWorkflow(framework);
        List<Object> received = new ArrayList<>();
        registerTransform(framework, WorkflowEvents.WORKFLOW_STREAM_OUTPUT,
                "output_transform", kwargs -> str(kwargs.get("result")) + "-wf");
        registerRegular(framework, WorkflowEvents.WORKFLOW_STREAM_OUTPUT,
                "record_output", kwargs -> received.add(kwargs.get("result")));

        List<Object> items = toList(workflow.stream("data"));

        assertThat(items).containsExactly("data-wf");
        assertThat(received).containsExactly("data-wf");
    }

    @Test
    void modelInvokeCallbacks() {
        AsyncCallbackFramework framework = framework();
        Model.setCallbackFramework(framework);
        Model model = echoModel();
        List<Object> received = new ArrayList<>();
        registerTransform(framework, LLMCallEvents.LLM_INVOKE_OUTPUT,
                "output_transform", kwargs -> new AssistantMessage(content(kwargs.get("result")) + "-extra"));
        registerRegular(framework, LLMCallEvents.LLM_INVOKE_OUTPUT,
                "record_output", kwargs -> received.add(content(kwargs.get("result"))));

        AssistantMessage result = model.invoke(List.of(new UserMessage("msg"))).toCompletableFuture().join();

        assertThat(result.getContentAsString()).isEqualTo("msg-extra");
        assertThat(received).containsExactly("msg-extra");
    }

    @Test
    void modelStreamCallbacks() {
        AsyncCallbackFramework framework = framework();
        Model.setCallbackFramework(framework);
        Model model = echoModel();
        List<Object> received = new ArrayList<>();
        registerTransform(framework, LLMCallEvents.LLM_STREAM_OUTPUT,
                "output_transform", kwargs -> chunk(content(kwargs.get("result")) + "-chunk"));
        registerRegular(framework, LLMCallEvents.LLM_STREAM_OUTPUT,
                "record_output", kwargs -> received.add(content(kwargs.get("result"))));

        List<String> items = toList(model.stream(List.of(new UserMessage("msg"))))
                .stream()
                .map(MetaclassCallbacksPythonParityTest::content)
                .toList();

        assertThat(items).containsExactly("msg-chunk");
        assertThat(received).containsExactly("msg-chunk");
    }

    @Test
    void triggerAndTransformFireInOrder() throws Exception {
        AsyncCallbackFramework framework = framework();
        Tool.setCallbackFramework(framework);
        EchoTool tool = new EchoTool();
        List<String> order = new ArrayList<>();
        registerTransform(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT, "output_transform", kwargs -> {
            order.add("transform:" + kwargs.get("result"));
            return str(kwargs.get("result")) + "-T";
        });
        registerRegular(framework, ToolCallEvents.TOOL_INVOKE_OUTPUT, "normal_output", kwargs -> {
            order.add("trigger:" + kwargs.get("result"));
            return null;
        });

        tool.invoke(input("v"));

        assertThat(order).containsExactly("transform:v", "trigger:v-T");
    }

    @Test
    void modelInvokeExtraKwargsInjected() {
        AsyncCallbackFramework framework = framework();
        Model.setCallbackFramework(framework);
        Model model = echoModel();
        Map<String, Object> captured = new LinkedHashMap<>();
        registerRegular(framework, LLMCallEvents.LLM_INVOKE_INPUT, "record_kwargs", kwargs -> {
            captured.putAll(kwargs);
            return null;
        });

        model.invoke(List.of(new UserMessage("msg"))).toCompletableFuture().join();

        assertThat(captured).containsEntry("model_config", model.getModelConfig());
        assertThat(captured).containsEntry("model_client_config", model.getModelClientConfig());
    }

    @Test
    void modelStreamExtraKwargsInjected() {
        AsyncCallbackFramework framework = framework();
        Model.setCallbackFramework(framework);
        Model model = echoModel();
        Map<String, Object> captured = new LinkedHashMap<>();
        registerRegular(framework, LLMCallEvents.LLM_STREAM_OUTPUT, "record_kwargs", kwargs -> {
            captured.putAll(kwargs);
            return null;
        });

        toList(model.stream(List.of(new UserMessage("msg"))));

        assertThat(captured).containsEntry("model_config", model.getModelConfig());
        assertThat(captured).containsEntry("model_client_config", model.getModelClientConfig());
    }

    private static AsyncCallbackFramework framework() {
        return new AsyncCallbackFramework(false, false);
    }

    private static void registerRegular(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        framework.on(event).apply(named(name, callback));
    }

    private static void registerTransform(
            AsyncCallbackFramework framework,
            String event,
            String name,
            Function<Map<String, Object>, Object> callback
    ) {
        framework.onTransform(event, 0).apply(named(name, callback));
    }

    private static Function<Map<String, Object>, Object> named(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) {
        return new NamedCallback(name, delegate);
    }

    private static Map<String, Object> input(String value) {
        return Map.of("value", value);
    }

    private static Object valueArg(Map<String, Object> kwargs) {
        Object value = arg(kwargs, 0);
        if (value instanceof Map<?, ?> map) {
            return map.get("value");
        }
        return value;
    }

    private static Object arg(Map<String, Object> kwargs, int index) {
        Object value = kwargs.get("_args");
        Object[] args = value instanceof Object[] values ? values : new Object[0];
        return args[index];
    }

    private static String str(Object value) {
        return String.valueOf(value);
    }

    private static String content(Object value) {
        if (value instanceof AssistantMessage message) {
            return message.getContentAsString();
        }
        if (value instanceof AssistantMessageChunk chunk) {
            return chunk.getContentAsString();
        }
        return String.valueOf(value);
    }

    private static AssistantMessageChunk chunk(String content) {
        return AssistantMessageChunk.builder().content(content).build();
    }

    private static <T> List<T> toList(Iterator<T> iterator) {
        List<T> values = new ArrayList<>();
        iterator.forEachRemaining(values::add);
        return values;
    }

    private static Model echoModel() {
        ModelRequestConfig modelConfig = ModelRequestConfig.builder().build();
        ModelClientConfig clientConfig = ModelClientConfig.builder()
                .clientProvider("OpenAI")
                .apiKey("test-key")
                .apiBase("https://example.com")
                .build();
        return new Model(new EchoModelClient(), clientConfig, modelConfig);
    }

    /**
     * Mirrors Python's local callback functions in
     * {@code tests/unit_tests/core/runner/callback/test_metaclass_callbacks.py}.
     */
    private record NamedCallback(
            String name,
            Function<Map<String, Object>, Object> delegate
    ) implements Function<Map<String, Object>, Object> {

        @Override
        public Object apply(Map<String, Object> kwargs) {
            return delegate.apply(kwargs);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * Mirrors Python's {@code _EchoTool} in
     * {@code tests/unit_tests/core/runner/callback/test_metaclass_callbacks.py}.
     */
    private static final class EchoTool extends Tool {
        private EchoTool() {
            super(ToolCard.builder()
                    .id("echo-tool")
                    .name("echo-tool")
                    .inputParams(Map.of("type", "object"))
                    .build());
        }

        @Override
        protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return inputs.get("value");
        }

        @Override
        protected Iterator<Object> streamInternal(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of(inputs.get("value")).iterator();
        }
    }

    /**
     * Mirrors Python's {@code _EchoWorkflow} callback-wrapped invoke/stream shape in
     * {@code tests/unit_tests/core/runner/callback/test_metaclass_callbacks.py}.
     */
    private static final class EchoWorkflow {
        private final AsyncCallbackFramework framework;

        private EchoWorkflow(AsyncCallbackFramework framework) {
            this.framework = framework;
        }

        private Object invoke(Object input) {
            Object resolvedInput = transformInput(WorkflowEvents.WORKFLOW_INVOKE_INPUT, input);
            framework.trigger(WorkflowEvents.WORKFLOW_INVOKE_INPUT, new Object[]{resolvedInput}, Map.of());
            Object result = resolvedInput;
            Object resolvedOutput = transformOutput(WorkflowEvents.WORKFLOW_INVOKE_OUTPUT, result);
            framework.trigger(WorkflowEvents.WORKFLOW_INVOKE_OUTPUT, new Object[0],
                    Map.of("result", resolvedOutput));
            return resolvedOutput;
        }

        private Iterator<Object> stream(Object input) {
            Object resolvedInput = transformInput(WorkflowEvents.WORKFLOW_STREAM_INPUT, input);
            framework.trigger(WorkflowEvents.WORKFLOW_STREAM_INPUT, new Object[]{resolvedInput}, Map.of());
            return new Iterator<>() {
                private boolean consumed;

                @Override
                public boolean hasNext() {
                    return !consumed;
                }

                @Override
                public Object next() {
                    consumed = true;
                    Object resolvedOutput = transformOutput(WorkflowEvents.WORKFLOW_STREAM_OUTPUT, resolvedInput);
                    framework.trigger(WorkflowEvents.WORKFLOW_STREAM_OUTPUT, new Object[0],
                            Map.of("result", resolvedOutput));
                    return resolvedOutput;
                }
            };
        }

        private Object transformInput(String event, Object input) {
            Object transformed = framework.triggerTransform(event, new Object[]{input}, Map.of());
            if (transformed instanceof CallbackDecorators.BoundArgs boundArgs && boundArgs.getArgs().length > 0) {
                return boundArgs.getArgs()[0];
            }
            return transformed == CallbackDecorators.TRANSFORM_NOOP ? input : transformed;
        }

        private Object transformOutput(String event, Object output) {
            Object transformed = framework.triggerTransform(event, new Object[0], Map.of("result", output));
            return transformed == CallbackDecorators.TRANSFORM_NOOP ? output : transformed;
        }
    }

    /**
     * Mirrors Python's {@code _EchoModelClient} in
     * {@code tests/unit_tests/core/runner/callback/test_metaclass_callbacks.py}.
     */
    private static final class EchoModelClient implements Model.ModelClient {

        @Override
        public CompletionStage<AssistantMessage> invoke(List<BaseMessage> messages, ModelInvokeOptions options) {
            return CompletableFuture.completedFuture(new AssistantMessage("msg"));
        }

        @Override
        public Iterator<AssistantMessageChunk> stream(List<BaseMessage> messages, ModelInvokeOptions options) {
            return List.of(chunk("msg")).iterator();
        }
    }
}
