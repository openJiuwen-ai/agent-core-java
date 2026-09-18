/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.dev_tools.agent_builder.builders.llm_agent;

import com.openjiuwen.core.common.exception.ApplicationError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.logging.LogManager;
import com.openjiuwen.core.common.logging.LoggerProtocol;
import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.foundation.llm.Model;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.BaseMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletionException;

/**
 * Requirement clarifier for agent-builder LLM agents.
 *
 * <p>Mirrors Python's {@code Clarifier} in
 * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/clarifier.py}.</p>
 */
public class Clarifier {
    private static final LoggerProtocol LOGGER = LogManager.getLogger("agent_builder");
    private static final String RESOURCE_PLAN_HEADER = "## Agent资源规划";
    private static final Map<String, ResourceConfig> RESOURCE_CONFIGS = resourceConfigs();

    public static final Map<String, Map<String, String>> RESOURCE_CONFIG = resourceConfigView();

    private final Model llm;

    public Clarifier(Model llm) {
        this.llm = Objects.requireNonNull(llm, "llm");
    }

    public Model getLlm() {
        return llm;
    }

    public static ParseResult parseResourceOutput(String resourceOutput, Map<String, Object> availableResources) {
        Objects.requireNonNull(resourceOutput, "resourceOutput");
        if (!resourceOutput.contains(RESOURCE_PLAN_HEADER)) {
            return new ParseResult("", new LinkedHashMap<>());
        }

        String resourcePlanning = resourceOutput.substring(
                resourceOutput.indexOf(RESOURCE_PLAN_HEADER) + RESOURCE_PLAN_HEADER.length()).strip();
        List<String> displayContent = new ArrayList<>();
        Map<String, List<String>> idDict = new LinkedHashMap<>();

        for (Map.Entry<String, ResourceConfig> entry : RESOURCE_CONFIGS.entrySet()) {
            String resourceType = entry.getKey();
            ResourceConfig config = entry.getValue();
            String sectionStart = "【选择的" + config.label() + "】";
            if (!resourcePlanning.contains(sectionStart)) {
                continue;
            }

            String sectionContent = extractSection(resourcePlanning, sectionStart);
            try {
                Object parsed = new PythonLiteralParser(sectionContent).parse();
                if (!(parsed instanceof List<?> resourceList)) {
                    continue;
                }

                List<String> validResources = new ArrayList<>();
                List<String> idList = new ArrayList<>();
                String availableKey = "plugin".equals(resourceType) ? "plugins" : resourceType;
                Set<String> availableIds = availableIds(availableResources, availableKey, config.idKey());

                int index = 1;
                for (Object resource : resourceList) {
                    int currentIndex = index++;
                    if (!(resource instanceof Map<?, ?> map)) {
                        continue;
                    }
                    String name = asString(map.get(config.nameKey()));
                    String desc = asString(map.get(config.descKey()));
                    Object resourceIdObject = map.get(config.idKey());
                    String resourceId = resourceIdObject == null ? null : String.valueOf(resourceIdObject);

                    if (resourceId != null && availableIds.contains(resourceId)) {
                        if (!name.isEmpty() && !desc.isEmpty()) {
                            validResources.add(currentIndex + ". " + name + ": " + desc);
                        }
                        idList.add(resourceId);
                    } else {
                        LOGGER.warning("Resource ID {} not in available resources, resource_type={}",
                                resourceId, resourceType);
                    }
                }

                if (!validResources.isEmpty()) {
                    displayContent.add(sectionStart + "\n" + String.join("\n", validResources));
                    if (!idList.isEmpty()) {
                        idDict.put(resourceType, idList);
                    }
                }
            } catch (Exception exception) {
                String message = "NL2LLM Agent requirement clarification resource parsing exception: "
                        + exceptionMessage(exception);
                LOGGER.error("Resource parsing failed, error={}, resource_type={}",
                        exceptionMessage(exception), resourceType);
                throw new ApplicationError(
                        StatusCode.AGENT_BUILDER_RESOURCE_PARSE_ERROR,
                        message,
                        Map.of("resource_type", resourceType, "error", exceptionMessage(exception)),
                        exception,
                        Map.of("resource_type", resourceType, "reason", exceptionMessage(exception))
                );
            }
        }

        return new ParseResult(String.join("\n", displayContent), idDict);
    }

