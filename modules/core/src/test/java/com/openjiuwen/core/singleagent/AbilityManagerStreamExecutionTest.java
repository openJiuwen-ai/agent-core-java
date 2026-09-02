/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.function.LocalFunction;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.Session;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * Execution-count regression tests for the streaming tool path of
 * {@link AbilityManager#executeStream}.
 * <p>
 * Guards GitCode issue #124: {@code LocalFunction.stream()} used to signal "not streaming"
 * by throwing <em>after</em> the wrapped function had already run, and
 * {@code executeStreamingTool} caught that error and fell back to {@code invoke()}, running
 * the function a second time. Tools with side effects (writes, payments) were executed twice.
 * <p>
 * These tests drive the private {@code executeStreamingTool} helper by reflection, which keeps
 * them free of the Runner / ResourceMgr bootstrap that a full {@code executeStream} call
 * would need.
 */
class AbilityManagerStreamExecutionTest {

    private static final String TOOL_NAME = "side_effect_tool";

    private static final String TOOL_CALL_ID = "call_124";

    private static ToolCall newToolCall() {
        return ToolCall.builder()
                .id(TOOL_CALL_ID)
                .name(TOOL_NAME)
                .arguments("{}")
                .index(0)
                .build();
    }

    private static LocalFunction newLocalFunction(Function<Map<String, Object>, Object> func) {
        ToolCard card = ToolCard.builder().name(TOOL_NAME).description("counts its executions").build();
        return new LocalFunction(card, func);
    }

    /**
     * Invoke the private {@code executeStreamingTool}, unwrapping the reflection wrapper so a
     * failure inside the tool surfaces as the exception the production caller would see.
     */
    private static AbilityManager.ToolExecutionEntry executeStreamingTool(
            Tool tool, AgentSessionApi agentSession) throws Throwable {
        Method method = AbilityManager.class.getDeclaredMethod("executeStreamingTool",
                Tool.class, ToolCall.class, Map.class, Session.class, AgentSessionApi.class, int.class);
        method.setAccessible(true);
        try {
            Object entry = method.invoke(new AbilityManager(), tool, newToolCall(), Map.of(), null, agentSession, 0);
            return (AbilityManager.ToolExecutionEntry) entry;
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    void executeStreamingTool_nonStreamingFunction_executesOnceAndEmitsOneChunk() throws Throwable {
        // Given: a local function returning a plain Map — the shape that used to run twice
        AtomicInteger executions = new AtomicInteger();
        Map<String, Object> payload = Map.of("status", "charged");
        LocalFunction tool = newLocalFunction(inputs -> {
            executions.incrementAndGet();
            return payload;
        });
        AgentSessionApi agentSession = mock(AgentSessionApi.class);

        // When
        AbilityManager.ToolExecutionEntry entry = executeStreamingTool(tool, agentSession);

        // Then: executed exactly once, and the complete result is one chunk
        assertThat(executions.get()).as("underlying function must run exactly once").isEqualTo(1);
        verify(agentSession, times(1)).writeStream(any());
        assertThat(entry.result()).isSameAs(payload);
        assertThat(entry.toolMessage().getContent()).isEqualTo(payload.toString());
    }

    @Test
    void executeStreamingTool_iterableFunction_executesOnceAndEmitsChunkPerElement() throws Throwable {
        // Given: a genuinely streaming function returning an Iterable
        AtomicInteger executions = new AtomicInteger();
        LocalFunction tool = newLocalFunction(inputs -> {
            executions.incrementAndGet();
            return List.of("first ", "second ", "third");
        });
        AgentSessionApi agentSession = mock(AgentSessionApi.class);

        // When
        AbilityManager.ToolExecutionEntry entry = executeStreamingTool(tool, agentSession);

        // Then: one execution, one chunk per element, chunks merged back into the full text
        assertThat(executions.get()).isEqualTo(1);
        verify(agentSession, times(3)).writeStream(any());
        assertThat(entry.result()).isEqualTo("first second third");
    }

    @Test
    void executeStreamingTool_iteratorFunction_executesOnceAndEmitsChunkPerElement() throws Throwable {
        // Given: a function returning an Iterator rather than an Iterable
        AtomicInteger executions = new AtomicInteger();
        LocalFunction tool = newLocalFunction(inputs -> {
            executions.incrementAndGet();
            Iterator<Object> chunks = List.<Object>of("a", "b").iterator();
            return chunks;
        });
        AgentSessionApi agentSession = mock(AgentSessionApi.class);

        // When
        AbilityManager.ToolExecutionEntry entry = executeStreamingTool(tool, agentSession);

        // Then
        assertThat(executions.get()).isEqualTo(1);
        verify(agentSession, times(2)).writeStream(any());
        assertThat(entry.result()).isEqualTo("ab");
    }

    @Test
    void executeStreamingTool_functionFailsWithLocalFunctionCode_doesNotExecuteAgain() throws Throwable {
        // Given: the tool itself fails with the very status code that used to mean
        // "this tool is not streaming", which previously triggered a second invoke()
        AtomicInteger executions = new AtomicInteger();
        LocalFunction tool = newLocalFunction(inputs -> {
            executions.incrementAndGet();
            throw ErrorHelper.buildError(StatusCode.TOOL_LOCAL_FUNCTION_EXECUTION_ERROR,
                    "method", "invoke", "reason", "downstream service rejected the request",
                    "card", TOOL_NAME);
        });
        AgentSessionApi agentSession = mock(AgentSessionApi.class);

        // When / Then: the failure propagates and the tool is not retried
        assertThatThrownBy(() -> executeStreamingTool(tool, agentSession))
                .hasMessageContaining("downstream service rejected the request");
        assertThat(executions.get()).as("a failing tool must not be executed a second time").isEqualTo(1);
    }

}
