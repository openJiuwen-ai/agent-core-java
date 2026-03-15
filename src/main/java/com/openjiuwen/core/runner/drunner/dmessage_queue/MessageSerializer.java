/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
 */
package com.openjiuwen.core.runner.drunner.dmessage_queue;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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
 * <p>
 * Mirrors Python's {@code message_serializer.py} with:
 * <ul>
 *   <li>{@code MAX_RECURSE_DEPTH} – prevents runaway recursion on nested payloads</li>
 *   <li>{@code TYPE_REGISTRY} – allows registering custom classes for deserialization via {@code __class__} marker</li>
 *   <li>datetime serialization – encodes {@link OffsetDateTime}/{@link LocalDateTime} as {@code {"__type__":"datetime","value":"..."}}</li>
 * </ul>
 */
public final class MessageSerializer {

    /** Maximum recursion depth for payload serialization / deserialization. */
    public static final int MAX_RECURSE_DEPTH = 10;

    private static final ObjectMapper MAPPER;

    /**
     * Type registry: className -> deserializer function.
     * <p>
     * When a JSON object contains {@code "__class__": "SomeName"}, the registry is
     * consulted to find a function that can reconstruct the object from a Map.
     */
    private static final Map<String, Function<Map<String, Object>, Object>> TYPE_REGISTRY = new ConcurrentHashMap<>();

