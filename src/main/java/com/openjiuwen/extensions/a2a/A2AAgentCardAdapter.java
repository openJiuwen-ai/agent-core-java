/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Adapter between openjiuwen AgentCard and A2A AgentCard.
 *
 * <p>Mirrors Python's {@code A2AAgentCardAdapter} in
 * {@code openjiuwen/extensions/a2a/a2a_agentcard_adapter.py}.</p>
 */
public final class A2AAgentCardAdapter {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final List<String> DEFAULT_INPUT_MODES = List.of("text/plain", "application/json");
    public static final List<String> DEFAULT_OUTPUT_MODES = List.of("text/plain", "application/json");

    private A2AAgentCardAdapter() {
    }

    public static A2aAgentCard toA2aAgentCard(Object agentCard) {
        return toA2aAgentCard(agentCard, null, "HTTP+JSON", "1.0", null, null);
    }

    public static A2aAgentCard toA2aAgentCard(Object agentCard,
                                             String interfaceUrl,
                                             String protocolBinding,
                                             String protocolVersion,
                                             String tenant,
                                             Iterable<?> supportedInterfaces) {
        if (!(agentCard instanceof AgentCard card)) {
            return null;
        }
        String description = buildDescription(card.getDescription(), card.getInputParams(), card.getOutputParams());
        A2aAgentCard a2aCard = new A2aAgentCard(
                card.getName() == null ? "" : card.getName(),
                description,
                new AgentCapabilities(true, false),
                DEFAULT_INPUT_MODES,
                DEFAULT_OUTPUT_MODES);

        List<AgentInterface> interfaces = buildInterfaces(
                interfaceUrl,
                protocolBinding == null ? "HTTP+JSON" : protocolBinding,
                protocolVersion == null ? "1.0" : protocolVersion,
                tenant,
                supportedInterfaces);
        if (!interfaces.isEmpty()) {
            a2aCard.getSupportedInterfaces().addAll(interfaces);
        }
        return a2aCard;
    }

    public static A2aAgentCard to_a2a_agent_card(Object agentCard,
                                                String interfaceUrl,
                                                String protocolBinding,
                                                String protocolVersion,
                                                String tenant,
                                                Iterable<?> supportedInterfaces) {
        return toA2aAgentCard(agentCard, interfaceUrl, protocolBinding, protocolVersion, tenant, supportedInterfaces);
    }

    public static AgentCard fromA2aAgentCard(A2aAgentCard a2aAgentCard) {
        Objects.requireNonNull(a2aAgentCard, "a2aAgentCard");
        AgentCard card = new AgentCard();
        card.setName(a2aAgentCard.getName());
        card.setDescription(a2aAgentCard.getDescription());
        return card;
    }

    public static AgentCard from_a2a_agent_card(A2aAgentCard a2aAgentCard) {
        return fromA2aAgentCard(a2aAgentCard);
    }

    static String serializeParamPayload(Object value) {
        if (value == null) {
            return "";
        }
        Object payload;
        if (value instanceof Map<?, ?>) {
            payload = value;
        } else {
            Object schema = callModelJsonSchema(value);
            if (schema != null) {
                payload = schema;
            } else if (value instanceof Class<?> type) {
                payload = Map.of("type", type.getSimpleName());
            } else {
                payload = Map.of("value", String.valueOf(value));
            }
        }
        return pythonJson(payload);
    }

    static String buildDescription(String baseDescription, Object inputParams, Object outputParams) {
        List<String> sections = new ArrayList<>();
        String base = baseDescription == null ? "" : baseDescription.strip();
        sections.add(base);
        String inputText = serializeParamPayload(inputParams);
        String outputText = serializeParamPayload(outputParams);
        if (!inputText.isEmpty()) {
            sections.add("[input_params] " + inputText);
        }
        if (!outputText.isEmpty()) {
            sections.add("[output_params] " + outputText);
        }
        return sections.stream()
                .filter(part -> !part.isEmpty())
                .collect(Collectors.joining("\n"))
                .strip();
    }

