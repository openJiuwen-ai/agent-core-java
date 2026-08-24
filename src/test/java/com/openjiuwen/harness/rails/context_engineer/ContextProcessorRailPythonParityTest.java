/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.context_engineer;

import com.openjiuwen.core.context.ContextEngine;
import com.openjiuwen.core.context.ContextStats;
import com.openjiuwen.core.context.ContextWindow;
import com.openjiuwen.core.context.ModelContext;
import com.openjiuwen.core.context.processor.compressor.CurrentRoundCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.DialogueCompressorConfig;
import com.openjiuwen.core.context.processor.compressor.RoundLevelCompressorConfig;
import com.openjiuwen.core.context.processor.offloader.MessageSummaryOffloaderConfig;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.foundation.tool.schema.ToolInfo;
import com.openjiuwen.core.context.context.SessionMemoryConfig;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.agents.ReActAgentConfig;
import com.openjiuwen.core.singleagent.prompts.PromptSection;
import com.openjiuwen.core.singleagent.prompts.SystemPromptBuilder;
import com.openjiuwen.core.singleagent.rail.AgentCallbackContext;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.harness.test_context_processor_rail} in
 * {@code tests/unit_tests/harness/test_context_processor_rail.py}.</p>
 */
class ContextProcessorRailPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "test_init_processors_merge",
            "test_init_preset_defaults",
            "test_fix_incomplete_tool_context",
            "test_fix_incomplete_tool_context_null_context",
            "test_before_invoke_and_on_exception_call_fix_context",
            "test_ensure_json_arguments_with_invalid_json",
            "test_ensure_json_arguments_with_dict_input",
            "test_fix_incomplete_tool_context_with_broken_arguments",
            "test_merge_config_with_overrides",
            "test_merge_config_with_overrides_none",
            "test_merge_processors_replace_existing",
            "test_merge_processors_add_new",
            "test_merge_processors_with_dict_override",
            "test_merge_processors_dict_override_new_processor_error",
            "test_merge_processors_with_model_config",
            "test_build_preset_processors_without_session_memory",
            "test_build_preset_processors_with_session_memory",
            "test_build_preset_processors_with_session_memory_dict",
            "test_context_processor_rail_init_with_preset",
            "test_context_processor_rail_init_without_preset",
            "test_context_processor_rail_init_with_tuple_processors",
            "test_context_processor_rail_init_with_dict_override",
            "test_context_processor_rail_uninit_clears_processors",
            "test_refresh_task_state_runtime_with_deep_agent_state",
            "test_refresh_task_state_runtime_with_persisted_dict",
            "test_refresh_task_state_runtime_no_session",
            "test_before_model_call_refreshes_state",
            "test_after_model_call_refreshes_state",
            "test_after_tool_call_refreshes_state",
            "test_init_with_session_memory_config",
            "test_init_with_session_memory_dict",
            "test_uninit_shuts_down_session_memory_manager",
            "test_session_memory_not_enabled_without_config",
            "test_fix_incomplete_tool_context_with_empty_messages",
            "test_fix_incomplete_tool_context_multiple_tools_same_call",
            "test_fix_incomplete_tool_context_with_matching_response",
            "test_fix_incomplete_tool_context_unordered_tool_responses",
            "test_ensure_json_arguments_with_nested_dict",
            "test_offload_section_injected_when_preset_enabled",
            "test_offload_section_not_injected_when_no_processors",
            "test_offload_section_injected_when_user_processors_exist",
            "test_offload_section_uses_correct_language_cn",
            "test_offload_section_uses_correct_language_en",
            "test_offload_section_not_injected_when_builder_is_none",
            "test_offload_section_priority",
            "test_uninit_removes_offload_section",
            "test_before_model_call_injects_offload_section"
    );

    @TestFactory
    Collection<DynamicTest> pythonContextProcessorRailCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "test_init_processors_merge" -> initProcessorsMerge();
            case "test_init_preset_defaults" -> initPresetDefaults();
            case "test_fix_incomplete_tool_context" -> fixIncompleteToolContext();
            case "test_fix_incomplete_tool_context_null_context" -> fixIncompleteToolContextNullContext();
            case "test_before_invoke_and_on_exception_call_fix_context" -> beforeInvokeAndOnExceptionCallFixContext();
            case "test_ensure_json_arguments_with_invalid_json" -> ensureJsonArgumentsWithInvalidJson();
            case "test_ensure_json_arguments_with_dict_input" -> ensureJsonArgumentsWithDictInput();
            case "test_fix_incomplete_tool_context_with_broken_arguments" -> fixIncompleteToolContextWithBrokenArguments();
            case "test_merge_config_with_overrides" -> mergeConfigWithOverrides();
            case "test_merge_config_with_overrides_none" -> mergeConfigWithOverridesNone();
            case "test_merge_processors_replace_existing" -> mergeProcessorsReplaceExisting();
            case "test_merge_processors_add_new" -> mergeProcessorsAddNew();
            case "test_merge_processors_with_dict_override" -> mergeProcessorsWithDictOverride();
            case "test_merge_processors_dict_override_new_processor_error" -> mergeProcessorsDictOverrideNewProcessorError();
            case "test_merge_processors_with_model_config" -> mergeProcessorsWithModelConfig();
            case "test_build_preset_processors_without_session_memory" -> buildPresetProcessorsWithoutSessionMemory();
            case "test_build_preset_processors_with_session_memory" -> buildPresetProcessorsWithSessionMemory();
            case "test_build_preset_processors_with_session_memory_dict" -> buildPresetProcessorsWithSessionMemoryDict();
            case "test_context_processor_rail_init_with_preset" -> contextProcessorRailInitWithPreset();
            case "test_context_processor_rail_init_without_preset" -> contextProcessorRailInitWithoutPreset();
            case "test_context_processor_rail_init_with_tuple_processors" -> contextProcessorRailInitWithTupleProcessors();
            case "test_context_processor_rail_init_with_dict_override" -> contextProcessorRailInitWithDictOverride();
            case "test_context_processor_rail_uninit_clears_processors" -> contextProcessorRailUninitClearsProcessors();
            case "test_refresh_task_state_runtime_with_deep_agent_state" -> refreshTaskStateRuntimeWithDeepAgentState();
            case "test_refresh_task_state_runtime_with_persisted_dict" -> refreshTaskStateRuntimeWithPersistedDict();
            case "test_refresh_task_state_runtime_no_session" -> refreshTaskStateRuntimeNoSession();
            case "test_before_model_call_refreshes_state" -> beforeModelCallRefreshesState();
            case "test_after_model_call_refreshes_state" -> afterModelCallRefreshesState();
            case "test_after_tool_call_refreshes_state" -> afterToolCallRefreshesState();
            case "test_init_with_session_memory_config" -> initWithSessionMemoryConfig();
            case "test_init_with_session_memory_dict" -> initWithSessionMemoryDict();
            case "test_uninit_shuts_down_session_memory_manager" -> uninitShutsDownSessionMemoryManager();
            case "test_session_memory_not_enabled_without_config" -> sessionMemoryNotEnabledWithoutConfig();
            case "test_fix_incomplete_tool_context_with_empty_messages" -> fixIncompleteToolContextWithEmptyMessages();
            case "test_fix_incomplete_tool_context_multiple_tools_same_call" -> fixIncompleteToolContextMultipleToolsSameCall();
            case "test_fix_incomplete_tool_context_with_matching_response" -> fixIncompleteToolContextWithMatchingResponse();
            case "test_fix_incomplete_tool_context_unordered_tool_responses" -> fixIncompleteToolContextUnorderedToolResponses();
            case "test_ensure_json_arguments_with_nested_dict" -> ensureJsonArgumentsWithNestedDict();
            case "test_offload_section_injected_when_preset_enabled" -> offloadSectionInjectedWhenPresetEnabled();
            case "test_offload_section_not_injected_when_no_processors" -> offloadSectionNotInjectedWhenNoProcessors();
            case "test_offload_section_injected_when_user_processors_exist" -> offloadSectionInjectedWhenUserProcessorsExist();
            case "test_offload_section_uses_correct_language_cn" -> offloadSectionUsesCorrectLanguageCn();
            case "test_offload_section_uses_correct_language_en" -> offloadSectionUsesCorrectLanguageEn();
            case "test_offload_section_not_injected_when_builder_is_none" -> offloadSectionNotInjectedWhenBuilderIsNone();
            case "test_offload_section_priority" -> offloadSectionPriority();
            case "test_uninit_removes_offload_section" -> uninitRemovesOffloadSection();
            case "test_before_model_call_injects_offload_section" -> beforeModelCallInjectsOffloadSection();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void initProcessorsMerge() {
        assertEquals(List.of(), initializedKeys(new ContextProcessorRail(null, false, null)));
        assertEquals(List.of("custom"), initializedKeys(new ContextProcessorRail(
                List.of(spec("custom", new DialogueCompressorConfig())), false, null)));
        DialogueCompressorConfig custom = new DialogueCompressorConfig();
        custom.setMessagesToKeep(5);
        assertEquals(List.of("d"), initializedKeys(new ContextProcessorRail(List.of(spec("d", custom)), false, null)));
        assertEquals(defaultProcessorKeys(), initializedKeys(new ContextProcessorRail()));
        assertEquals(List.of("MessageSummaryOffloader", "DialogueCompressor", "CurrentRoundCompressor",
                        "RoundLevelCompressor", "d"),
                initializedKeys(new ContextProcessorRail(List.of(spec("d", new DialogueCompressorConfig())))));
        assertEquals(defaultProcessorKeys(), initializedKeys(new ContextProcessorRail(
                List.of(spec("DialogueCompressor", mapOf("messages_threshold", 99))))));
    }

    private void initPresetDefaults() {
        MockAgent agent = new MockAgent();
        new ContextProcessorRail().init(agent);
        Map<String, Object> processors = processorMap(agent);

        MessageSummaryOffloaderConfig offloader = (MessageSummaryOffloaderConfig) processors.get("MessageSummaryOffloader");
        assertNotNull(offloader);
        assertEquals(10000, offloader.getLargeMessageThreshold());
        assertEquals(List.of("tool"), offloader.getOffloadMessageType());
        assertEquals(List.of("read_file:*SKILL.md", "reload_original_context_messages"), offloader.getProtectedToolNames());
        assertEquals(900, offloader.getSummaryMaxTokens());

        DialogueCompressorConfig dialogue = (DialogueCompressorConfig) processors.get("DialogueCompressor");
        assertNull(dialogue.getMessagesThreshold());
        assertEquals(100000, dialogue.getTokensThreshold());
        assertEquals(10, dialogue.getMessagesToKeep());
        assertFalse(dialogue.isKeepLastRound());
        assertEquals(1800, dialogue.getCompressionTargetTokens());

        CurrentRoundCompressorConfig current = (CurrentRoundCompressorConfig) processors.get("CurrentRoundCompressor");
        assertEquals(100000, current.getTokensThreshold());
        assertEquals(3, current.getMessagesToKeep());
        assertEquals(4000, current.getCompressionTargetTokens());

        RoundLevelCompressorConfig roundLevel = (RoundLevelCompressorConfig) processors.get("RoundLevelCompressor");
        assertEquals(230000, roundLevel.getTriggerTotalTokens());
        assertEquals(160000, roundLevel.getTargetTotalTokens());
        assertEquals(6, roundLevel.getKeepRecentMessages());
    }

    private void fixIncompleteToolContext() {
        List<FixCase> cases = List.of(
                new FixCase(List.of(), 0, List.of()),
                new FixCase(List.of(new SystemMessage("sys"), new UserMessage("user")), 2, List.of()),
                new FixCase(List.of(new AssistantMessage("no tools")), 1, List.of()),
                new FixCase(List.of(new UserMessage("user")), 1, List.of()),
                new FixCase(List.of(assistant("call", tool("tc1", "t", "{}"))), 2, List.of("tc1")),
                new FixCase(List.of(assistant("call", tool("", "t", "{}"))), 2, List.of("")),
                new FixCase(List.of(assistant("c1", tool("a", "t", "{}")), new UserMessage("user")), 3, List.of("a")),
                new FixCase(List.of(assistant("c1", tool("a", "t1", "{}")),
                        assistant("c2", tool("b", "t2", "{}"))), 4, List.of("a", "b")),
                new FixCase(List.of(assistant("call", tool("x", "t1", "{}"), tool("y", "t2", "{}")),
                        new ToolMessage("res", "x")), 3, List.of("y")),
                new FixCase(List.of(new ToolMessage("res", "old"), assistant("call", tool("new", "t", "{}"))),
                        3, List.of("new"))
        );
        for (FixCase fixCase : cases) {
            MockModelContext modelContext = new MockModelContext(fixCase.messages());
            ContextProcessorRail.fixIncompleteToolContext(contextWithModelContext(modelContext));
            assertEquals(fixCase.expectedAddedCount(), modelContext.addedMessages.size());
            assertEquals(fixCase.expectedPlaceholderIds(), placeholderIds(modelContext.addedMessages));
        }
    }

    private void fixIncompleteToolContextNullContext() {
        assertDoesNotThrow(() -> ContextProcessorRail.fixIncompleteToolContext(new AgentCallbackContext()));
    }

    private void beforeInvokeAndOnExceptionCallFixContext() {
        ContextProcessorRail rail = new ContextProcessorRail();
        MockModelContext modelContext = new MockModelContext(List.of(
                assistant("call", tool("tc", "t", "{}")),
                new UserMessage("u")
        ));
        AgentCallbackContext ctx = contextWithModelContext(modelContext);

        rail.beforeInvoke(ctx).toCompletableFuture().join();
        assertEquals(List.of("tc"), placeholderIds(modelContext.addedMessages));

        MockModelContext modelContext2 = new MockModelContext(List.of(
                assistant("call", tool("tc2", "t", "{}")),
                new UserMessage("u")
        ));
        rail.onModelException(contextWithModelContext(modelContext2)).toCompletableFuture().join();
        assertEquals(List.of("tc2"), placeholderIds(modelContext2.addedMessages));
    }

    private void ensureJsonArgumentsWithInvalidJson() {
        Map<Object, String> cases = new LinkedHashMap<>();
        cases.put("{\"key\": \"value\"}", "{\"key\": \"value\"}");
        cases.put("{}", "{}");
        cases.put("{\"a\": 1, \"b\": 2}", "{\"a\": 1, \"b\": 2}");
        cases.put("{\"incomplete\": ", "{}");
        cases.put("{bad json}", "{}");
        cases.put("{\"unterminated\": \"string", "{}");
        cases.put("[1, 2,", "{}");
        cases.put("null", "{}");
        cases.put("{\"nested\": {\"incomplete\":", "{}");
        cases.put(null, "{}");
        cases.put(123, "{}");
        cases.put("", "{}");
        cases.forEach((input, expected) -> assertEquals(expected, ContextProcessorRail.ensureJsonArguments(input)));
    }

    private void ensureJsonArgumentsWithDictInput() {
        assertEquals("{}", ContextProcessorRail.ensureJsonArguments(Map.of()));
        assertEquals("{\"key\": \"value\"}", ContextProcessorRail.ensureJsonArguments(Map.of("key", "value")));
        assertEquals("{\"a\": 1, \"b\": [1, 2, 3]}",
                ContextProcessorRail.ensureJsonArguments(mapOf("a", 1, "b", List.of(1, 2, 3))));
    }

    private void fixIncompleteToolContextWithBrokenArguments() {
        MockModelContext modelContext = new MockModelContext(List.of(assistant("call", tool("tc1", "test_tool",
                "{\"incomplete\": "))));

        ContextProcessorRail.fixIncompleteToolContext(contextWithModelContext(modelContext));

        assertEquals(2, modelContext.addedMessages.size());
        AssistantMessage assistant = (AssistantMessage) modelContext.addedMessages.get(0);
        assertEquals("{}", assistant.getToolCalls().get(0).getArguments());
        ToolMessage placeholder = (ToolMessage) modelContext.addedMessages.get(1);
        assertEquals("tc1", placeholder.getToolCallId());
    }

    private void mergeConfigWithOverrides() {
        DialogueCompressorConfig base = new DialogueCompressorConfig();
        base.setMessagesThreshold(10);
        base.setTokensThreshold(50000);
        base.setMessagesToKeep(5);

        DialogueCompressorConfig result = (DialogueCompressorConfig) ContextProcessorRail.mergeConfigWithOverrides(
                base, mapOf("messages_threshold", 20, "compression_target_tokens", 2000));

        assertEquals(20, result.getMessagesThreshold());
        assertEquals(50000, result.getTokensThreshold());
        assertEquals(5, result.getMessagesToKeep());
        assertEquals(2000, result.getCompressionTargetTokens());
    }

    private void mergeConfigWithOverridesNone() {
        DialogueCompressorConfig base = new DialogueCompressorConfig();
        base.setMessagesThreshold(10);

        DialogueCompressorConfig result = (DialogueCompressorConfig) ContextProcessorRail.mergeConfigWithOverrides(base, null);

        assertEquals(10, result.getMessagesThreshold());
    }

    private void mergeProcessorsReplaceExisting() {
        List<ContextEngine.ProcessorSpec> result = ContextProcessorRail.mergeProcessors(
                List.of(spec("DialogueCompressor", configWithMessagesThreshold(10)),
                        spec("CurrentRoundCompressor", new CurrentRoundCompressorConfig())),
                List.of(spec("DialogueCompressor", configWithMessagesThreshold(99))),
                null,
                null
        );

        assertTrue(keys(result).containsAll(List.of("DialogueCompressor", "CurrentRoundCompressor")));
        assertEquals(99, ((DialogueCompressorConfig) result.get(0).config()).getMessagesThreshold());
    }

    private void mergeProcessorsAddNew() {
        List<ContextEngine.ProcessorSpec> result = ContextProcessorRail.mergeProcessors(
                List.of(spec("DialogueCompressor", configWithMessagesThreshold(10))),
                List.of(spec("CustomProcessor", configWithMessagesThreshold(50))),
                null,
                null
        );

        assertEquals(List.of("DialogueCompressor", "CustomProcessor"), keys(result));
    }

    private void mergeProcessorsWithDictOverride() {
        List<ContextEngine.ProcessorSpec> result = ContextProcessorRail.mergeProcessors(
                List.of(spec("DialogueCompressor", dialogueConfig(10, 50000))),
                List.of(spec("DialogueCompressor", Map.of("messages_threshold", 25))),
                null,
                null
        );

        DialogueCompressorConfig config = (DialogueCompressorConfig) result.get(0).config();
        assertEquals(25, config.getMessagesThreshold());
        assertEquals(50000, config.getTokensThreshold());
    }

    private void mergeProcessorsDictOverrideNewProcessorError() {
        assertThrows(IllegalArgumentException.class, () -> ContextProcessorRail.mergeProcessors(
                List.of(),
                List.of(spec("NonExistent", Map.of("messages_threshold", 10))),
                null,
                null
        ));
    }

    private void mergeProcessorsWithModelConfig() {
        List<ContextEngine.ProcessorSpec> result = ContextProcessorRail.mergeProcessors(
                List.of(spec("DialogueCompressor", new DialogueCompressorConfig())),
                List.of(),
                new com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig(),
                null
        );

        assertNotNull(((DialogueCompressorConfig) result.get(0).config()).getModel());
    }

    private void buildPresetProcessorsWithoutSessionMemory() {
        assertEquals(defaultProcessorKeys(), initializedKeys(new ContextProcessorRail()));
    }

    private void buildPresetProcessorsWithSessionMemory() {
        ContextProcessorRail rail = new ContextProcessorRail(null, true, new SessionMemoryConfig());
        assertEquals(List.of("ToolResultBudgetProcessor", "MicroCompactProcessor", "FullCompactProcessor"),
                initializedKeys(rail));
    }

    private void buildPresetProcessorsWithSessionMemoryDict() {
        SessionMemoryConfig config = new SessionMemoryConfig();
        config.setTriggerTokens(5);
        ContextProcessorRail rail = new ContextProcessorRail(null, true, config);
        rail.init(new MockAgent());

        assertTrue(rail.isSessionMemoryEnabled());
        assertEquals(5, rail.getSessionMemoryConfig().getTriggerTokens());
    }

    private void contextProcessorRailInitWithPreset() {
        ContextProcessorRail rail = new ContextProcessorRail();
        MockAgent agent = new MockAgent();
        rail.init(agent);

        assertFalse(contextProcessors(agent).isEmpty());
    }

    private void contextProcessorRailInitWithoutPreset() {
        ContextProcessorRail rail = new ContextProcessorRail(null, false, null);
        MockAgent agent = new MockAgent();
        rail.init(agent);

        assertEquals(List.of(), contextProcessors(agent));
    }

    private void contextProcessorRailInitWithTupleProcessors() {
        ContextProcessorRail rail = new ContextProcessorRail(List.of(spec("CustomProcessor", new DialogueCompressorConfig())),
                false, null);
        assertEquals(List.of("CustomProcessor"), initializedKeys(rail));
    }

    private void contextProcessorRailInitWithDictOverride() {
        MockAgent agent = new MockAgent();
        new ContextProcessorRail(List.of(spec("DialogueCompressor", Map.of("messages_threshold", 25)))).init(agent);

        DialogueCompressorConfig config = (DialogueCompressorConfig) processorMap(agent).get("DialogueCompressor");
        assertEquals(25, config.getMessagesThreshold());
    }

    private void contextProcessorRailUninitClearsProcessors() {
        ContextProcessorRail rail = new ContextProcessorRail();
        MockAgent agent = new MockAgent();
        rail.init(agent);
        assertFalse(contextProcessors(agent).isEmpty());

        rail.uninit(agent);

        assertEquals(List.of(), contextProcessors(agent));
    }

    private void refreshTaskStateRuntimeWithDeepAgentState() {
        FakeSession session = new FakeSession(mapOf(
                "iteration", 5,
                "stop_condition_state", Map.of("iteration", 5),
                "pending_follow_ups", List.of("follow1"),
                "plan_mode", mapOf("mode", "normal", "pre_plan_mode", null, "plan_slug", null, "prompt_context", null)
        ));
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setSession(session);

        new ContextProcessorRail().beforeModelCall(ctx).toCompletableFuture().join();

        assertEquals(5, session.lastUpdate.get("iteration"));
        assertEquals(List.of("follow1"), session.lastUpdate.get("pending_follow_ups"));
        @SuppressWarnings("unchecked")
        Map<String, Object> planMode = (Map<String, Object>) session.lastUpdate.get("plan_mode");
        assertEquals("normal", planMode.get("mode"));
        assertEquals("normal", planMode.get("pre_plan_mode"));
    }

    private void refreshTaskStateRuntimeWithPersistedDict() {
        FakeSession session = new FakeSession(mapOf(
                "iteration", 10,
                "stop_condition_state", Map.of("iteration", 10),
                "pending_follow_ups", List.of(),
                "plan_mode", mapOf("mode", "manual", "pre_plan_mode", null)
        ));
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setSession(session);

        new ContextProcessorRail().beforeModelCall(ctx).toCompletableFuture().join();

        assertEquals(10, session.lastUpdate.get("iteration"));
    }

    private void refreshTaskStateRuntimeNoSession() {
        AgentCallbackContext ctx = new AgentCallbackContext();
        assertDoesNotThrow(() -> new ContextProcessorRail().beforeModelCall(ctx).toCompletableFuture().join());
    }

    private void beforeModelCallRefreshesState() {
        FakeSession session = new FakeSession(Map.of("iteration", 3));
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setSession(session);
        new ContextProcessorRail().beforeModelCall(ctx).toCompletableFuture().join();
        assertEquals(3, session.lastUpdate.get("iteration"));
    }

    private void afterModelCallRefreshesState() {
        FakeSession session = new FakeSession(Map.of("iteration", 4));
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setSession(session);
        new ContextProcessorRail().afterModelCall(ctx).toCompletableFuture().join();
        assertEquals(4, session.lastUpdate.get("iteration"));
    }

    private void afterToolCallRefreshesState() {
        FakeSession session = new FakeSession(Map.of("iteration", 5));
        AgentCallbackContext ctx = new AgentCallbackContext();
        ctx.setSession(session);
        new ContextProcessorRail().afterToolCall(ctx).toCompletableFuture().join();
        assertEquals(5, session.lastUpdate.get("iteration"));
    }

    private void initWithSessionMemoryConfig() {
        ContextProcessorRail rail = new ContextProcessorRail(null, true, new SessionMemoryConfig());

        assertTrue(rail.isSessionMemoryEnabled());
        assertNotNull(rail.getSessionMemoryConfig());
        assertNotNull(rail.getSessionMemoryManager());
    }

    private void initWithSessionMemoryDict() {
        SessionMemoryConfig config = new SessionMemoryConfig();
        config.setTriggerTokens(5);
        ContextProcessorRail rail = new ContextProcessorRail(null, true, config);

        assertTrue(rail.isSessionMemoryEnabled());
        assertInstanceOf(SessionMemoryConfig.class, rail.getSessionMemoryConfig());
    }

    private void uninitShutsDownSessionMemoryManager() {
        ContextProcessorRail rail = new ContextProcessorRail(null, true, new SessionMemoryConfig());
        assertNotNull(rail.getSessionMemoryManager());
        assertDoesNotThrow(() -> rail.uninit(new MockAgent()));
    }

    private void sessionMemoryNotEnabledWithoutConfig() {
        ContextProcessorRail rail = new ContextProcessorRail();

        assertFalse(rail.isSessionMemoryEnabled());
        assertNull(rail.getSessionMemoryConfig());
        assertNull(rail.getSessionMemoryManager());
    }

    private void fixIncompleteToolContextWithEmptyMessages() {
        MockModelContext modelContext = new MockModelContext(List.of());
        ContextProcessorRail.fixIncompleteToolContext(contextWithModelContext(modelContext));
        assertEquals(0, modelContext.addedMessages.size());
    }

    private void fixIncompleteToolContextMultipleToolsSameCall() {
        MockModelContext modelContext = new MockModelContext(List.of(
                assistant("I'll use multiple tools", tool("tc1", "tool1", "{}"),
                        tool("tc2", "tool2", "{}"), tool("tc3", "tool3", "{}"))
        ));

        ContextProcessorRail.fixIncompleteToolContext(contextWithModelContext(modelContext));

        assertEquals(List.of("tc1", "tc2", "tc3"), placeholderIds(modelContext.addedMessages));
    }

    private void fixIncompleteToolContextWithMatchingResponse() {
        MockModelContext modelContext = new MockModelContext(List.of(
                assistant("using tool", tool("tc1", "t", "{}")),
                new ToolMessage("result", "tc1")
        ));

        ContextProcessorRail.fixIncompleteToolContext(contextWithModelContext(modelContext));

        assertEquals(List.of(), placeholderIds(modelContext.addedMessages));
        List<ToolMessage> toolMessages = modelContext.addedMessages.stream()
                .filter(ToolMessage.class::isInstance)
                .map(ToolMessage.class::cast)
                .toList();
        assertEquals(1, toolMessages.size());
        assertEquals("result", toolMessages.get(0).getContent());
    }

    private void fixIncompleteToolContextUnorderedToolResponses() {
        MockModelContext modelContext = new MockModelContext(List.of(
                assistant("using tool", tool("tc1", "t", "{}")),
                new ToolMessage("result2", "tc2")
        ));

        ContextProcessorRail.fixIncompleteToolContext(contextWithModelContext(modelContext));

        assertEquals(List.of("tc1"), placeholderIds(modelContext.addedMessages));
    }

    private void ensureJsonArgumentsWithNestedDict() {
        assertEquals("{\"outer\": {\"inner\": [1, 2, 3]}}",
                ContextProcessorRail.ensureJsonArguments(Map.of("outer", Map.of("inner", List.of(1, 2, 3)))));
    }

    private void offloadSectionInjectedWhenPresetEnabled() {
        ContextProcessorRail rail = initializedRail(new ContextProcessorRail());
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        rail.beforeModelCall(contextWithBuilder(builder)).toCompletableFuture().join();
        assertTrue(builder.hasSection("offload"));
    }

    private void offloadSectionNotInjectedWhenNoProcessors() {
        ContextProcessorRail rail = initializedRail(new ContextProcessorRail(null, false, null));
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        rail.beforeModelCall(contextWithBuilder(builder)).toCompletableFuture().join();
        assertFalse(builder.hasSection("offload"));
    }

    private void offloadSectionInjectedWhenUserProcessorsExist() {
        ContextProcessorRail rail = initializedRail(new ContextProcessorRail(
                List.of(spec("CustomProcessor", new DialogueCompressorConfig())), false, null));
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        rail.beforeModelCall(contextWithBuilder(builder)).toCompletableFuture().join();
        assertTrue(builder.hasSection("offload"));
    }

    private void offloadSectionUsesCorrectLanguageCn() {
        PromptSection section = injectedOffloadSection("cn");
        assertTrue(section.getContent().containsKey("cn"));
        assertTrue(section.getContent().get("cn").contains("上下文压缩"));
    }

    private void offloadSectionUsesCorrectLanguageEn() {
        PromptSection section = injectedOffloadSection("en");
        assertTrue(section.getContent().containsKey("en"));
        assertTrue(section.getContent().get("en").contains("Context Compression"));
    }

    private void offloadSectionNotInjectedWhenBuilderIsNone() {
        ContextProcessorRail rail = initializedRail(new ContextProcessorRail());
        assertDoesNotThrow(() -> rail.beforeModelCall(new AgentCallbackContext()).toCompletableFuture().join());
    }

    private void offloadSectionPriority() {
        assertEquals(90, injectedOffloadSection("cn").getPriority());
    }

    private void uninitRemovesOffloadSection() {
        ContextProcessorRail rail = initializedRail(new ContextProcessorRail());
        MockAgent agent = new MockAgent();
        rail.init(agent);
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        rail.beforeModelCall(contextWithBuilder(builder)).toCompletableFuture().join();
        assertTrue(builder.hasSection("offload"));

        rail.uninit(agent);

        assertFalse(builder.hasSection("offload"));
        assertEquals(List.of(), rail.getAllProcessors());
    }

    private void beforeModelCallInjectsOffloadSection() {
        ContextProcessorRail rail = initializedRail(new ContextProcessorRail());
        SystemPromptBuilder builder = new SystemPromptBuilder("cn");
        FakeSession session = new FakeSession(Map.of("iteration", 1));
        AgentCallbackContext ctx = contextWithBuilder(builder);
        ctx.setSession(session);

        rail.beforeModelCall(ctx).toCompletableFuture().join();

        assertTrue(builder.hasSection("offload"));
        assertEquals(1, session.lastUpdate.get("iteration"));
    }

    private static PromptSection injectedOffloadSection(String language) {
        ContextProcessorRail rail = initializedRail(new ContextProcessorRail());
        SystemPromptBuilder builder = new SystemPromptBuilder(language);
        rail.beforeModelCall(contextWithBuilder(builder)).toCompletableFuture().join();
        return builder.getSection("offload").orElseThrow();
    }

    private static ContextProcessorRail initializedRail(ContextProcessorRail rail) {
        rail.init(new MockAgent());
        return rail;
    }

    private static List<String> initializedKeys(ContextProcessorRail rail) {
        MockAgent agent = new MockAgent();
        rail.init(agent);
        return keys(contextProcessors(agent));
    }

    private static List<String> defaultProcessorKeys() {
        return List.of("MessageSummaryOffloader", "DialogueCompressor", "CurrentRoundCompressor",
                "RoundLevelCompressor");
    }

    private static Map<String, Object> processorMap(MockAgent agent) {
        Map<String, Object> processors = new LinkedHashMap<>();
        for (ContextEngine.ProcessorSpec spec : contextProcessors(agent)) {
            processors.put(spec.processorType(), spec.config());
        }
        return processors;
    }

    private static List<ContextEngine.ProcessorSpec> contextProcessors(MockAgent agent) {
        List<ContextEngine.ProcessorSpec> processors = agent.react_agent.config.getContextProcessors();
        return processors == null ? List.of() : processors;
    }

    private static List<String> keys(List<ContextEngine.ProcessorSpec> processors) {
        return processors.stream().map(ContextEngine.ProcessorSpec::processorType).toList();
    }

    private static ContextEngine.ProcessorSpec spec(String type, Object config) {
        return new ContextEngine.ProcessorSpec(type, config);
    }

    private static DialogueCompressorConfig configWithMessagesThreshold(int threshold) {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(threshold);
        return config;
    }

    private static DialogueCompressorConfig dialogueConfig(int messagesThreshold, int tokensThreshold) {
        DialogueCompressorConfig config = new DialogueCompressorConfig();
        config.setMessagesThreshold(messagesThreshold);
        config.setTokensThreshold(tokensThreshold);
        return config;
    }

    private static AssistantMessage assistant(String content, ToolCall... calls) {
        AssistantMessage message = new AssistantMessage(content);
        message.setToolCalls(List.of(calls));
        return message;
    }

    private static ToolCall tool(String id, String name, String arguments) {
        return ToolCall.builder().id(id).type("function").name(name).arguments(arguments).build();
    }

    private static AgentCallbackContext contextWithModelContext(ModelContext modelContext) {
        AgentCallbackContext context = new AgentCallbackContext();
        context.setContext(modelContext);
        return context;
    }

    private static AgentCallbackContext contextWithBuilder(SystemPromptBuilder builder) {
        AgentCallbackContext context = new AgentCallbackContext();
        context.getExtra().put("system_prompt_builder", builder);
        return context;
    }

    private static List<String> placeholderIds(List<BaseMessage> messages) {
        return messages.stream()
                .filter(ToolMessage.class::isInstance)
                .map(ToolMessage.class::cast)
                .filter(message -> String.valueOf(message.getContent()).contains("[Tool execution interrupted]"))
                .map(ToolMessage::getToolCallId)
                .toList();
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }

    private record FixCase(List<BaseMessage> messages, int expectedAddedCount, List<String> expectedPlaceholderIds) {
    }

    private static final class MockModelContext implements ModelContext {
        private List<BaseMessage> messages;
        private final List<BaseMessage> addedMessages = new ArrayList<>();
        private List<BaseMessage> poppedMessages = new ArrayList<>();

        private MockModelContext(List<BaseMessage> messages) {
            this.messages = new ArrayList<>(messages);
        }

        @Override
        public int length() {
            return messages.size();
        }

        @Override
        public List<BaseMessage> getMessages(Integer size, boolean withHistory) {
            if (size == null || size >= messages.size()) {
                return new ArrayList<>(messages);
            }
            return new ArrayList<>(messages.subList(0, size));
        }

        @Override
        public void setMessages(List<BaseMessage> messages, boolean withHistory) {
            this.messages = new ArrayList<>(messages);
        }

        @Override
        public List<BaseMessage> popMessages(int size, boolean withHistory) {
            int count = Math.min(size, messages.size());
            poppedMessages = new ArrayList<>(messages.subList(0, count));
            messages = new ArrayList<>(messages.subList(count, messages.size()));
            return poppedMessages;
        }

        @Override
        public CompletionStage<Void> clearMessages(boolean withHistory) {
            messages.clear();
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(BaseMessage message) {
            addedMessages.add(message);
            messages.add(message);
            return CompletableFuture.completedFuture(new ArrayList<>(addedMessages));
        }

        @Override
        public CompletionStage<List<BaseMessage>> addMessages(List<BaseMessage> messages) {
            addedMessages.addAll(messages);
            this.messages.addAll(messages);
            return CompletableFuture.completedFuture(new ArrayList<>(addedMessages));
        }

        @Override
        public CompletionStage<ContextWindow> getContextWindow(List<BaseMessage> systemMessages, List<ToolInfo> tools,
                                                               Integer windowSize, Integer dialogueRound,
                                                               Map<String, Object> kwargs) {
            return CompletableFuture.completedFuture(new ContextWindow(
                    systemMessages == null ? List.of() : systemMessages,
                    getMessages(null, true),
                    tools == null ? List.of() : tools,
                    null
            ));
        }

        @Override
        public ContextStats statistic() {
            return null;
        }

        @Override
        public String sessionId() {
            return "session";
        }

        @Override
        public String contextId() {
            return "context";
        }

        @Override
        public TokenCounterPort tokenCounter() {
            return null;
        }

        @Override
        public ToolPort reloaderTool() {
            return null;
        }
    }

    private static final class FakeSession implements AgentSessionApi {
        private final Map<String, Object> state = new LinkedHashMap<>();
        private Map<String, Object> lastUpdate = new LinkedHashMap<>();

        private FakeSession(Map<String, Object> state) {
            this.state.putAll(state);
        }

        @Override
        public String getSessionId() {
            return "session";
        }

        @Override
        public Object getState(String key) {
            return key == null ? new LinkedHashMap<>(state) : state.get(key);
        }

        @Override
        public void updateState(Map<String, Object> data) {
            lastUpdate = new LinkedHashMap<>(data);
            state.putAll(data);
        }

        @Override
        public void writeStream(Object data) {
        }

        @Override
        public Iterator<Object> streamIterator() {
            return Collections.emptyIterator();
        }
    }

    private static final class MockAgent extends BaseAgent {
        @SuppressWarnings("checkstyle:MemberName")
        private final ReactAgent react_agent = new ReactAgent();

        private MockAgent() {
            super(new AgentCard("agent-1", "agent-1", "Agent"));
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return Collections.emptyIterator();
        }
    }

    private static final class ReactAgent {
        private final ReActAgentConfig config = new ReActAgentConfig();
    }
}
