package com.openjiuwen.harness.subagents.browser_agent;

import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.tool.Tool;
import com.openjiuwen.core.singleagent.rail.AgentRail;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.harness.DeepAgent;
import com.openjiuwen.harness.DeepAgentConfig;
import com.openjiuwen.harness.HarnessFactory;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserAgentRuntime;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRunGuardrails;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeConfig;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.BrowserRuntimeRail;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.RuntimeSettings;
import com.openjiuwen.harness.tools.browser_move.playwright_runtime.runtime_tools.BrowserRuntimeTools;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's browser-agent factory helpers in
 * {@code openjiuwen.harness.subagents.browser_agent}.
 */
public final class BrowserAgentFactory {

    public static final String BROWSER_AGENT_FACTORY_NAME = "browser_agent";
    public static final Map<String, String> DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT = Map.of(
            "cn", "你是浏览器自动化代理，负责执行网页任务。请使用浏览器工具完成导航、交互和信息提取。每次请求优先发起一次完整的浏览器任务调用。请如实、简洁地汇报结果。",
            "en", "You are a browser automation agent responsible for executing web tasks directly. Plan and decide at this agent level, then use Playwright browser tools to navigate, click, type, select, inspect, and extract information. Use browser_custom_action only for deterministic helper actions that are awkward to express with the primitive browser tools. Do not assume a nested browser worker or browser_run_task wrapper exists. Avoid redundant actions, preserve session continuity, and only claim completion when the requested browser outcome is actually evidenced."
    );

    private static final Map<String, String> DEFAULT_BROWSER_AGENT_DESCRIPTION = Map.of(
            "cn", "专用浏览器子代理，直接使用 Playwright MCP 工具执行网页任务。",
            "en", "Dedicated browser subagent that directly controls the browser with Playwright MCP tools."
    );

    private BrowserAgentFactory() {
    }

    public static BrowserAgentConfigSpec buildBrowserAgentConfig(Model model) {
        return buildBrowserAgentConfig(model, null, "cn");
    }

    public static BrowserAgentConfigSpec buildBrowserAgentConfig(Model model, RuntimeSettings settings, String language) {
        String resolvedLanguage = resolveLanguage(language);
        RuntimeSettings resolvedSettings = resolveRuntimeSettings(model, settings);
        AgentCard card = AgentCard.builder()
                .name("browser_agent")
                .description(DEFAULT_BROWSER_AGENT_DESCRIPTION.getOrDefault(resolvedLanguage, DEFAULT_BROWSER_AGENT_DESCRIPTION.get("cn")))
                .build();
        return new BrowserAgentConfigSpec(
                card,
                DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.getOrDefault(resolvedLanguage, DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("cn")),
                List.of(),
                List.of(),
                model,
                false,
                25,
                BROWSER_AGENT_FACTORY_NAME,
                Map.of("settings", resolvedSettings)
        );
    }

    public static DeepAgent createBrowserAgent(Model model) {
        return createBrowserAgent(model, null, List.of(), List.of(), null, "cn", 25);
    }

    public static DeepAgent createBrowserAgent(
            Model model,
            RuntimeSettings settings,
            List<Tool> tools,
            List<AgentRail> rails,
            AgentCard card,
            String language,
            int maxIterations
    ) {
        String resolvedLanguage = resolveLanguage(language);
        RuntimeSettings resolvedSettings = resolveRuntimeSettings(model, settings);
        BrowserAgentRuntime browserBackend = new BrowserAgentRuntime(
                resolvedSettings.provider(),
                resolvedSettings.apiKey(),
                resolvedSettings.apiBase(),
                resolvedSettings.modelName(),
                resolvedSettings.mcpCfg(),
                resolvedSettings.guardrails()
        );
        List<Tool> injectedTools = BrowserRuntimeTools.buildBrowserRuntimeTools(browserBackend);
        List<Tool> finalTools = new ArrayList<>();
        if (tools != null) {
            finalTools.addAll(tools);
        }
        finalTools.addAll(injectedTools);

        List<AgentRail> finalRails = new ArrayList<>();
        if (rails != null) {
            finalRails.addAll(rails);
        }
        finalRails.add(new BrowserRuntimeRail(browserBackend));

        AgentCard finalCard = card != null ? card : AgentCard.builder()
                .name("browser_agent")
                .description(DEFAULT_BROWSER_AGENT_DESCRIPTION.getOrDefault(resolvedLanguage, DEFAULT_BROWSER_AGENT_DESCRIPTION.get("cn")))
                .build();

        DeepAgentConfig config = new DeepAgentConfig();
        config.setCard(finalCard);
        config.setSystemPrompt(DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.getOrDefault(resolvedLanguage, DEFAULT_BROWSER_AGENT_SYSTEM_PROMPT.get("cn")));
        config.setMaxIterations(maxIterations);
        config.setTools(finalTools.stream().map(Tool::getCard).toList());
        config.setRails(finalRails);

        if (model != null) {
            assignModelConfig(config, model);
        }

        return HarnessFactory.createDeepAgent(config);
    }

    private static RuntimeSettings resolveRuntimeSettings(Model model, RuntimeSettings settings) {
        if (settings != null) {
            return settings;
        }
        if (model != null) {
            ModelClientConfig clientConfig = readField(model, "modelClientConfig", ModelClientConfig.class);
            ModelRequestConfig requestConfig = readField(model, "modelConfig", ModelRequestConfig.class);
            if (clientConfig != null) {
                String modelName = requestConfig != null
                        ? firstNonBlank(requestConfig.getModelName(), asString(requestConfig.getExtraFields().get("model_name")))
                        : "";
                return new RuntimeSettings(
                        clientConfig.getClientProvider(),
                        clientConfig.getApiKey(),
                        clientConfig.getApiBase(),
                        modelName,
                        BrowserRuntimeConfig.buildPlaywrightMcpConfig(BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S),
                        new BrowserRunGuardrails(20, 2, BrowserRuntimeConfig.DEFAULT_BROWSER_TIMEOUT_S, true)
                );
            }
        }
        return BrowserRuntimeConfig.buildRuntimeSettings();
    }

    private static void assignModelConfig(DeepAgentConfig config, Model model) {
        ModelClientConfig clientConfig = readField(model, "modelClientConfig", ModelClientConfig.class);
        ModelRequestConfig requestConfig = readField(model, "modelConfig", ModelRequestConfig.class);
        config.setModelClientConfig(clientConfig);
        config.setModelRequestConfig(requestConfig);
    }

    private static String resolveLanguage(String language) {
        if ("en".equalsIgnoreCase(language)) {
            return "en";
        }
        return "cn";
    }

    private static String firstNonBlank(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : fallback != null ? fallback : "";
    }

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(Object target, String fieldName, Class<T> type) {
        if (target == null) {
            return null;
        }
        Class<?> current = target.getClass();
        while (current != null) {
            try {
                java.lang.reflect.Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                Object value = field.get(target);
                return type.isInstance(value) ? (T) value : null;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new IllegalStateException("Failed to read field '" + fieldName + "'", e);
            }
        }
        return null;
    }
}