    static List<AgentInterface> buildInterfaces(String interfaceUrl,
                                                String protocolBinding,
                                                String protocolVersion,
                                                String tenant,
                                                Iterable<?> supportedInterfaces) {
        List<AgentInterface> result = new ArrayList<>();
        if (supportedInterfaces != null) {
            for (Object item : supportedInterfaces) {
                if (!(item instanceof Map<?, ?> rawMap)) {
                    continue;
                }
                Object url = rawMap.get("url");
                Object binding = rawMap.get("protocol_binding");
                Object version = rawMap.get("protocol_version");
                if (isBlank(url) || isBlank(binding) || isBlank(version)) {
                    continue;
                }
                AgentInterface agentInterface = new AgentInterface(
                        String.valueOf(url),
                        String.valueOf(binding),
                        String.valueOf(version));
                Object itemTenant = rawMap.get("tenant");
                if (!isBlank(itemTenant)) {
                    agentInterface.setTenant(String.valueOf(itemTenant));
                }
                result.add(agentInterface);
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        if (interfaceUrl != null && !interfaceUrl.isEmpty()) {
            AgentInterface agentInterface = new AgentInterface(interfaceUrl, protocolBinding, protocolVersion);
            if (tenant != null && !tenant.isEmpty()) {
                agentInterface.setTenant(tenant);
            }
            result.add(agentInterface);
        }
        return result;
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isEmpty();
    }

    private static Object callModelJsonSchema(Object value) {
        try {
            Method method = value.getClass().getMethod("modelJsonSchema");
            return method.invoke(value);
        } catch (NoSuchMethodException ignored) {
            return null;
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalArgumentException("modelJsonSchema failed", exception);
        }
    }

    private static String pythonJson(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> String.valueOf(entry.getKey())))
                    .map(entry -> quote(String.valueOf(entry.getKey())) + ": " + pythonJson(entry.getValue()))
                    .collect(Collectors.joining(", ", "{", "}"));
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(A2AAgentCardAdapter::pythonJson)
                    .collect(Collectors.joining(", ", "[", "]"));
        }
        if (value == null) {
            return "null";
        }
        if (value instanceof String text) {
            return quote(text);
        }
        if (value instanceof Boolean bool) {
            return bool ? "true" : "false";
        }
        if (value instanceof Number) {
            return String.valueOf(value);
        }
        return quote(String.valueOf(value));
    }

    private static String quote(String text) {
        try {
            return MAPPER.writeValueAsString(text);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Failed to encode JSON string", exception);
        }
    }

    public static final class A2aAgentCard {
        private final String name;
        private final String description;
        private final AgentCapabilities capabilities;
        private final List<String> defaultInputModes;
        private final List<String> defaultOutputModes;
        private final List<AgentInterface> supportedInterfaces = new ArrayList<>();

        public A2aAgentCard(String name,
                            String description,
                            AgentCapabilities capabilities,
                            List<String> defaultInputModes,
                            List<String> defaultOutputModes) {
            this.name = name;
            this.description = description;
            this.capabilities = capabilities;
            this.defaultInputModes = new ArrayList<>(defaultInputModes);
            this.defaultOutputModes = new ArrayList<>(defaultOutputModes);
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public AgentCapabilities getCapabilities() {
            return capabilities;
        }

        public List<String> getDefaultInputModes() {
            return defaultInputModes;
        }

        public List<String> getDefaultOutputModes() {
            return defaultOutputModes;
        }

        public List<AgentInterface> getSupportedInterfaces() {
            return supportedInterfaces;
        }
    }

    public static final class AgentCapabilities {
        private final boolean streaming;
        private final boolean pushNotifications;

        public AgentCapabilities(boolean streaming, boolean pushNotifications) {
            this.streaming = streaming;
            this.pushNotifications = pushNotifications;
        }

        public boolean isStreaming() {
            return streaming;
        }

        public boolean isPushNotifications() {
            return pushNotifications;
        }
    }

    public static final class AgentInterface {
        private final String url;
        private final String protocolBinding;
        private final String protocolVersion;
        private String tenant;

        public AgentInterface(String url, String protocolBinding, String protocolVersion) {
            this.url = url;
            this.protocolBinding = protocolBinding;
            this.protocolVersion = protocolVersion;
        }

        public String getUrl() {
            return url;
        }

        public String getProtocolBinding() {
            return protocolBinding;
        }

        public String getProtocolVersion() {
            return protocolVersion;
        }

        public String getTenant() {
            return tenant;
        }

        public void setTenant(String tenant) {
            this.tenant = tenant;
        }
    }

}
