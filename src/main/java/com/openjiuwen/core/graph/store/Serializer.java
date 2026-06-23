/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.graph.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.SystemMessage;
import com.openjiuwen.core.foundation.llm.schema.ToolCall;
import com.openjiuwen.core.foundation.llm.schema.ToolMessage;
import com.openjiuwen.core.foundation.llm.schema.UsageMetadata;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.graph.pregel.Message;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors Python's serializer module in
 * {@code openjiuwen/core/graph/store/serde.py}.
 */
public abstract class Serializer {

    public record TypedBytes(String type, byte[] data) {
    }

    public abstract TypedBytes dumpsTyped(Object obj);

    public abstract Object loadsTyped(TypedBytes data);

    public static Serializer create(String typeName) {
        return createSerializer(typeName);
    }

    public static Serializer createSerializer(String typeName) {
        if ("json".equals(typeName)) {
            return new JsonSerializer();
        }
        if ("pickle".equals(typeName)) {
            return new PickleSerializer();
        }
        throw new IllegalArgumentException("Unknown serializer type: " + typeName);
    }

    /**
     * Mirrors Python's {@code JsonSerializer} in
     * {@code openjiuwen/core/graph/store/serde.py}.
     */
    public static final class JsonSerializer extends Serializer {

        private static final String TYPE_FIELD = "__jiuwenType";
        private static final String TYPE_MESSAGE_USER = "message.user";
        private static final String TYPE_MESSAGE_ASSISTANT = "message.assistant";
        private static final String TYPE_MESSAGE_SYSTEM = "message.system";
        private static final String TYPE_MESSAGE_TOOL = "message.tool";
        private static final String TYPE_GRAPH_STORE_STATE = "graph.storeState";
        private static final String TYPE_GRAPH_MESSAGE = "graph.message";
        private static final String TYPE_GRAPH_PENDING_NODE = "graph.pendingNode";
        private static final ObjectMapper MAPPER = new ObjectMapper();

        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try {
                byte[] bytes = MAPPER.writeValueAsBytes(toProtocolValue(obj));
                return new TypedBytes("json", bytes);
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to serialize JSON", e);
            }
        }

        @Override
        public Object loadsTyped(TypedBytes data) {
            if (data == null || !"json".equals(data.type())) {
                return null;
            }
            try {
                Object value = MAPPER.readValue(data.data(), Object.class);
                return fromProtocolValue(value);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to deserialize JSON", e);
            }
        }

