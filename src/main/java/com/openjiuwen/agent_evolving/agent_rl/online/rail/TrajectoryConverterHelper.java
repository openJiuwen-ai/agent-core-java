// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Convert Rail-collected trajectories into online RL rail-v1 batches.
 * <p>
 * Mirrors Python's {@code converter.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.online.rail.converter}.
 */
public final class TrajectoryConverterHelper {
    
    private static final ObjectMapper JSON = new ObjectMapper();
    
    private TrajectoryConverterHelper() {
        // Utility class
    }
    
    /**
     * Try to get model_dump from Pydantic-like object.
     */
    private static Map<String, Object> modelDump(Object value) {
        // In Java, we don't have Pydantic model_dump
        // This is a placeholder for Jackson-based serialization
        if (value == null) {
            return null;
        }
        try {
            String json = JSON.writeValueAsString(value);
            return JSON.readValue(json, Map.class);
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Convert any value to JSON-safe value.
     */
    public static Object jsonValue(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String || value instanceof Integer || 
            value instanceof Double || value instanceof Boolean) {
            return value;
        }
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) {
                result.add(jsonValue(item));
            }
            return result;
        }
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                result.put(String.valueOf(entry.getKey()), jsonValue(entry.getValue()));
            }
            return result;
        }
        Map<String, Object> dumped = modelDump(value);
        if (dumped != null) {
            return jsonValue(dumped);
        }
        return String.valueOf(value);
    }
    
    /**
     * Convert message to dict.
     */
    public static Map<String, Object> messageToDict(Object message) {
        if (message instanceof Map) {
            return (Map<String, Object>) jsonValue(message);
        }
        Map<String, Object> dumped = modelDump(message);
        if (dumped != null) {
            return (Map<String, Object>) jsonValue(dumped);
        }
        
        // Try to extract fields via reflection
        Map<String, Object> out = new LinkedHashMap<>();
        Object role = getAttribute(message, "role");
        if (role != null) {
            out.put("role", String.valueOf(role));
            out.put("content", jsonValue(getAttribute(message, "content")));
            
            Object name = getAttribute(message, "name");
            if (name != null) {
                out.put("name", String.valueOf(name));
            }
            Object metadata = getAttribute(message, "metadata");
            if (metadata != null) {
                out.put("metadata", jsonValue(metadata));
            }
            Object toolCalls = getAttribute(message, "tool_calls");
            if (toolCalls != null) {
                out.put("tool_calls", jsonValue(toolCalls));
            }
            return out;
        }
        
        return Map.of("role", "unknown", "content", String.valueOf(message));
    }
    
    /**
     * Convert response to dict.
     */
    public static Map<String, Object> responseToDict(Object response) {
        if (response == null) {
            return new LinkedHashMap<>();
        }
        if (response instanceof Map) {
            return (Map<String, Object>) jsonValue(response);
        }
        Map<String, Object> dumped = modelDump(response);
        if (dumped != null) {
            return (Map<String, Object>) jsonValue(dumped);
        }
        
        Map<String, Object> out = new LinkedHashMap<>();
        Object role = getAttribute(response, "role");
        out.put("role", role != null ? String.valueOf(role) : "assistant");
        out.put("content", jsonValue(getAttribute(response, "content")));
        
        Object toolCalls = getAttribute(response, "tool_calls");
        if (toolCalls != null) {
            out.put("tool_calls", jsonValue(toolCalls));
        }
        
        Object usage = getAttribute(response, "usage_metadata");
        if (usage == null) {
            usage = getAttribute(response, "usage");
        }
        if (usage != null) {
            out.put("usage", jsonValue(usage));
        }
        
        Object finishReason = getAttribute(response, "finish_reason");
        if (finishReason != null) {
            out.put("finish_reason", finishReason);
        }
        
        Object reasoningContent = getAttribute(response, "reasoning_content");
        if (reasoningContent != null) {
            out.put("reasoning_content", reasoningContent);
        }
        
        return out;
    }
    
    /**
     * Extract text from content.
     */
    public static String extractText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof List) {
            List<String> parts = new ArrayList<>();
            for (Object item : (List<?>) value) {
                if (item instanceof String && !((String) item).isEmpty()) {
                    parts.add((String) item);
                } else if (item instanceof Map) {
                    Object text = ((Map<?, ?>) item).get("text");
                    if (text == null) {
                        text = ((Map<?, ?>) item).get("content");
                    }
                    if (text instanceof String && !((String) text).isEmpty()) {
                        parts.add((String) text);
                    }
                }
            }
            return String.join("\n", parts);
        }
        return String.valueOf(value);
    }
    
    /**
     * Coerce logprobs to list of floats.
     */
    public static List<Double> coerceLogprobs(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof List) {
            List<Double> out = new ArrayList<>();
            for (Object item : (List<?>) value) {
                try {
                    out.add(toDouble(item));
                } catch (NumberFormatException e) {
                    // Skip invalid items
                }
            }
            return out.isEmpty() ? null : out;
        }
        
        Object content = null;
        if (value instanceof Map) {
            content = ((Map<?, ?>) value).get("content");
        } else {
            content = getAttribute(value, "content");
        }
        
        if (content instanceof List) {
            List<Double> out = new ArrayList<>();
            for (Object item : (List<?>) content) {
                try {
                    Object logprob = item instanceof Map ? 
                        ((Map<?, ?>) item).get("logprob") : 
                        getAttribute(item, "logprob");
                    if (logprob != null) {
                        out.add(toDouble(logprob));
                    }
                } catch (Exception e) {
                    // Skip invalid items
                }
            }
            return out.isEmpty() ? null : out;
        }
        return null;
    }
    
    /**
     * Create fingerprint for payload.
     */
    public static Map<String, Object> fingerprintPayload(List<Map<String, Object>> messages, Object tools) {
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("messages", messages);
            payload.put("tools", jsonValue(tools));
            
            String raw = JSON.writeValueAsString(payload);
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            
            return Map.of("type", "rail-local-sha256", "sha256", hexString.toString());
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
    }
    
    // ===== Helper methods =====
    
    private static Object getAttribute(Object obj, String attrName) {
        if (obj == null) return null;
        try {
            String getterName = "get" + capitalize(attrName);
            java.lang.reflect.Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (Exception e) {
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(attrName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception e2) {
                return null;
            }
        }
    }
    
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    private static double toDouble(Object obj) throws NumberFormatException {
        if (obj == null) throw new NumberFormatException("null");
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        return Double.parseDouble(obj.toString());
    }
}