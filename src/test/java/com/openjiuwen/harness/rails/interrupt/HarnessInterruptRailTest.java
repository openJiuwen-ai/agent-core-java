package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptException;
import com.openjiuwen.core.singleagent.interrupt.ToolInterruptionState;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HarnessInterruptRailTest {

    @Test
    void addPolicyAndGetToolsMirrorPythonStyleApi() {
        TestRail rail = new TestRail();

        rail.addPolicy("ask_user", null);
        rail.addTool("confirm");

        assertThat(rail.getToolNames()).containsExactlyInAnyOrder("ask_user", "confirm");
    }

    @Test
    void interruptDecisionRaisesToolInterruptException() {
        TestRail rail = new TestRail();
        AgentCallbackContext ctx = context("ask_user", "{\"question\":\"name?\"}");

        ToolInterruptException error = assertThrows(ToolInterruptException.class, () -> rail.beforeToolCall(ctx));

        assertThat(error.getRequest().getMessage()).isEqualTo("name?");
    }

    @Test
    void approveDecisionOverridesToolArgs() {
        TestRail rail = new TestRail();
        AgentCallbackContext ctx = context("ask_user", "{\"question\":\"name?\"}");
        ctx.getExtra().put(ToolInterruptionState.RESUME_USER_INPUT_KEY, "Alice");

        rail.beforeToolCall(ctx);

        assertThat(((ToolCallInputs) ctx.getInputs()).getToolArgs()).isEqualTo("{\"response\":\"Alice\"}");
    }

    private AgentCallbackContext context(String toolName, String toolArgs) {
        return AgentCallbackContext.builder()
                .session(AgentSessionApi.create("harness-session", null, null))
                .inputs(ToolCallInputs.builder()
                        .toolCall(ToolCall.builder().id(toolName + "-call").name(toolName).arguments(toolArgs).build())
                        .toolName(toolName)
                        .toolArgs(toolArgs)
                        .build())
                .build();
    }

    private static final class TestRail extends BaseInterruptRail {

        private TestRail() {
            super(List.of("ask_user"));
        }

        @Override
        protected InterruptDecision resolveInterrupt(AgentCallbackContext ctx, ToolCall toolCall, Object userInput) {
            if (userInput == null) {
                return interrupt(com.openjiuwen.core.singleagent.interrupt.InterruptRequest.builder()
                        .interruptId(toolCall.getId())
                        .message("name?")
                        .context(Map.of("tool_call_id", toolCall.getId()))
                        .build());
            }
            return approve("{\"response\":\"" + userInput + "\"}");
        }
    }
}
