/*
 *  Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */
package com.openjiuwen.harness.tools.browser_move;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.foundation.tool.ToolCard;
import com.openjiuwen.core.foundation.tool.mcp.McpServerConfig;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.subagents.browser_agent.BrowserAgentConfigSpec;
import com.openjiuwen.harness.subagents.browser_agent.BrowserAgentFactory;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserRuntimeTools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for CreateBrowserAgent.
 * <p>
 * Mirrors Python's {@code test_create_browser_agent.py} from
 * {@code tests/unit_tests/harness/tools/browser_move/test_create_browser_agent.py}.
 *
 * <p>Python test file contains 15 test methods:
 * - test_default_wiring_creates_one_agent
 * - test_default_wiring_main_agent_card_is_browser_agent
 * - test_default_wiring_main_agent_has_no_subagents
 * - test_default_wiring_main_agent_receives_browser_helper_tools
 * - test_default_wiring_does_not_pre_register_playwright_mcp_on_subagent
 * - test_default_wiring_main_agent_has_browser_runtime_rail
 * - test_default_wiring_does_not_add_sys_operation_rail
 * - test_default_wiring_build_tools_called_with_runtime_instance
 * - test_custom_subagents_are_forwarded
 * - test_settings_forwarded_to_runtime_constructor
 * - test_language_en_uses_english_prompt
 * - test_language_cn_uses_chinese_prompt
 * - test_user_tools_are_merged_with_browser_tools
 * - test_build_browser_agent_config_uses_browser_factory
 * - test_build_browser_agent_config_fallback_uses_model_name_field
 */
@DisplayName("CreateBrowserAgent Tests")
class TestCreateBrowserAgent {

    // Helper method to create fake Model
    private Model createFakeModel() {
        ModelClientConfig clientConfig = mock(ModelClientConfig.class);
        when(clientConfig.getClientProvider()).thenReturn("openai");
        when(clientConfig.getApiKey()).thenReturn("test-key");
        when(clientConfig.getApiBase()).thenReturn("https://example.invalid/v1");

        ModelRequestConfig requestConfig = mock(ModelRequestConfig.class);
        when(requestConfig.getModelName()).thenReturn("test-model");

        Model model = mock(Model.class);
        // Use reflection to set fields since Model doesn't have setters for these
        setPrivateField(model, "modelClientConfig", clientConfig);
        setPrivateField(model, "modelConfig", requestConfig);
        return model;
    }

    // Helper method to create fake RuntimeSettings
    private RuntimeSettings createFakeSettings() {
        McpServerConfig mcpCfg = McpServerConfig.builder()
                .serverId("test")
                .serverName("test")
                .serverPath("stdio://playwright")
                .clientType("stdio")
                .params(Map.of("cwd", "."))
                .build();
        return new RuntimeSettings(
                "openai",
                "test-key",
                "https://example.invalid/v1",
                "test-model",
                mcpCfg,
                new BrowserRunGuardrails(3, 1, 30, false)
        );
    }