    static {
        ObjectMapper mapper = new ObjectMapper();
        SimpleModule module = new SimpleModule();
        // datetime serializers
        module.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeStartObject();
                gen.writeStringField("__type__", "datetime");
                gen.writeStringField("value", value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
                gen.writeEndObject();
            }
        });
        module.addSerializer(LocalDateTime.class, new JsonSerializer<>() {
            @Override
            public void serialize(LocalDateTime value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeStartObject();
                gen.writeStringField("__type__", "datetime");
                gen.writeStringField("value", value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                gen.writeEndObject();
            }
        });
        mapper.registerModule(module);
        MAPPER = mapper;
    }

    private MessageSerializer() {
    }

    // ========== Type Registry ==========

    /**
     * Register a type for deserialization. When a dict with {@code "__class__": name} is
     * encountered during deserialization, the supplied function is called with the remaining
     * fields to reconstruct the object.
     *
     * @param className      class marker value
     * @param deserializer   function that takes field map and returns the domain object
     */
    public static void registerType(String className, Function<Map<String, Object>, Object> deserializer) {
        TYPE_REGISTRY.put(className, deserializer);
    }

    /**
     * Unregister a previously registered type.
     */
    public static void unregisterType(String className) {
        TYPE_REGISTRY.remove(className);
    }

    /**
     * Returns an unmodifiable view of the current type registry.
     */
    public static Map<String, Function<Map<String, Object>, Object>> getTypeRegistry() {
        return Map.copyOf(TYPE_REGISTRY);
    }

    // ========== Serialization ==========

    public static byte[] serializeMessage(DmqMessage message) throws Exception {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("class", message.getClass().getSimpleName());
        data.put("message_id", message.getMessageId());
        data.put("body", serializePayload(message.getBody(), 0));
        data.put("error_code", message.getErrorCode());
        data.put("error_msg", message.getErrorMsg());
        if (message instanceof DmqRequestMessage request) {
            data.put("type", request.getType().name());
            data.put("reply_topic", request.getReplyTopic());
            data.put("request_id", request.getRequestId());
            data.put("sender_id", request.getSenderId());
            data.put("receiver_id", request.getReceiverId());
            data.put("enable_stream", request.isEnableStream());
            data.put("expire_at", request.getExpireAt());
        } else if (message instanceof DmqResponseMessage response) {
            data.put("type", response.getType().name());
            data.put("result_type", response.getResultType().name());
            data.put("request_id", response.getRequestId());
            data.put("sender_id", response.getSenderId());
            data.put("receiver_id", response.getReceiverId());
            data.put("seq", response.getSeq());
            data.put("last_chunk", response.isLastChunk());
            data.put("expire_at", response.getExpireAt());
        }
        return MAPPER.writeValueAsBytes(data);
    }

    // ========== Deserialization ==========

    @SuppressWarnings("unchecked")
    public static DmqMessage deserializeMessage(byte[] bytes) throws Exception {
        Map<String, Object> data = MAPPER.readValue(bytes, Map.class);
        // Recursively deserialize the body payload (handles __class__ and __type__ markers)
        data.put("body", deserializePayload(data.get("body"), 0));

        String className = String.valueOf(data.get("class"));
        if ("DmqResponseMessage".equals(className)) {
            DmqResponseMessage response = new DmqResponseMessage();
            response.setResultType(ResultType.valueOf(String.valueOf(data.getOrDefault("result_type", "MESSAGE"))));
            response.setSeq(((Number) data.getOrDefault("seq", 0)).intValue());
            response.setLastChunk(Boolean.TRUE.equals(data.get("last_chunk")));
            populateCommonFields(response, data);
            return response;
        }
        DmqRequestMessage request = new DmqRequestMessage();
        request.setEnableStream(Boolean.TRUE.equals(data.get("enable_stream")));
        request.setReplyTopic(String.valueOf(data.getOrDefault("reply_topic", "")));
        populateCommonFields(request, data);
        return request;
    }

    // ========== Recursive payload helpers ==========

    /**
     * Recursively serialize a payload with depth limit.
     * Handles Enum values, Maps, Collections/arrays, and datetime objects.
     */
    @SuppressWarnings("unchecked")
    static Object serializePayload(Object payload, int depth) {
        if (depth > MAX_RECURSE_DEPTH) {
            throw new StackOverflowError("Payload nested too deep (> " + MAX_RECURSE_DEPTH + ")");
        }
        if (payload == null) {
            return null;
        }
        // Enum -> value
        if (payload instanceof Enum<?> e) {
            return e.name();
        }
        // datetime
        if (payload instanceof OffsetDateTime odt) {
            Map<String, Object> dt = new LinkedHashMap<>();
            dt.put("__type__", "datetime");
            dt.put("value", odt.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            return dt;
        }
        if (payload instanceof LocalDateTime ldt) {
            Map<String, Object> dt = new LinkedHashMap<>();
            dt.put("__type__", "datetime");
            dt.put("value", ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return dt;
        }
        if (payload instanceof Instant instant) {
            Map<String, Object> dt = new LinkedHashMap<>();
            dt.put("__type__", "datetime");
            dt.put("value", instant.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
            return dt;
        }
        // Map
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), serializePayload(entry.getValue(), depth + 1));
            }
            return result;
        }
        // Collection / array
        if (payload instanceof Collection<?> coll) {
            List<Object> result = new ArrayList<>(coll.size());
            for (Object item : coll) {
                result.add(serializePayload(item, depth + 1));
            }
            return result;
        }
        if (payload.getClass().isArray()) {
            Object[] arr = (Object[]) payload;
            List<Object> result = new ArrayList<>(arr.length);
            for (Object item : arr) {
                result.add(serializePayload(item, depth + 1));
            }
            return result;
        }
        // Primitives (String, Number, Boolean)
        return payload;
    }

    /**
     * Recursively deserialize a payload with depth limit.
     * Handles {@code __class__} markers (type registry) and {@code __type__: datetime}.
     */
    @SuppressWarnings("unchecked")
    static Object deserializePayload(Object payload, int depth) {
        if (depth > MAX_RECURSE_DEPTH) {
            throw new StackOverflowError("Payload nested too deep (> " + MAX_RECURSE_DEPTH + ")");
        }
        if (payload == null) {
            return null;
        }
        // Dict with __type__ == "datetime"
        if (payload instanceof Map<?, ?> map && "datetime".equals(map.get("__type__"))) {
            String isoValue = String.valueOf(map.get("value"));
            try {
                return OffsetDateTime.parse(isoValue, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
            } catch (Exception e) {
                return LocalDateTime.parse(isoValue, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }
        // Dict with __class__ marker -> type registry
        if (payload instanceof Map<?, ?> map && map.containsKey("__class__")) {
            String clsName = String.valueOf(map.get("__class__"));
            Function<Map<String, Object>, Object> factory = TYPE_REGISTRY.get(clsName);
            if (factory == null) {
                throw new IllegalArgumentException("Unknown payload class: " + clsName);
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
        // Plain Map
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), deserializePayload(entry.getValue(), depth + 1));
            }
            return result;
        }
        // List
        if (payload instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object item : list) {
                result.add(deserializePayload(item, depth + 1));
            }
            return result;
        }
        // Primitives
        return payload;
    }

    // ========== Internal ==========

    private static void populateCommonFields(DmqMessage message, Map<String, Object> data) {
        message.setMessageId(String.valueOf(data.getOrDefault("message_id", "")));
        message.setBody(data.get("body"));
        if (message instanceof DmqRequestMessage request) {
            request.setType(DMessageType.valueOf(String.valueOf(data.getOrDefault("type", "INPUT"))));
            request.setRequestId(String.valueOf(data.getOrDefault("request_id", "")));
            request.setSenderId(String.valueOf(data.getOrDefault("sender_id", "")));
            request.setReceiverId(String.valueOf(data.getOrDefault("receiver_id", "")));
            if (data.get("expire_at") instanceof Number number) {
                request.setExpireAt(number.doubleValue());
            }
        }
        if (message instanceof DmqResponseMessage response) {
            response.setType(DMessageType.valueOf(String.valueOf(data.getOrDefault("type", "OUTPUT"))));
            response.setRequestId(String.valueOf(data.getOrDefault("request_id", "")));
            response.setSenderId(String.valueOf(data.getOrDefault("sender_id", "")));
            response.setReceiverId(String.valueOf(data.getOrDefault("receiver_id", "")));
            if (data.get("expire_at") instanceof Number number) {
                response.setExpireAt(number.doubleValue());
            }
        }
        if (data.get("error_code") instanceof Number number) {
            message.setErrorCode(number.intValue());
        }
        message.setErrorMsg(String.valueOf(data.getOrDefault("error_msg", "")));
    }
}