    public ClarifyResult clarify(String messages, Map<String, Object> resource) {
        List<BaseMessage> userMessages = LlmAgentPrompts.USER_PROMPT_TEMPLATE.format(Map.of(
                "user_messages", messages == null ? "" : messages
        )).toMessages();
        List<BaseMessage> factorMessages = new ArrayList<>();
        factorMessages.add(new SystemMessage(LlmAgentPrompts.FACTOR_SYSTEM_PROMPT));
        factorMessages.addAll(userMessages);

        String factorOutput = invokeContent(factorMessages);

        Object resourceDump = JsonUtils.safeJsonDumps(resource);
        List<BaseMessage> resourceUserMessages = LlmAgentPrompts.RESOURCE_USER_PROMPT_TEMPLATE.format(Map.of(
                "agent_factor_info", factorOutput,
                "resource", String.valueOf(resourceDump)
        )).toMessages();
        List<BaseMessage> resourceMessages = new ArrayList<>();
        resourceMessages.add(new SystemMessage(LlmAgentPrompts.RESOURCE_SYSTEM_PROMPT));
        resourceMessages.addAll(resourceUserMessages);

        String resourceOutput = invokeContent(resourceMessages);
        ParseResult parsed = parseResourceOutput(resourceOutput, resource);
        return new ClarifyResult(factorOutput, parsed.displayContent(), parsed.resourceIdDict());
    }

    private String invokeContent(List<BaseMessage> messages) {
        AssistantMessage response = llm.invoke(messages).toCompletableFuture().join();
        return Objects.toString(response == null ? null : response.getContent(), "");
    }

    private static String extractSection(String resourcePlanning, String sectionStart) {
        int start = resourcePlanning.indexOf(sectionStart) + sectionStart.length();
        String afterStart = resourcePlanning.substring(start);
        int nextSection = afterStart.indexOf("【选择");
        if (nextSection >= 0) {
            return afterStart.substring(0, nextSection).strip();
        }
        return afterStart.strip();
    }

