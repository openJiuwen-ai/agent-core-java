/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.lang.reflect.Array;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * JSON serializer for distributed-runner messages.
 *
 * <p>Mirrors Python's module in
 * {@code openjiuwen/core/runner/drunner/dmessage_queue/message_serializer.py}.</p>
 */
public final class MessageSerializer {

    public static final int MAX_RECURSE_DEPTH = 10;

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, Function<Map<String, Object>, Object>> TYPE_REGISTRY =
            new ConcurrentHashMap<>();

    static {
        registerBuiltinTypes();
    }

    private MessageSerializer() {
    }

    public static byte[] serializeMessage(DmqMessage message) {
        Object data = serializePayload(message, 0);
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new IllegalArgumentException("Message is not JSON serializable", e);
        }
    }

    public static DmqMessage deserializeMessage(byte[] data) {
        try {
            Object raw = MAPPER.readValue(data, new TypeReference<Object>() {
            });
            Object message = deserializePayload(raw, 0);
            if (message instanceof DmqMessage dmqMessage) {
                return dmqMessage;
            }
            throw new IllegalArgumentException("JSON payload is not a distributed message");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to deserialize distributed message", e);
        }
    }

    public static void registerType(String className, Function<Map<String, Object>, Object> factory) {
        TYPE_REGISTRY.put(className, factory);
    }

    public static Map<String, Function<Map<String, Object>, Object>> getTypeRegistry() {
        return Map.copyOf(TYPE_REGISTRY);
    }

    static Object serializePayload(Object payload, int depth) {
        checkDepth(depth);
        if (payload == null) {
            return null;
        }
        if (payload instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (payload instanceof DmqRequestMessage request) {
            Map<String, Object> result = classPayload("DmqRequestMessage");
            putQueueFields(result, request, depth);
            result.put("type", serializePayload(request.getType(), depth + 1));
            result.put("reply_topic", serializePayload(request.getReplyTopic(), depth + 1));
            result.put("request_id", serializePayload(request.getRequestId(), depth + 1));
            result.put("sender_id", serializePayload(request.getSenderId(), depth + 1));
            result.put("receiver_id", serializePayload(request.getReceiverId(), depth + 1));
            result.put("enable_stream", serializePayload(request.isEnableStream(), depth + 1));
            result.put("expire_at", serializePayload(request.getExpireAt(), depth + 1));
            return result;
        }
        if (payload instanceof DmqResponseMessage response) {
            Map<String, Object> result = classPayload("DmqResponseMessage");
            putQueueFields(result, response, depth);
            result.put("type", serializePayload(response.getType(), depth + 1));
            result.put("result_type", serializePayload(response.getResultType(), depth + 1));
            result.put("request_id", serializePayload(response.getRequestId(), depth + 1));
            result.put("sender_id", serializePayload(response.getSenderId(), depth + 1));
            result.put("receiver_id", serializePayload(response.getReceiverId(), depth + 1));
            result.put("seq", serializePayload(response.getSeq(), depth + 1));
            result.put("last_chunk", serializePayload(response.isLastChunk(), depth + 1));
            result.put("expire_at", serializePayload(response.getExpireAt(), depth + 1));
            return result;
        }
        if (payload instanceof OutputSchema outputSchema) {
            Map<String, Object> result = classPayload("OutputSchema");
            result.put("type", serializePayload(outputSchema.getType(), depth + 1));
            result.put("index", serializePayload(outputSchema.getIndex(), depth + 1));
            result.put("payload", serializePayload(outputSchema.getPayload(), depth + 1));
            return result;
        }
        if (payload instanceof CustomSchema customSchema) {
            Map<String, Object> result = classPayload("CustomSchema");
            customSchema.getProperties().forEach((key, value) -> result.put(key, serializePayload(value, depth + 1)));
            return result;
        }
        if (payload instanceof TraceSchema traceSchema) {
            Map<String, Object> result = classPayload("TraceSchema");
            result.put("type", serializePayload(traceSchema.getType(), depth + 1));
            result.put("payload", serializePayload(traceSchema.getPayload(), depth + 1));
            return result;
        }
        if (payload instanceof WorkflowOutput workflowOutput) {
            Map<String, Object> result = classPayload("WorkflowOutput");
            result.put("result", serializePayload(workflowOutput.getResult(), depth + 1));
            result.put("state", serializePayload(workflowOutput.getState(), depth + 1));
            return result;
        }
        if (payload instanceof DynamicPayload dynamicPayload) {
            Map<String, Object> result = classPayload(dynamicPayload.className());
            dynamicPayload.fields().forEach((key, value) -> result.put(key, serializePayload(value, depth + 1)));
            return result;
        }
        if (payload instanceof OffsetDateTime dateTime) {
            return datetimePayload(dateTime.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }
        if (payload instanceof LocalDateTime dateTime) {
            return datetimePayload(dateTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        }
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), serializePayload(entry.getValue(), depth + 1));
            }
            return result;
        }
        if (payload instanceof Collection<?> collection) {
            List<Object> result = new ArrayList<>(collection.size());
            for (Object value : collection) {
                result.add(serializePayload(value, depth + 1));
            }
            return result;
        }
        if (payload.getClass().isArray()) {
            int length = Array.getLength(payload);
            List<Object> result = new ArrayList<>(length);
            for (int index = 0; index < length; index++) {
                result.add(serializePayload(Array.get(payload, index), depth + 1));
            }
            return result;
        }
        return payload;
    }

    @SuppressWarnings("unchecked")
    static Object deserializePayload(Object payload, int depth) {
        checkDepth(depth);
        if (payload == null) {
            return null;
        }
        if (payload instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object value : list) {
                result.add(deserializePayload(value, depth + 1));
            }
            return result;
        }
        if (payload instanceof Map<?, ?> map) {
            if ("datetime".equals(map.get("__type__"))) {
                return parseDatetime(String.valueOf(map.get("value")));
            }
            if (map.containsKey("__class__")) {
                String className = String.valueOf(map.get("__class__"));
                Function<Map<String, Object>, Object> factory = TYPE_REGISTRY.get(className);
                if (factory == null) {
                    throw new IllegalArgumentException("Unknown payload class: " + className);
                }
                Map<String, Object> fields = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String key = String.valueOf(entry.getKey());
                    if (!"__class__".equals(key)) {
                        fields.put(key, deserializePayload(entry.getValue(), depth + 1));
                    }
                }
                return factory.apply(fields);
            }
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), deserializePayload(entry.getValue(), depth + 1));
            }
            return result;
        }
        return payload;
    }

    private static void registerBuiltinTypes() {
        TYPE_REGISTRY.put("OutputSchema", MessageSerializer::outputSchemaFromFields);
        TYPE_REGISTRY.put("CustomSchema", CustomSchema::new);
        TYPE_REGISTRY.put("TraceSchema", fields -> new TraceSchema(asString(fields.get("type")), fields.get("payload")));
        TYPE_REGISTRY.put("InteractionOutput", fields -> new DynamicPayload("InteractionOutput", fields));
        TYPE_REGISTRY.put("WorkflowOutput", MessageSerializer::workflowOutputFromFields);
        TYPE_REGISTRY.put("DmqRequestMessage", MessageSerializer::requestFromFields);
        TYPE_REGISTRY.put("DmqResponseMessage", MessageSerializer::responseFromFields);
    }

    private static OutputSchema outputSchemaFromFields(Map<String, Object> fields) {
        return new OutputSchema(asString(fields.get("type")), intValue(fields.get("index"), 0), fields.get("payload"));
    }

    private static WorkflowOutput workflowOutputFromFields(Map<String, Object> fields) {
        return new WorkflowOutput(fields.get("result"), enumValue(
                WorkflowExecutionState.class, fields.get("state"), WorkflowExecutionState.COMPLETED));
    }

    private static DmqRequestMessage requestFromFields(Map<String, Object> fields) {
        DmqRequestMessage request = new DmqRequestMessage();
        populateQueueFields(request, fields);
        request.setType(enumValue(DMessageType.class, fields.get("type"), DMessageType.INPUT));
        request.setReplyTopic(asString(fields.get("reply_topic")));
        request.setRequestId(asString(fields.get("request_id")));
        request.setSenderId(asString(fields.get("sender_id")));
        request.setReceiverId(asString(fields.get("receiver_id")));
        request.setEnableStream(Boolean.TRUE.equals(fields.get("enable_stream")));
        request.setExpireAt(doubleValue(fields.get("expire_at")));
        return request;
    }

    private static DmqResponseMessage responseFromFields(Map<String, Object> fields) {
        DmqResponseMessage response = new DmqResponseMessage();
        populateQueueFields(response, fields);
        response.setType(enumValue(DMessageType.class, fields.get("type"), DMessageType.OUTPUT));
        response.setResultType(enumValue(ResultType.class, fields.get("result_type"), ResultType.MESSAGE));
        response.setRequestId(asString(fields.get("request_id")));
        response.setSenderId(asString(fields.get("sender_id")));
        response.setReceiverId(asString(fields.get("receiver_id")));
        response.setSeq(intValue(fields.get("seq"), 0));
        response.setLastChunk(Boolean.TRUE.equals(fields.get("last_chunk")));
        response.setExpireAt(doubleValue(fields.get("expire_at")));
        return response;
    }

    private static void putQueueFields(Map<String, Object> result, DmqMessage message, int depth) {
        result.put("message_id", serializePayload(message.getMessageId(), depth + 1));
        result.put("payload", serializePayload(message.getBody(), depth + 1));
        result.put("error_code", serializePayload(message.getErrorCode(), depth + 1));
        result.put("error_msg", serializePayload(message.getErrorMsg(), depth + 1));
    }

    private static void populateQueueFields(DmqMessage message, Map<String, Object> fields) {
        message.setMessageId(asString(fields.get("message_id")));
        message.setPayload(fields.get("payload"));
        message.setErrorCode(intValue(fields.get("error_code"), 0));
        message.setErrorMsg(asString(fields.get("error_msg")));
    }

    private static Map<String, Object> classPayload(String className) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("__class__", className);
        return result;
    }

    private static Map<String, Object> datetimePayload(String value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("__type__", "datetime");
        result.put("value", value);
        return result;
    }

    private static Object parseDatetime(String value) {
        try {
            return OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        } catch (Exception ignored) {
            return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    private static void checkDepth(int depth) {
        if (depth > MAX_RECURSE_DEPTH) {
            throw new StackOverflowError("Payload nested too deep (> " + MAX_RECURSE_DEPTH + ")");
        }
    }

    private static String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static int intValue(Object value, int defaultValue) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return defaultValue;
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private static Double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return null;
        }
        return Double.valueOf(String.valueOf(value));
    }

    private static <T extends Enum<T>> T enumValue(Class<T> enumType, Object value, T defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (enumType.isInstance(value)) {
            return enumType.cast(value);
        }
        return Enum.valueOf(enumType, String.valueOf(value));
    }

    /**
     * Dynamic fallback for Python registry types whose concrete Java type is not in this scope.
     */
    public record DynamicPayload(String className, Map<String, Object> fields) {
        public DynamicPayload {
            fields = fields == null ? new LinkedHashMap<>() : new LinkedHashMap<>(fields);
        }
    }
}
