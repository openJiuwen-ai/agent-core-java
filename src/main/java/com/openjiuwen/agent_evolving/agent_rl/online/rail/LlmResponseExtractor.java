// coding: utf-8
// Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.

package com.openjiuwen.agent_evolving.agent_rl.online.rail;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

/**
 * Extract token-level fields from LLM responses for online Rail (rail-v1).
 * <p>
 * Mirrors Python's {@code llm_response.py} from
 * {@code openjiuwen.agent_evolving.agent_rl.online.rail.llm_response}.
 * <p>
 * Single source of truth for:
 * <ul>
 *   <li>RLOnlineRail (step hook: fill TrajectoryStep / LLMCallDetail.meta)</li>
 *   <li>OnlineTrajectoryConverter (fallback when step fields are empty)</li>
 * </ul>
 */
public final class LlmResponseExtractor {
    
    private static final Logger log = Logger.getLogger(LlmResponseExtractor.class.getName());
    
    private LlmResponseExtractor() {
        // Utility class - prevent instantiation
    }
    
    /**
     * Extract provider response JSON from response object.
     * Mirrors Python's _provider_response_json function.
     * 
     * @param response Response object (can be Map or object with metadata)
     * @return Response JSON as Map, or null if not available
     */
    private static Map<String, Object> providerResponseJson(Object response) {
        if (response instanceof Map) {
            return (Map<String, Object>) response;
        }
        
        // Try to get metadata attribute
        Object metadata = getAttribute(response, "metadata");
        if (metadata instanceof Map) {
            return (Map<String, Object>) metadata;
        }
        
        return null;
    }
    
    /**
     * Get first choice from response JSON.
     * Mirrors Python's _first_choice function.
     * 
     * @param responseJson Response JSON map
     * @return First choice map, or null if not available
     */
    private static Map<String, Object> firstChoice(Map<String, Object> responseJson) {
        if (responseJson == null) {
            return null;
        }
        
        Object choices = responseJson.get("choices");
        if (!(choices instanceof List) || ((List<?>) choices).isEmpty()) {
            return null;
        }
        
        Object choice = ((List<?>) choices).get(0);
        return choice instanceof Map ? (Map<String, Object>) choice : null;
    }
    
    /**
     * Extract integer list from response using multiple field names.
     * Mirrors Python's _extract_int_list function.
     * 
     * @param response Response object
     * @param fieldNames Field names to check in choice and response
     * @param runtimeField Optional runtime-specific field name
     * @return List of integers, or null if not found
     */
    private static List<Integer> extractIntList(Object response, String[] fieldNames, String runtimeField) {
        Map<String, Object> responseJson = providerResponseJson(response);
        List<Object> candidates = new ArrayList<>();
        
        // Check runtime field first
        if (runtimeField != null && responseJson != null) {
            candidates.add(responseJson.get(runtimeField));
        }
        
        // Check fields in choice
        Map<String, Object> choice = firstChoice(responseJson);
        if (choice != null) {
            for (String name : fieldNames) {
                candidates.add(choice.get(name));
            }
        }
        
        // Check fields in response JSON
        if (responseJson != null) {
            for (String name : fieldNames) {
                candidates.add(responseJson.get(name));
            }
        }
        
        // Try to extract integers from candidates
        for (Object candidate : candidates) {
            if (candidate instanceof List) {
                List<Integer> out = new ArrayList<>();
                for (Object item : (List<?>) candidate) {
                    try {
                        out.add(toInt(item));
                    } catch (NumberFormatException e) {
                        // Skip invalid items
                    }
                }
                if (!out.isEmpty()) {
                    return out;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Best-effort logprob list from an OpenAI-style or dict response.
     * Mirrors Python's extract_logprobs function.
     * 
     * @param response Response object (can be Map or object with logprobs)
     * @return List of logprobs as doubles, or null if not found
     */
    public static List<Double> extractLogprobs(Object response) {
        if (response == null) {
            return null;
        }
        
        Map<String, Object> responseJson = providerResponseJson(response);
        Map<String, Object> choice = firstChoice(responseJson);
        
        // Try to get direct logprobs
        Object direct = null;
        if (choice != null) {
            direct = choice.get("logprobs");
        }
        if (direct == null && responseJson != null) {
            direct = responseJson.get("logprobs");
        }
        
        if (direct == null) {
            return null;
        }
        
        // Handle list of logprobs
        if (direct instanceof List) {
            List<Double> out = new ArrayList<>();
            for (Object item : (List<?>) direct) {
                try {
                    out.add(toDouble(item));
                } catch (NumberFormatException e) {
                    // Skip invalid items
                }
            }
            return out.isEmpty() ? null : out;
        }
        
        // Handle content array with logprob field
        Object content = getAttribute(direct, "content");
        if (content == null && direct instanceof Map) {
            content = ((Map<String, Object>) direct).get("content");
        }
        
        if (content instanceof List) {
            List<Double> out = new ArrayList<>();
            for (Object item : (List<?>) content) {
                try {
                    Object logprob = item instanceof Map ? 
                        ((Map<String, Object>) item).get("logprob") : 
                        getAttribute(item, "logprob");
                    if (logprob != null) {
                        out.add(toDouble(logprob));
                    }
                } catch (NumberFormatException | NullPointerException e) {
                    // Skip invalid items
                }
            }
            return out.isEmpty() ? null : out;
        }
        
        return null;
    }
    
    /**
     * Best-effort response token id list (vLLM return_token_ids or similar).
     * Mirrors Python's extract_token_ids function.
     * 
     * @param response Response object
     * @return List of token IDs, or null if not found
     */
    public static List<Integer> extractTokenIds(Object response) {
        if (response == null) {
            return null;
        }
        return extractIntList(response, 
            new String[]{"completion_token_ids", "token_ids", "response_tokens"}, 
            null);
    }
    
    /**
     * Best-effort prompt token id list from vLLM return_token_ids payloads.
     * Mirrors Python's extract_prompt_ids function.
     * 
     * @param response Response object
     * @return List of prompt token IDs, or null if not found
     */
    public static List<Integer> extractPromptIds(Object response) {
        if (response == null) {
            return null;
        }
        return extractIntList(response, 
            new String[]{"prompt_token_ids", "prompt_ids"}, 
            null);
    }
    
    // ===== Helper methods for reflection-based attribute access =====
    
    /**
     * Get attribute value from object using reflection.
     * 
     * @param obj Object to get attribute from
     * @param attrName Attribute name
     * @return Attribute value, or null if not available
     */
    private static Object getAttribute(Object obj, String attrName) {
        if (obj == null) {
            return null;
        }
        
        try {
            // Try getter method first
            String getterName = "get" + capitalize(attrName);
            Method getter = obj.getClass().getMethod(getterName);
            return getter.invoke(obj);
        } catch (NoSuchMethodException e) {
            // Try direct field access via reflection
            try {
                java.lang.reflect.Field field = obj.getClass().getDeclaredField(attrName);
                field.setAccessible(true);
                return field.get(obj);
            } catch (Exception e2) {
                return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Capitalize first letter of string.
     */
    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    /**
     * Convert object to integer.
     */
    private static int toInt(Object obj) throws NumberFormatException {
        if (obj == null) {
            throw new NumberFormatException("null");
        }
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        }
        return Integer.parseInt(obj.toString());
    }
    
    /**
     * Convert object to double.
     */
    private static double toDouble(Object obj) throws NumberFormatException {
        if (obj == null) {
            throw new NumberFormatException("null");
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        return Double.parseDouble(obj.toString());
    }
}