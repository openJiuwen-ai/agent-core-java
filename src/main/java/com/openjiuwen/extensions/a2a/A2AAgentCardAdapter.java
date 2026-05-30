/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.*;

/**
 * A2A agent card adapter — bridges openjiuwen agent cards to A2A format.
 * <p>
 * Mirrors Python's {@code A2AAgentCardAdapter} in
 * {@code openjiuwen.extensions.a2a.a2a_agentcard_adapter}.
 */
public class A2AAgentCardAdapter {

    public record SupportedInterface(String url, String protocolBinding, String protocolVersion, String tenant) {
    }

    public static final class A2aAgentCard {
        private final String name;
        private final String description;
        private final List<String> defaultInputModes;
        private final List<String> defaultOutputModes;
        private final List<SupportedInterface> supportedInterfaces;

        private A2aAgentCard(
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
        List<SupportedInterface> interfaces = new ArrayList<>(supportedInterfaces != null
                ? supportedInterfaces : List.of());
        if (interfaces.isEmpty() && interfaceUrl != null) {
            interfaces.add(new SupportedInterface(interfaceUrl, protocolBinding, protocolVersion, tenant));
        }
        String description = card.getDescription()
                + "\n[input_params] " + card.getInputParamsAsMap()
                + "\n[output_params] " + card.getOutputParamsAsMap();
        return new A2aAgentCard(
                card.getName(),
                description,
                List.of("text/plain", "application/json"),
                List.of("text/plain", "application/json"),
                interfaces);
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
