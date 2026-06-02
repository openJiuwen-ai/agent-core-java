/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;

/**
 * A2A agent card adapter — bridges openjiuwen agent cards to A2A format.
 * <p>
 * Mirrors Python's {@code A2AAgentCardAdapter} in
 * {@code openjiuwen.extensions.a2a.a2a_agentcard_adapter}.
 */
public class A2AAgentCardAdapter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final List<String> DEFAULT_INPUT_MODES = List.of("text/plain", "application/json");
    private static final List<String> DEFAULT_OUTPUT_MODES = List.of("text/plain", "application/json");

    public record SupportedInterface(String url, String protocolBinding, String protocolVersion, String tenant) {
    }

    public static final class A2aAgentCard {
        private final String name;
        private final String description;
        private final List<String> defaultInputModes;
        private final List<String> defaultOutputModes;
        private final List<SupportedInterface> supportedInterfaces;

        public A2aAgentCard(
                String name,
                String description,
                List<String> defaultInputModes,
                List<String> defaultOutputModes,
                List<SupportedInterface> supportedInterfaces) {
            this.name = name;
            this.description = description;
            this.defaultInputModes = List.copyOf(defaultInputModes);
            this.defaultOutputModes = List.copyOf(defaultOutputModes);
            this.supportedInterfaces = List.copyOf(supportedInterfaces);
        }

        public String getName() {
            return name;
        }

        public String getDescription() {
            return description;
        }

        public List<String> getDefaultInputModes() {
            return defaultInputModes;
        }

        public List<String> getDefaultOutputModes() {
            return defaultOutputModes;
        }

        public List<SupportedInterface> getSupportedInterfaces() {
            return supportedInterfaces;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("name", name);
            value.put("description", description);
            value.put("default_input_modes", defaultInputModes);
            value.put("default_output_modes", defaultOutputModes);
            value.put("supported_interfaces", supportedInterfaces);
            return value;
        }
    }

    public static A2aAgentCard toA2aAgentCard(AgentCard card) {
        return toA2aAgentCard(card, List.of(), null, null, null, null);
    }

    public static A2aAgentCard toA2aAgentCard(
            AgentCard card,
            List<SupportedInterface> supportedInterfaces,
            String interfaceUrl,
            String protocolBinding,
            String protocolVersion,
            String tenant) {
        if (card == null) {
            return null;
        }
        List<SupportedInterface> interfaces = new ArrayList<>(supportedInterfaces != null
                ? supportedInterfaces : List.of());
        if (interfaces.isEmpty() && interfaceUrl != null) {
            interfaces.add(new SupportedInterface(
                    interfaceUrl,
                    protocolBinding != null ? protocolBinding : "HTTP+JSON",
                    protocolVersion != null ? protocolVersion : "1.0",
                    tenant));
        }
        String description = buildDescription(card.getDescription(), card.getInputParams(), card.getOutputParams());
        return new A2aAgentCard(
                card.getName() != null ? card.getName() : "",
                description,
                DEFAULT_INPUT_MODES,
                DEFAULT_OUTPUT_MODES,
                interfaces);
    }

    public static AgentCard fromA2aAgentCard(A2aAgentCard a2aAgentCard) {
        if (a2aAgentCard == null) {
            return AgentCard.builder().build();
        }
        return AgentCard.builder()
                .name(a2aAgentCard.getName())
                .description(a2aAgentCard.getDescription())
                .build();
    }

    private static String buildDescription(String baseDescription, Object inputParams, Object outputParams) {
        List<String> sections = new ArrayList<>();
        String base = baseDescription != null ? baseDescription.strip() : "";
        if (!base.isEmpty()) {
            sections.add(base);
        }
        String inputText = serializeParamPayload(inputParams);
        String outputText = serializeParamPayload(outputParams);
        if (!inputText.isEmpty()) {
            sections.add("[input_params] " + inputText);
        }
        if (!outputText.isEmpty()) {
            sections.add("[output_params] " + outputText);
        }
        return String.join("\n", sections).strip();
    }

    private static String serializeParamPayload(Object value) {
        if (value == null) {
            return "";
        }
        Object payload = value;
        if (value instanceof Class<?> cls) {
            payload = Map.of("type", cls.getSimpleName());
        } else if (!(value instanceof Map<?, ?>)) {
            payload = Map.of("value", String.valueOf(value));
        }
        try {
            return OBJECT_MAPPER
                    .writerWithDefaultPrettyPrinter()
                    .writeValueAsString(payload)
                    .replace("\r\n", "\n");
        } catch (JsonProcessingException e) {
            return String.valueOf(payload);
        }
    }

    /** Convert an openjiuwen agent card to A2A agent card. */
    public static Map<String, Object> toA2aCard(Map<String, Object> agentCard) {
        Map<String, Object> a2aCard = new LinkedHashMap<>();
        a2aCard.put("name", agentCard.getOrDefault("name", "unknown"));
        a2aCard.put("description", agentCard.getOrDefault("description", ""));
        a2aCard.put("url", agentCard.getOrDefault("endpoint", ""));
        a2aCard.put("default_input_modes", List.of("text/plain", "application/json"));
        a2aCard.put("default_output_modes", List.of("text/plain", "application/json"));
        return a2aCard;
    }

    /** Convert an A2A agent card to openjiuwen format. */
    public static Map<String, Object> fromA2aCard(Map<String, Object> a2aCard) {
        Map<String, Object> agentCard = new LinkedHashMap<>();
        agentCard.put("name", a2aCard.getOrDefault("name", "unknown"));
        agentCard.put("description", a2aCard.getOrDefault("description", ""));
        return agentCard;
    }
}