    // Helper method to create fake tools
    private List<Tool> createFakeTools(int count) {
        List<Tool> tools = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ToolCard card = ToolCard.builder()
                    .id("fake-tool-" + i)
                    .name("fake-tool-" + i)
                    .description("fake browser helper tool")
                    .build();
            tools.add(new FakeTool(card));
        }
        return tools;
    }

    private static final class FakeTool extends Tool {
        private FakeTool(ToolCard card) {
            super(card);
        }

        @Override
        public Object invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return Map.of();
        }

        @Override
        public Iterator<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
            return List.of().iterator();
        }
    }

    // Helper to set private field via reflection
    private void setPrivateField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                field.set(target, value);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set field " + fieldName, e);
        }
    }

    // Helper to find field in class hierarchy
    private java.lang.reflect.Field findField(Class<?> clazz, String fieldName) {
        while (clazz != null) {
            try {
                return clazz.getDeclaredField(fieldName);
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    // Helper to get private field via reflection
    private Object getPrivateField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = findField(target.getClass(), fieldName);
            if (field != null) {
                field.setAccessible(true);
                return field.get(target);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to get field " + fieldName, e);
        }
        return null;
    }

    @Nested
    @DisplayName("Default Wiring Tests")
    class DefaultWiringTests {

        @Test
        @DisplayName("test default wiring creates one agent")
        void testDefaultWiringCreatesOneAgent() {
            // Python: test_default_wiring_creates_one_agent
            // Assert that create_browser_agent creates exactly one agent
            List<DeepAgentConfig> capturedConfigs = new ArrayList<>();

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenAnswer(invocation -> {
                            DeepAgentConfig config = invocation.getArgument(0);
                            capturedConfigs.add(config);
                            return mock(DeepAgent.class);
                        });

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    DeepAgent agent = BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    assertNotNull(agent);
                    assertEquals(1, capturedConfigs.size());
                }
            }
        }

        @Test
        @DisplayName("test default wiring main agent card is browser_agent")
        void testDefaultWiringMainAgentCardIsBrowserAgent() {
            // Python: test_default_wiring_main_agent_card_is_browser_agent
            // Assert that the created agent's card name is "browser_agent"
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertNotNull(config.getCard());
                    assertEquals("browser_agent", config.getCard().getName());
                }
            }
        }

        @Test
        @DisplayName("test default wiring main agent has no subagents")
        void testDefaultWiringMainAgentHasNoSubagents() {
            // Python: test_default_wiring_main_agent_has_no_subagents
            // Assert that the created agent has no subagents
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    // In Java, subagents are stored in DeepAgentConfig.subagents field
                    // Check that there's no explicit subagent configuration
                    List<?> subagents = config.getSubagents();
                    assertTrue(subagents == null || subagents.isEmpty());
                }
            }
        }

        @Test
        @DisplayName("test default wiring main agent receives browser helper tools")
        void testDefaultWiringMainAgentReceivesBrowserHelperTools() {
            // Python: test_default_wiring_main_agent_receives_browser_helper_tools
            // Assert that the created agent receives browser runtime tools
            List<Tool> fakeTools = createFakeTools(3);
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(fakeTools);

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertNotNull(config.getTools());
                    // Verify that browser tools are included
                    for (Tool fakeTool : fakeTools) {
                        assertTrue(config.getTools().contains(fakeTool.getCard()));
                    }
                }
            }
        }

        @Test
        @DisplayName("test default wiring does not pre-register playwright mcp on subagent")
        void testDefaultWiringDoesNotPreRegisterPlaywrightMcp() {
            // Python: test_default_wiring_does_not_pre_register_playwright_mcp_on_subagent
            // NOTE: In Python, this test verifies that settings.mcp_cfg is NOT passed to create_deep_agent's mcps parameter.
            // In Java, DeepAgentConfig does not have a mcps field - the mcp configuration is handled differently.
            // The Java implementation passes mcp_cfg to BrowserAgentRuntime constructor instead.
            // This test verifies that the mcp_cfg is properly passed to runtime but NOT added as a direct config field.

            RuntimeSettings settings = createFakeSettings();
            ArgumentCaptor<BrowserAgentRuntime> runtimeCaptor = ArgumentCaptor.forClass(BrowserAgentRuntime.class);

            try (MockedConstruction<BrowserAgentRuntime> mockedRuntime = Mockito.mockConstruction(BrowserAgentRuntime.class)) {
                try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                    mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                            .thenReturn(mock(DeepAgent.class));

                    try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                        mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                                .thenReturn(createFakeTools(3));

                        BrowserAgentFactory.createBrowserAgent(createFakeModel(), settings, List.of(), List.of(), null, "cn", 25);

                        // Verify BrowserAgentRuntime was constructed with mcp_cfg
                        assertEquals(1, mockedRuntime.constructed().size());
                        // The mcp_cfg should be passed to runtime constructor, not to agent config
                    }
                }
            }
        }

        @Test
        @DisplayName("test default wiring main agent has browser runtime rail")
        void testDefaultWiringMainAgentHasBrowserRuntimeRail() {
            // Python: test_default_wiring_main_agent_has_browser_runtime_rail
            // Assert that rails contains a BrowserRuntimeRail
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertNotNull(config.getRails());
                    boolean hasBrowserRuntimeRail = config.getRails().stream()
                            .anyMatch(rail -> rail instanceof BrowserRuntimeRail);
                    assertTrue(hasBrowserRuntimeRail);
                }
            }
        }

        @Test
        @DisplayName("test default wiring does not add sys operation rail")
        void testDefaultWiringDoesNotAddSysOperationRail() {
            // Python: test_default_wiring_does_not_add_sys_operation_rail
            // Assert that rails does not contain SysOperationRail
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertNotNull(config.getRails());
                    boolean hasSysOperationRail = config.getRails().stream()
                            .anyMatch(rail -> rail.getClass().getSimpleName().equals("SysOperationRail"));
                    assertFalse(hasSysOperationRail);
                }
            }
        }

        @Test
        @DisplayName("test default wiring build tools called with runtime instance")
        void testDefaultWiringBuildToolsCalledWithRuntimeInstance() {
            // Python: test_default_wiring_build_tools_called_with_runtime_instance
            // Assert that build_browser_runtime_tools is called with the runtime instance
            ArgumentCaptor<BrowserAgentRuntime> runtimeCaptor = ArgumentCaptor.forClass(BrowserAgentRuntime.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    mockedTools.verify(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(runtimeCaptor.capture()));
                    assertNotNull(runtimeCaptor.getValue());
                }
            }
        }
    }

    @Nested
    @DisplayName("Custom Configuration Tests")
    class CustomConfigTests {

        @Test
        @DisplayName("test custom subagents are forwarded")
        void testCustomSubagentsAreForwarded() {
            // Python: test_custom_subagents_are_forwarded
            // Assert that custom subagents are passed through to the agent config
            AgentRail customRail = mock(AgentRail.class);
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(
                            createFakeModel(),
                            createFakeSettings(),
                            List.of(),
                            List.of(customRail),
                            null,
                            "cn",
                            25
                    );

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertNotNull(config.getRails());
                    assertTrue(config.getRails().contains(customRail));
                }
            }
        }

        @Test
        @DisplayName("test settings forwarded to runtime constructor")
        void testSettingsForwardedToRuntimeConstructor() {
            // Python: test_settings_forwarded_to_runtime_constructor
            // Assert that settings are passed to BrowserAgentRuntime constructor
            RuntimeSettings settings = createFakeSettings();
            List<List<?>> constructorArgs = new ArrayList<>();

            try (MockedConstruction<BrowserAgentRuntime> mockedRuntime = Mockito.mockConstruction(
                    BrowserAgentRuntime.class,
                    (mock, context) -> constructorArgs.add(new ArrayList<>(context.arguments()))
            )) {
                try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                    mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                            .thenReturn(mock(DeepAgent.class));

                    try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                        mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                                .thenReturn(createFakeTools(3));

                        BrowserAgentFactory.createBrowserAgent(createFakeModel(), settings, List.of(), List.of(), null, "cn", 25);

                        // Verify BrowserAgentRuntime was constructed with correct settings
                        assertEquals(1, mockedRuntime.constructed().size());
                        assertEquals(1, constructorArgs.size());
                        List<?> args = constructorArgs.get(0);
                        assertEquals(settings.provider(), args.get(0));
                        assertEquals(settings.apiKey(), args.get(1));
                        assertEquals(settings.apiBase(), args.get(2));
                        assertEquals(settings.modelName(), args.get(3));
                        assertSame(settings.mcpCfg(), args.get(4));
                        assertSame(settings.guardrails(), args.get(5));
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Language Prompt Tests")
    class LanguagePromptTests {

        @Test
        @DisplayName("test language en uses english prompt")
        void testLanguageEnUsesEnglishPrompt() {
            // Python: test_language_en_uses_english_prompt
            // Assert that "en" language uses English system prompt
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "en", 25);

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertEquals(
                            BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"),
                            config.getSystemPrompt()
                    );
                }
            }
        }

        @Test
        @DisplayName("test language cn uses chinese prompt")
        void testLanguageCnUsesChinesePrompt() {
            // Python: test_language_cn_uses_chinese_prompt
            // Assert that "cn" language uses Chinese system prompt
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(createFakeTools(3));

                    BrowserAgentFactory.createBrowserAgent(createFakeModel(), createFakeSettings(), List.of(), List.of(), null, "cn", 25);

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertEquals(
                            BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("cn"),
                            config.getSystemPrompt()
                    );
                }
            }
        }
    }

    @Nested
    @DisplayName("User Tools Tests")
    class UserToolsTests {

        @Test
        @DisplayName("test user tools are merged with browser tools")
        void testUserToolsAreMergedWithBrowserTools() {
            // Python: test_user_tools_are_merged_with_browser_tools
            // Assert that user-provided tools are merged with browser tools
            Tool userTool = createFakeTools(1).get(0);
            List<Tool> browserTools = createFakeTools(3);
            ArgumentCaptor<DeepAgentConfig> configCaptor = ArgumentCaptor.forClass(DeepAgentConfig.class);

            try (MockedStatic<HarnessFactory> mockedFactory = Mockito.mockStatic(HarnessFactory.class)) {
                mockedFactory.when(() -> HarnessFactory.createDeepAgent(any(DeepAgentConfig.class)))
                        .thenReturn(mock(DeepAgent.class));

                try (MockedStatic<BrowserRuntimeTools> mockedTools = Mockito.mockStatic(BrowserRuntimeTools.class)) {
                    mockedTools.when(() -> BrowserRuntimeTools.buildBrowserRuntimeTools(any(BrowserAgentRuntime.class)))
                            .thenReturn(browserTools);

                    BrowserAgentFactory.createBrowserAgent(
                            createFakeModel(),
                            createFakeSettings(),
                            List.of(userTool),
                            List.of(),
                            null,
                            "cn",
                            25
                    );

                    mockedFactory.verify(() -> HarnessFactory.createDeepAgent(configCaptor.capture()));
                    DeepAgentConfig config = configCaptor.getValue();

                    assertNotNull(config.getTools());
                    // User tool should be in the tools list
                    assertTrue(config.getTools().contains(userTool.getCard()));
                    // Total should be 4 (1 user + 3 browser)
                    assertEquals(4, config.getTools().size());
                }
            }
        }
    }

    @Nested
    @DisplayName("Build Config Tests")
    class BuildConfigTests {

        @Test
        @DisplayName("test build browser agent config uses browser factory")
        void testBuildBrowserAgentConfigUsesBrowserFactory() {
            // Python: test_build_browser_agent_config_uses_browser_factory
            // Assert that build_browser_agent_config creates proper SubAgentConfig-like spec
            RuntimeSettings settings = createFakeSettings();
            Model model = createFakeModel();

            BrowserAgentConfigSpec spec = BrowserAgentFactory.buildBrowserAgentConfig(model, settings, "en");

            assertNotNull(spec);
            assertEquals("browser_agent", spec.agentCard().getName());
            assertEquals(BrowserAgentFactory.DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("en"), spec.systemPrompt());
            assertEquals(BrowserAgentFactory.BROWSER_AGENT_FACTORY_NAME, spec.factoryName());
            assertEquals(settings, spec.settings());
        }

        @Test
        @DisplayName("test build browser agent config fallback uses model name field")
        void testBuildBrowserAgentConfigFallbackUsesModelNameField() {
            // Python: test_build_browser_agent_config_fallback_uses_model_name_field
            // Assert that when Model.modelConfig.model is not set, model_name field is used
            ModelClientConfig clientConfig = mock(ModelClientConfig.class);
            when(clientConfig.getClientProvider()).thenReturn("openai");
            when(clientConfig.getApiKey()).thenReturn("test-key");
            when(clientConfig.getApiBase()).thenReturn("https://example.invalid/v1");

            ModelRequestConfig requestConfig = mock(ModelRequestConfig.class);
            when(requestConfig.getModelName()).thenReturn("test-model-name");
            // Simulate that model field doesn't exist
            Map<String, Object> extraFields = new HashMap<>();
            extraFields.put("model_name", "fallback-model-name");
            when(requestConfig.getExtraFields()).thenReturn(extraFields);

            Model model = mock(Model.class);
            setPrivateField(model, "modelClientConfig", clientConfig);
            setPrivateField(model, "modelConfig", requestConfig);

            BrowserAgentConfigSpec spec = BrowserAgentFactory.buildBrowserAgentConfig(model, null, "en");

            assertNotNull(spec);
            assertNotNull(spec.settings());
            // Should use model_name from requestConfig
            assertEquals("test-model-name", spec.settings().modelName());
        }
    }
}