        private static Object toProtocolValue(Object value) {
            if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
                return value;
            }
            if (value instanceof UserMessage message) {
                return userMessageToMap(message);
            }
            if (value instanceof AssistantMessage message) {
                return assistantMessageToMap(message);
            }
            if (value instanceof SystemMessage message) {
                return systemMessageToMap(message);
            }
            if (value instanceof ToolMessage message) {
                return toolMessageToMap(message);
            }
            if (value instanceof GraphStoreState state) {
                return graphStoreStateToMap(state);
            }
            if (value instanceof Message message) {
                return graphMessageToMap(message);
            }
            if (value instanceof PendingNode pendingNode) {
                return pendingNodeToMap(pendingNode);
            }
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>(list.size());
                for (Object item : list) {
                    result.add(toProtocolValue(item));
                }
                return result;
            }
            if (value instanceof Map<?, ?> map) {
                return mapToProtocolValue(map);
            }
            throw new IllegalArgumentException("Unsupported JSON value type: " + value.getClass().getName());
        }

        private static Map<String, Object> mapToProtocolValue(Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON Map key must be String: " + entry.getKey());
                }
                if (TYPE_FIELD.equals(key)) {
                    throw new IllegalArgumentException("JSON reserves field " + TYPE_FIELD);
                }
                result.put(key, toProtocolValue(entry.getValue()));
            }
            return result;
        }

        private static Map<String, Object> userMessageToMap(UserMessage message) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(TYPE_FIELD, TYPE_MESSAGE_USER);
            result.put("role", message.getRole());
            result.put("content", toProtocolValue(message.getContent()));
            result.put("name", message.getName());
            return result;
        }

        private static Map<String, Object> assistantMessageToMap(AssistantMessage message) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(TYPE_FIELD, TYPE_MESSAGE_ASSISTANT);
            result.put("role", message.getRole());
            result.put("content", toProtocolValue(message.getContent()));
            result.put("name", message.getName());
            result.put("toolCalls", assistantFieldToProtocolValue(message.getToolCalls()));
            result.put("usageMetadata", assistantFieldToProtocolValue(message.getUsageMetadata()));
            result.put("finishReason", message.getFinishReason());
            result.put("parserContent", toProtocolValue(message.getParserContent()));
            result.put("reasoningContent", message.getReasoningContent());
            return result;
        }

        private static Map<String, Object> systemMessageToMap(SystemMessage message) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(TYPE_FIELD, TYPE_MESSAGE_SYSTEM);
            result.put("role", message.getRole());
            result.put("content", toProtocolValue(message.getContent()));
            result.put("name", message.getName());
            return result;
        }

        private static Map<String, Object> toolMessageToMap(ToolMessage message) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(TYPE_FIELD, TYPE_MESSAGE_TOOL);
            result.put("role", message.getRole());
            result.put("content", toProtocolValue(message.getContent()));
            result.put("name", message.getName());
            result.put("toolCallId", message.getToolCallId());
            return result;
        }

        private static Map<String, Object> graphStoreStateToMap(GraphStoreState state) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(TYPE_FIELD, TYPE_GRAPH_STORE_STATE);
            result.put("ns", state.getNs());
            result.put("step", state.getStep());
            result.put("channelValues", toProtocolValue(state.getChannelValues()));
            result.put("pendingBuffer", toProtocolValue(state.getPendingBuffer()));
            result.put("pendingNode", toProtocolValue(state.getPendingNode()));
            result.put("nodeVersion", toProtocolValue(state.getNodeVersion()));
            return result;
        }

        private static Map<String, Object> graphMessageToMap(Message message) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(TYPE_FIELD, TYPE_GRAPH_MESSAGE);
            result.put("sender", message.getSender());
            result.put("target", message.getTarget());
            result.put("payload", toProtocolValue(message.getPayload()));
            return result;
        }

        private static Map<String, Object> pendingNodeToMap(PendingNode pendingNode) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put(TYPE_FIELD, TYPE_GRAPH_PENDING_NODE);
            result.put("nodeName", pendingNode.getNodeName());
            result.put("status", pendingNode.getStatus());
            result.put("exceptions", exceptionsToProtocolValue(pendingNode.getExceptions()));
            return result;
        }

        private static List<Map<String, Object>> exceptionsToProtocolValue(List<Exception> exceptions) {
            if (exceptions == null) {
                return null;
            }
            List<Map<String, Object>> result = new ArrayList<>(exceptions.size());
            for (Exception exception : exceptions) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("className", exception.getClass().getName());
                item.put("message", exception.getMessage());
                result.add(item);
            }
            return result;
        }

        private static Object assistantFieldToProtocolValue(Object value) {
            if (value instanceof ToolCall || value instanceof UsageMetadata) {
                return mapToProtocolValue(MAPPER.convertValue(value, new TypeReference<Map<String, Object>>() {
                }));
            }
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>(list.size());
                for (Object item : list) {
                    result.add(assistantFieldToProtocolValue(item));
                }
                return result;
            }
            return toProtocolValue(value);
        }

        private static Object fromProtocolValue(Object value) {
            if (value == null || value instanceof String || value instanceof Number || value instanceof Boolean) {
                return value;
            }
            if (value instanceof List<?> list) {
                List<Object> result = new ArrayList<>(list.size());
                for (Object item : list) {
                    result.add(fromProtocolValue(item));
                }
                return result;
            }
            if (value instanceof Map<?, ?> map) {
                Map<String, Object> stringMap = requireStringMap(map);
                if (!stringMap.containsKey(TYPE_FIELD)) {
                    return restorePlainMap(stringMap);
                }
                Object type = stringMap.get(TYPE_FIELD);
                if (!(type instanceof String typeName)) {
                    throw new IllegalArgumentException("JSON field must be String: " + TYPE_FIELD);
                }
                return switch (typeName) {
                    case TYPE_MESSAGE_USER -> restoreUserMessage(stringMap);
                    case TYPE_MESSAGE_ASSISTANT -> restoreAssistantMessage(stringMap);
                    case TYPE_MESSAGE_SYSTEM -> restoreSystemMessage(stringMap);
                    case TYPE_MESSAGE_TOOL -> restoreToolMessage(stringMap);
                    case TYPE_GRAPH_STORE_STATE -> restoreGraphStoreState(stringMap);
                    case TYPE_GRAPH_MESSAGE -> restoreGraphMessage(stringMap);
                    case TYPE_GRAPH_PENDING_NODE -> restorePendingNode(stringMap);
                    default -> throw new IllegalArgumentException("Unknown JSON type: " + typeName);
                };
            }
            throw new IllegalArgumentException("Unsupported JSON value: " + value);
        }

        private static Map<String, Object> requireStringMap(Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON Map key must be String: " + entry.getKey());
                }
                result.put(key, entry.getValue());
            }
            return result;
        }

        private static Map<String, Object> restorePlainMap(Map<String, Object> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                result.put(entry.getKey(), fromProtocolValue(entry.getValue()));
            }
            return result;
        }

        private static UserMessage restoreUserMessage(Map<String, Object> map) {
            requireAllowedFields(TYPE_MESSAGE_USER, map, Set.of(TYPE_FIELD, "role", "content", "name"));
            requireRole(map, "user");
            UserMessage message = new UserMessage();
            message.setRole("user");
            message.setContent(fromProtocolValue(requireField(map, "content")));
            message.setName(optionalStringField(map, "name"));
            return message;
        }

        private static AssistantMessage restoreAssistantMessage(Map<String, Object> map) {
            requireAllowedFields(TYPE_MESSAGE_ASSISTANT, map, Set.of(
                    TYPE_FIELD, "role", "content", "name", "toolCalls", "usageMetadata",
                    "finishReason", "parserContent", "reasoningContent"));
            requireRole(map, "assistant");
            AssistantMessage message = new AssistantMessage();
            message.setRole("assistant");
            message.setContent(fromProtocolValue(requireField(map, "content")));
            message.setName(optionalStringField(map, "name"));
            if (map.get("toolCalls") != null) {
                message.setToolCalls(MAPPER.convertValue(map.get("toolCalls"), new TypeReference<List<ToolCall>>() {
                }));
            }
            if (map.get("usageMetadata") != null) {
                message.setUsageMetadata(MAPPER.convertValue(map.get("usageMetadata"), UsageMetadata.class));
            }
            if (map.get("finishReason") != null) {
                message.setFinishReason(String.valueOf(map.get("finishReason")));
            }
            if (map.get("parserContent") != null) {
                message.setParserContent(fromProtocolValue(map.get("parserContent")));
            }
            if (map.get("reasoningContent") != null) {
                message.setReasoningContent(String.valueOf(map.get("reasoningContent")));
            }
            return message;
        }

        private static SystemMessage restoreSystemMessage(Map<String, Object> map) {
            requireAllowedFields(TYPE_MESSAGE_SYSTEM, map, Set.of(TYPE_FIELD, "role", "content", "name"));
            requireRole(map, "system");
            SystemMessage message = new SystemMessage();
            message.setRole("system");
            message.setContent(fromProtocolValue(requireField(map, "content")));
            message.setName(optionalStringField(map, "name"));
            return message;
        }

        private static ToolMessage restoreToolMessage(Map<String, Object> map) {
            requireAllowedFields(TYPE_MESSAGE_TOOL, map, Set.of(TYPE_FIELD, "role", "content", "name", "toolCallId"));
            requireRole(map, "tool");
            ToolMessage message = new ToolMessage();
            message.setRole("tool");
            message.setContent(fromProtocolValue(requireField(map, "content")));
            message.setName(optionalStringField(map, "name"));
            message.setToolCallId(requireStringField(map, "toolCallId"));
            return message;
        }

        private static GraphStoreState restoreGraphStoreState(Map<String, Object> map) {
            requireAllowedFields(TYPE_GRAPH_STORE_STATE, map, Set.of(
                    TYPE_FIELD, "ns", "step", "channelValues", "pendingBuffer", "pendingNode", "nodeVersion"));
            return GraphStoreState.create(
                    requireStringField(map, "ns"),
                    requireIntField(map, "step"),
                    restoreObjectMap(requireNonNullField(map, "channelValues")),
                    restoreGraphMessages(requireNonNullField(map, "pendingBuffer")),
                    restorePendingNodeMap(requireNonNullField(map, "pendingNode")),
                    restoreIntegerMap(requireNonNullField(map, "nodeVersion"))
            );
        }

        private static Message restoreGraphMessage(Map<String, Object> map) {
            requireAllowedFields(TYPE_GRAPH_MESSAGE, map, Set.of(TYPE_FIELD, "sender", "target", "payload"));
            return new Message(
                    requireStringField(map, "sender"),
                    requireStringField(map, "target"),
                    fromProtocolValue(requireField(map, "payload"))
            );
        }

        private static PendingNode restorePendingNode(Map<String, Object> map) {
            requireAllowedFields(TYPE_GRAPH_PENDING_NODE, map, Set.of(TYPE_FIELD, "nodeName", "status", "exceptions"));
            return new PendingNode(
                    requireStringField(map, "nodeName"),
                    requireStringField(map, "status"),
                    restoreExceptions(requireField(map, "exceptions"))
            );
        }

        private static Map<String, Object> restoreObjectMap(Object value) {
            Object restored = fromProtocolValue(value);
            if (restored == null) {
                return new LinkedHashMap<>();
            }
            if (!(restored instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("JSON value must be Map");
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON Map key must be String: " + entry.getKey());
                }
                result.put(key, entry.getValue());
            }
            return result;
        }

        private static List<Message> restoreGraphMessages(Object value) {
            Object restored = fromProtocolValue(value);
            if (restored == null) {
                return List.of();
            }
            if (!(restored instanceof List<?> list)) {
                throw new IllegalArgumentException("JSON pendingBuffer must be List");
            }
            List<Message> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Message message)) {
                    throw new IllegalArgumentException("JSON pendingBuffer item must be graph message");
                }
                result.add(message);
            }
            return result;
        }

        private static Map<String, PendingNode> restorePendingNodeMap(Object value) {
            Object restored = fromProtocolValue(value);
            if (restored == null) {
                return new LinkedHashMap<>();
            }
            if (!(restored instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("JSON pendingNode must be Map");
            }
            Map<String, PendingNode> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON Map key must be String: " + entry.getKey());
                }
                if (!(entry.getValue() instanceof PendingNode pendingNode)) {
                    throw new IllegalArgumentException("JSON pendingNode value must be PendingNode");
                }
                result.put(key, pendingNode);
            }
            return result;
        }

        private static Map<String, Integer> restoreIntegerMap(Object value) {
            Object restored = fromProtocolValue(value);
            if (restored == null) {
                return new LinkedHashMap<>();
            }
            if (!(restored instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("JSON nodeVersion must be Map");
            }
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)) {
                    throw new IllegalArgumentException("JSON Map key must be String: " + entry.getKey());
                }
                result.put(key, strictIntValue(entry.getValue(), "nodeVersion." + key));
            }
            return result;
        }

        private static List<Exception> restoreExceptions(Object value) {
            Object restored = fromProtocolValue(value);
            if (restored == null) {
                return null;
            }
            if (!(restored instanceof List<?> list)) {
                throw new IllegalArgumentException("JSON pendingNode exceptions must be List");
            }
            List<Exception> result = new ArrayList<>(list.size());
            for (Object item : list) {
                if (!(item instanceof Map<?, ?> exceptionMap)) {
                    throw new IllegalArgumentException("JSON exception item must be Map");
                }
                Map<String, Object> stringMap = requireStringMap(exceptionMap);
                requireAllowedFields("exception", stringMap, Set.of("className", "message"));
                String className = requireStringField(stringMap, "className");
                Object message = requireField(stringMap, "message");
                result.add(new RuntimeException(className + ": " + stringValue(message)));
            }
            return result;
        }

        private static void requireAllowedFields(String typeName, Map<String, Object> map, Set<String> allowedFields) {
            for (String field : map.keySet()) {
                if (!allowedFields.contains(field)) {
                    throw new IllegalArgumentException("Unexpected JSON field for " + typeName + ": " + field);
                }
            }
        }

        private static Object requireField(Map<String, Object> map, String fieldName) {
            if (!map.containsKey(fieldName)) {
                throw new IllegalArgumentException("Missing JSON field: " + fieldName);
            }
            return map.get(fieldName);
        }

        private static Object requireNonNullField(Map<String, Object> map, String fieldName) {
            Object value = requireField(map, fieldName);
            if (value == null) {
                throw new IllegalArgumentException("JSON field must not be null: " + fieldName);
            }
            return value;
        }

        private static String requireStringField(Map<String, Object> map, String fieldName) {
            Object value = requireField(map, fieldName);
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("JSON field must be String: " + fieldName);
            }
            return text;
        }

        private static String optionalStringField(Map<String, Object> map, String fieldName) {
            Object value = map.get(fieldName);
            if (value == null) {
                return null;
            }
            if (!(value instanceof String text)) {
                throw new IllegalArgumentException("JSON field must be String: " + fieldName);
            }
            return text;
        }

        private static void requireRole(Map<String, Object> map, String expectedRole) {
            String role = requireStringField(map, "role");
            if (!expectedRole.equals(role)) {
                throw new IllegalArgumentException("JSON role must be " + expectedRole + ": " + role);
            }
        }

        private static int requireIntField(Map<String, Object> map, String fieldName) {
            return strictIntValue(requireField(map, fieldName), fieldName);
        }

        private static String stringValue(Object value) {
            return value != null ? String.valueOf(value) : null;
        }

        private static int strictIntValue(Object value, String fieldName) {
            if (!(value instanceof Number number)) {
                throw new IllegalArgumentException("JSON integer field must be Number: " + fieldName);
            }
            BigDecimal decimal = numberToBigDecimal(number, fieldName).stripTrailingZeros();
            if (decimal.scale() > 0) {
                throw new IllegalArgumentException("JSON integer field must not be fractional: " + fieldName);
            }
            if (decimal.compareTo(BigDecimal.valueOf(Integer.MIN_VALUE)) < 0
                    || decimal.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE)) > 0) {
                throw new IllegalArgumentException("JSON integer field out of int range: " + fieldName);
            }
            return decimal.intValueExact();
        }

        private static BigDecimal numberToBigDecimal(Number number, String fieldName) {
            if (number instanceof BigDecimal decimal) {
                return decimal;
            }
            if (number instanceof BigInteger integer) {
                return new BigDecimal(integer);
            }
            if (number instanceof Byte || number instanceof Short || number instanceof Integer
                    || number instanceof Long) {
                return BigDecimal.valueOf(number.longValue());
            }
            if (number instanceof Float || number instanceof Double) {
                double value = number.doubleValue();
                if (!Double.isFinite(value)) {
                    throw new IllegalArgumentException("JSON integer field must be finite: " + fieldName);
                }
                return BigDecimal.valueOf(value);
            }
            return new BigDecimal(number.toString());
        }
    }

    /**
     * Mirrors Python's {@code PickleSerializer} in
     * {@code openjiuwen/core/graph/store/serde.py}.
     */
    public static final class PickleSerializer extends Serializer {

        @Override
        public TypedBytes dumpsTyped(Object obj) {
            try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
                 ObjectOutputStream oos = new ObjectOutputStream(bos)) {
                oos.writeObject(obj);
                oos.flush();
                return new TypedBytes("pickle", bos.toByteArray());
            } catch (IOException e) {
                throw new IllegalStateException("Failed to serialize pickle payload", e);
            }
        }

        @Override
        public Object loadsTyped(TypedBytes data) {
            if (data == null || !"pickle".equals(data.type())) {
                return null;
            }
            try (ByteArrayInputStream bis = new ByteArrayInputStream(data.data());
                 ObjectInputStream ois = new ObjectInputStream(bis)) {
                return ois.readObject();
            } catch (IOException | ClassNotFoundException e) {
                throw new IllegalStateException("Failed to deserialize pickle payload", e);
            }
        }
    }
}