    private static Set<String> availableIds(Map<String, Object> availableResources, String availableKey, String idKey) {
        Set<String> ids = new LinkedHashSet<>();
        if (availableResources == null || !availableResources.containsKey(availableKey)) {
            return ids;
        }
        Object resources = availableResources.get(availableKey);
        if (!(resources instanceof List<?> list)) {
            return ids;
        }
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                Object id = map.get(idKey);
                if (id != null) {
                    ids.add(String.valueOf(id));
                }
            }
        }
        return ids;
    }

    private static Map<String, ResourceConfig> resourceConfigs() {
        Map<String, ResourceConfig> config = new LinkedHashMap<>();
        config.put("plugin", new ResourceConfig("插件", "tool_id", "tool_name", "tool_desc"));
        config.put("knowledge", new ResourceConfig("知识库", "knowledge_id", "knowledge_name", "knowledge_desc"));
        config.put("workflow", new ResourceConfig("工作流", "workflow_id", "workflow_name", "workflow_desc"));
        return Collections.unmodifiableMap(config);
    }

    private static Map<String, Map<String, String>> resourceConfigView() {
        Map<String, Map<String, String>> view = new LinkedHashMap<>();
        for (Map.Entry<String, ResourceConfig> entry : RESOURCE_CONFIGS.entrySet()) {
            ResourceConfig config = entry.getValue();
            Map<String, String> item = new LinkedHashMap<>();
            item.put("label", config.label());
            item.put("id_key", config.idKey());
            item.put("name_key", config.nameKey());
            item.put("desc_key", config.descKey());
            view.put(entry.getKey(), Collections.unmodifiableMap(item));
        }
        return Collections.unmodifiableMap(view);
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String exceptionMessage(Throwable exception) {
        Throwable effective = exception;
        if (effective instanceof CompletionException && effective.getCause() != null) {
            effective = effective.getCause();
        }
        String message = effective.getMessage();
        return message == null ? effective.toString() : message;
    }

    /**
     * Mirrors Python's parsed resource configuration entries in
     * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/clarifier.py}.
     */
    private record ResourceConfig(String label, String idKey, String nameKey, String descKey) {
    }

    /**
     * Mirrors Python's {@code parse_resource_output(...)} tuple in
     * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/clarifier.py}.
     */
    public record ParseResult(String displayContent, Map<String, List<String>> resourceIdDict) {
    }

    /**
     * Mirrors Python's {@code clarify(...)} tuple in
     * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/clarifier.py}.
     */
    public record ClarifyResult(String factorOutput, String displayResource,
                                Map<String, List<String>> resourceIdDict) {
    }

    /**
     * Minimal parser for Python literal list/dict/string output used by
     * {@code ast.literal_eval} in
     * {@code openjiuwen/dev_tools/agent_builder/builders/llm_agent/clarifier.py}.
     */
    private static final class PythonLiteralParser {
        private final String text;
        private int index;

        private PythonLiteralParser(String text) {
            this.text = text == null ? "" : text;
        }

        private Object parse() {
            Object value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw new IllegalArgumentException("unexpected trailing text at " + index);
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw new IllegalArgumentException("unexpected end of literal");
            }
            char current = text.charAt(index);
            if (current == '[') {
                return parseList();
            }
            if (current == '{') {
                return parseMap();
            }
            if (current == '\'' || current == '"') {
                return parseString();
            }
            return parseBareValue();
        }

        private List<Object> parseList() {
            expect('[');
            List<Object> result = new ArrayList<>();
            skipWhitespace();
            if (peek(']')) {
                index++;
                return result;
            }
            while (true) {
                result.add(parseValue());
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    if (peek(']')) {
                        index++;
                        return result;
                    }
                    continue;
                }
                expect(']');
                return result;
            }
        }

        private Map<String, Object> parseMap() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            skipWhitespace();
            if (peek('}')) {
                index++;
                return result;
            }
            while (true) {
                Object key = parseValue();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                result.put(String.valueOf(key), value);
                skipWhitespace();
                if (peek(',')) {
                    index++;
                    skipWhitespace();
                    if (peek('}')) {
                        index++;
                        return result;
                    }
                    continue;
                }
                expect('}');
                return result;
            }
        }

        private String parseString() {
            char quote = text.charAt(index++);
            StringBuilder builder = new StringBuilder();
            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == quote) {
                    return builder.toString();
                }
                if (current == '\\' && index < text.length()) {
                    char escaped = text.charAt(index++);
                    builder.append(switch (escaped) {
                        case 'n' -> '\n';
                        case 'r' -> '\r';
                        case 't' -> '\t';
                        default -> escaped;
                    });
                } else {
                    builder.append(current);
                }
            }
            throw new IllegalArgumentException("unterminated string literal");
        }

        private Object parseBareValue() {
            int start = index;
            while (index < text.length()) {
                char current = text.charAt(index);
                if (Character.isWhitespace(current) || current == ',' || current == ']' || current == '}') {
                    break;
                }
                index++;
            }
            String token = text.substring(start, index);
            if ("None".equals(token) || "null".equals(token)) {
                return null;
            }
            if ("True".equals(token) || "true".equals(token)) {
                return true;
            }
            if ("False".equals(token) || "false".equals(token)) {
                return false;
            }
            if (token.isEmpty()) {
                throw new IllegalArgumentException("empty token at " + start);
            }
            return token;
        }

        private void expect(char expected) {
            skipWhitespace();
            if (index >= text.length() || text.charAt(index) != expected) {
                throw new IllegalArgumentException("expected '" + expected + "' at " + index);
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }
    }
}
