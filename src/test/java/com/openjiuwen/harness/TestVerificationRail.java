/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.single_agent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.rail.ToolCallInputs;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.harness.rails.subagent.VerificationRail;
import com.openjiuwen.harness.schema.DeepAgentState;
import com.openjiuwen.harness.schema.PlanModeState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mirrors Python's {@code test_verification_rail} in
 * {@code tests.unit_tests.harness.test_verification_rail}.
 */
class TestVerificationRail {

    @TempDir
    Path tmpDir;

    @Test
    void testAllowedPathWithinWorkspacePasses() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("list_files", Map.of("path", tmpDir.resolve("src").toString()));

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    void testAllowedPathWithinWorkspacePassesJsonArgs() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("grep", "{\"path\":\"" + esc(tmpDir.resolve("src").toString()) + "\"}");

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    void testAllowedPathAtWorkspaceRootPasses() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("list_files", Map.of("path", tmpDir.toString()));

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    void testOutOfScopePathIsBlocked() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("list_files", Map.of("path", tmpDir.resolveSibling("outside").toString()));

        rail.beforeToolCall(ctx);

        assertEquals(Boolean.TRUE, ctx.getExtra().get("_skip_tool"));
        assertTrue(String.valueOf(((Map<?, ?>) ((ToolCallInputs) ctx.getInputs()).getToolResult()).get("error"))
                .contains("outside the workspace scope"));
    }

    @Test
    void testOutOfScopePathIsBlockedJsonArgs() {
        VerificationRail rail = railWithRoot(tmpDir);
        String outside = tmpDir.resolveSibling("outside-json").toString();
        AgentCallbackContext ctx = toolCtx("glob", "{\"path\":\"" + esc(outside) + "\"}");

        rail.beforeToolCall(ctx);

        assertEquals(Boolean.TRUE, ctx.getExtra().get("_skip_tool"));
    }

    @Test
    void testOutOfScopeReadFileIsBlocked() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("read_file", Map.of("file_path", tmpDir.resolveSibling("secret.txt").toString()));

        rail.beforeToolCall(ctx);

        assertEquals(Boolean.TRUE, ctx.getExtra().get("_skip_tool"));
    }

    @Test
    void testOutOfScopeReadFileIsBlockedJsonArgs() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("read_file", "{\"file_path\":\"" + esc(tmpDir.resolveSibling("secret.json").toString()) + "\"}");

        rail.beforeToolCall(ctx);

        assertEquals(Boolean.TRUE, ctx.getExtra().get("_skip_tool"));
    }

    @Test
    void testNoWorkspaceConfiguredPassesThrough() {
        VerificationRail rail = new VerificationRail();
        AgentCallbackContext ctx = toolCtx("read_file", Map.of("file_path", tmpDir.resolveSibling("outside").toString()));

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    void testWorkspaceAsStringPath() {
        VerificationRail rail = new VerificationRail();
        rail.setWorkspaceRoot(tmpDir.toString());
        AgentCallbackContext ctx = toolCtx("list_files", Map.of("path", tmpDir.resolve("nested").toString()));

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    void testNonPathToolNotAffected() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("bash", Map.of("command", "pwd"));

        rail.beforeToolCall(ctx);

        assertFalse(ctx.getExtra().containsKey("_skip_tool"));
    }

    @Test
    void testDisallowedToolBlockedBeforeScopeCheck() {
        VerificationRail rail = railWithRoot(tmpDir);
        AgentCallbackContext ctx = toolCtx("write_file", Map.of("file_path", tmpDir.resolve("ok.txt").toString()));

        rail.beforeToolCall(ctx);

        assertEquals(Boolean.TRUE, ctx.getExtra().get("_skip_tool"));
        assertTrue(String.valueOf(((ToolCallInputs) ctx.getInputs()).getToolMsg().getContent())
                .contains("not available"));
    }

    @Test
    void testInjectsWhenTaskLoopActive() {
        FakeAgent agent = new FakeAgent(true, "normal");
        VerificationRail rail = new VerificationRail();
        rail.init(agent);

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).session(new FakeSession()).build());

        assertTrue(agent.systemPromptBuilder.getSection("verification_reminder").isPresent());
        assertTrue(agent.systemPromptBuilder.build().contains("VERDICT: PASS"));
    }

    @Test
    void testSkipsWhenTaskLoopDisabled() {
        FakeAgent agent = new FakeAgent(false, "normal");
        VerificationRail rail = new VerificationRail();
        rail.init(agent);

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).session(new FakeSession()).build());

        assertTrue(agent.systemPromptBuilder.getSection("verification_reminder").isEmpty());
    }

    @Test
    void testSkipsWhenInPlanMode() {
        FakeAgent agent = new FakeAgent(true, "plan");
        VerificationRail rail = new VerificationRail();
        rail.init(agent);

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).session(new FakeSession()).build());

        assertTrue(agent.systemPromptBuilder.getSection("verification_reminder").isEmpty());
    }

    @Test
    void testSkipsWhenNoBuilder() {
        VerificationRail rail = new VerificationRail();

        assertDoesNotThrow(() -> rail.beforeModelCall(AgentCallbackContext.builder()
                .agent(new FakeAgent(true, "normal"))
                .session(new FakeSession())
                .build()));
    }

    @Test
    void testLoadStateExceptionIsSwallowed() {
        ThrowingAgent agent = new ThrowingAgent(true);
        VerificationRail rail = new VerificationRail();
        rail.init(agent);

        rail.beforeModelCall(AgentCallbackContext.builder().agent(agent).session(new FakeSession()).build());

        assertTrue(agent.systemPromptBuilder.getSection("verification_reminder").isPresent());
    }

    private VerificationRail railWithRoot(Path root) {
        VerificationRail rail = new VerificationRail();
        rail.setWorkspaceRoot(root);
        return rail;
    }

    private static AgentCallbackContext toolCtx(String toolName, Object args) {
        return AgentCallbackContext.builder()
                .extra(new LinkedHashMap<>())
                .inputs(ToolCallInputs.builder()
                        .toolName(toolName)
                        .toolArgs(args)
                        .toolCall(ToolCall.builder().id("call-1").name(toolName).arguments("{}").build())
                        .build())
                .build();
    }

    private static String esc(String text) {
        return text.replace("\\", "\\\\");
    }

    static class FakeAgent {
        final SystemPromptBuilder systemPromptBuilder = new SystemPromptBuilder("en");
        final FakeConfig config;
        final String mode;

        FakeAgent(boolean enableTaskLoop, String mode) {
            this.config = new FakeConfig(enableTaskLoop);
            this.mode = mode;
        }

        public SystemPromptBuilder getSystemPromptBuilder() {
            return systemPromptBuilder;
        }

        public FakeConfig getConfig() {
            return config;
        }

        public DeepAgentState loadState(Session session) {
            DeepAgentState state = new DeepAgentState();
            state.setPlanMode(new PlanModeState(mode, "normal", null));
            return state;
        }
    }

    static final class ThrowingAgent extends FakeAgent {
        ThrowingAgent(boolean enableTaskLoop) {
            super(enableTaskLoop, "normal");
        }

        @Override
        public DeepAgentState loadState(Session session) {
            throw new IllegalStateException("boom");
        }
    }

    record FakeConfig(boolean enableTaskLoop) {
        public boolean getEnableTaskLoop() {
            return enableTaskLoop;
        }
    }

    static final class FakeSession implements Session {
        private final Map<String, Object> state = new LinkedHashMap<>();

        @Override
        public String getSessionId() {
            return "verification-session";
        }

        @Override
        public Object getState(String key) {
            return state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> state) {
            this.state.putAll(state);
        }
    }
}
