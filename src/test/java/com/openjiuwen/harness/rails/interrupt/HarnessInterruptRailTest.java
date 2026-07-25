package com.openjiuwen.harness.rails.interrupt;

import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.rails.CallbackContext;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class HarnessInterruptRailTest {

    @Test
    void addPolicyAndGetToolsMirrorPythonStyleApi() {
        TestRail rail = new TestRail();

        rail.addPolicy("ask_user", null);
        rail.addTool("confirm");

        assertThat(rail.getTools()).containsExactlyInAnyOrder("ask_user", "confirm");
    }

    @Test
    void interruptDecisionRaisesFlagInContext() {
        TestRail rail = new TestRail();
        DeepAgent agent = new DeepAgent();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", "ask_user");
        values.put("tool_args", "{\"question\":\"name?\"}");
        CallbackContext ctx = new CallbackContext(agent, values);

        rail.beforeToolCall(ctx);

        assertThat(ctx.get("interrupt_required")).isEqualTo(true);
    }

    @Test
    void approveDecisionOverridesToolArgs() {
        TestRail rail = new TestRail();
        DeepAgent agent = new DeepAgent();
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("tool_name", "ask_user");
        values.put("tool_args", "{\"question\":\"name?\"}");
        values.put("user_input", "Alice");
        CallbackContext ctx = new CallbackContext(agent, values);

        rail.beforeToolCall(ctx);

        assertThat(ctx.get("tool_args")).isEqualTo("{\"response\":\"Alice\"}");
    }

    private static final class TestRail extends BaseInterruptRail {

        private TestRail() {
            super(List.of("ask_user"));
        }

        @Override
        public void beforeToolCall(CallbackContext ctx) {
            super.beforeToolCall(ctx);
            if (Boolean.TRUE.equals(ctx.get("interrupt_required"))) {
                Object userInput = ctx.get("user_input");
                if (userInput == null) {
                    ctx.put("interrupt_required", true);
                } else {
                    ctx.put("tool_args", "{\"response\":\"" + userInput + "\"}");
                    ctx.put("interrupt_required", false);
                }
            }
        }
    }
}
