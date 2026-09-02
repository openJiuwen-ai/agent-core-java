/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.common.security.JsonUtils;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Adapter between openjiuwen AgentCard and A2A protocol card payloads.
 * 
 * @since 0.1.7
 */
public final class A2AAgentCardAdapter {
    private static final List<String> DEFAULT_INPUT_MODES = List.of("text/plain", "application/json");

    /**
     * List.of.
     * 
     * @since 0.1.7
     */
    private static final List<String> DEFAULT_OUTPUT_MODES = List.of("text/plain", "application/json");

    /**
     * A2AAgentCardAdapter.
     * 
     * @since 0.1.7
     */
    private A2AAgentCardAdapter() {
    }

    /**
     * toA2ACard.
     * 
     * @param agentCard agentCard
     * @param interfaceUrl interfaceUrl
     * @param protocolBinding protocolBinding
     * @param protocolVersion protocolVersion
     * @param tenant tenant
     * @param supportedInterfaces supportedInterfaces
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> toA2ACard(AgentCard agentCard, String interfaceUrl, String protocolBinding,
            String protocolVersion, String tenant, List<Map<String, Object>> supportedInterfaces) {
        if (agentCard == null) {
            return Map.of();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", agentCard.getName() != null ? agentCard.getName() : "");
        payload.put("description",
                buildDescription(agentCard.getDescription(), agentCard.getInputParams(), agentCard.getOutputParams()));
        payload.put("defaultInputModes", DEFAULT_INPUT_MODES);
        payload.put("defaultOutputModes", DEFAULT_OUTPUT_MODES);

        List<Map<String, Object>> interfaces =
            buildInterfaces(interfaceUrl, protocolBinding != null ? protocolBinding : "HTTP+JSON",
                    protocolVersion != null ? protocolVersion : "1.0", tenant, supportedInterfaces);
        if (!interfaces.isEmpty()) {
            payload.put("supportedInterfaces", interfaces);
        }
        return payload;
    }

    /**
     * toA2ACard.
     * 
     * @param agentCard agentCard
     * @return the result
     * @since 0.1.7
     */
    public static Map<String, Object> toA2ACard(AgentCard agentCard) {
        return toA2ACard(agentCard, null, "HTTP+JSON", "1.0", null, null);
    }

    /**
     * fromA2ACard.
     * 
     * @param a2aCard a2aCard
     * @return the result
     * @since 0.1.7
     */
    @SuppressWarnings("unchecked")
    public static AgentCard fromA2ACard(Map<String, Object> a2aCard) {
        if (a2aCard == null) {
            return AgentCard.builder().name("").description("").build();
        }
        String name = a2aCard.getOrDefault("name", "") instanceof String value ? value : "";
        String description = a2aCard.getOrDefault("description", "") instanceof String value ? value : "";
        return AgentCard.builder().name(name).description(description).build();
    }

    /**
     * buildDescription.
     * 
     * @param baseDescription baseDescription
     * @param inputParams inputParams
     * @param outputParams outputParams
     * @return the result
     * @since 0.1.7
     */
    private static String buildDescription(String baseDescription, Object inputParams, Object outputParams) {
        List<String> sections = new ArrayList<>();
        if (baseDescription != null && !baseDescription.isBlank()) {
            sections.add(baseDescription.trim());
        }
        String inputText = serializePayload(inputParams);
        String outputText = serializePayload(outputParams);
        if (!inputText.isBlank()) {
            sections.add("[input_params] " + inputText);
        }
        if (!outputText.isBlank()) {
            sections.add("[output_params] " + outputText);
        }
        return String.join("\n", sections).trim();
    }

    /**
     * serializePayload.
     * 
     * @param value value
     * @return the result
     * @since 0.1.7
     */
    private static String serializePayload(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Class<?> cls) {
            return JsonUtils.safeJsonDumps(Map.of("type", cls.getName()), "");
        }
        if (value instanceof Map<?, ?> map) {
            return JsonUtils.safeJsonDumps(map, "");
        }
        return JsonUtils.safeJsonDumps(Map.of("value", String.valueOf(value)), "");
    }

    /**
     * buildInterfaces.
     * 
     * @param interfaceUrl interfaceUrl
     * @param protocolBinding protocolBinding
     * @param protocolVersion protocolVersion
     * @param tenant tenant
     * @param supportedInterfaces supportedInterfaces
     * @return the result
     * @since 0.1.7
     */
    private static List<Map<String, Object>> buildInterfaces(String interfaceUrl, String protocolBinding,
            String protocolVersion, String tenant, List<Map<String, Object>> supportedInterfaces) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (supportedInterfaces != null) {
            for (Map<String, Object> item : supportedInterfaces) {
                if (item == null) {
                    continue;
                }
                Object url = item.get("url");
                Object binding = item.get("protocolBinding");
                if (binding == null) {
                    binding = item.get("protocol_binding");
                }
                Object version = item.get("protocolVersion");
                if (version == null) {
                    version = item.get("protocol_version");
                }
                if (url == null || binding == null || version == null) {
                    continue;
                }
                Map<String, Object> built = new LinkedHashMap<>();
                built.put("url", String.valueOf(url));
                built.put("protocolBinding", String.valueOf(binding));
                built.put("protocolVersion", String.valueOf(version));
                Object interfaceTenant = item.get("tenant");
                if (interfaceTenant != null) {
                    built.put("tenant", String.valueOf(interfaceTenant));
                }
                result.add(built);
            }
            if (!result.isEmpty()) {
                return result;
            }
        }

        if (interfaceUrl != null && !interfaceUrl.isBlank()) {
            Map<String, Object> built = new LinkedHashMap<>();
            built.put("url", interfaceUrl);
            built.put("protocolBinding", protocolBinding);
            built.put("protocolVersion", protocolVersion);
            if (tenant != null && !tenant.isBlank()) {
                built.put("tenant", tenant);
            }
            result.add(built);
        }
        return result;
    }
}
