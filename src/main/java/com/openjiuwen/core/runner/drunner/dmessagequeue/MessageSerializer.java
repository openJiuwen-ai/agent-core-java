// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.dmessagequeue;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.session.interaction.InteractionOutput;
import com.openjiuwen.core.session.stream.CustomSchema;
import com.openjiuwen.core.session.stream.OutputSchema;
import com.openjiuwen.core.session.stream.TraceSchema;
import com.openjiuwen.core.workflow.WorkflowExecutionState;
import com.openjiuwen.core.workflow.WorkflowOutput;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Message serializer for distributed message queue.
 * Serializes/deserializes messages with type markers for polymorphic reconstruction.
 * 
 * 对应Python: drunner/dmessage_queue/message_serializer.py
 */
public class MessageSerializer {

    private static final int MAX_RECURSE_DEPTH = 10;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Type registry mapping class names to reconstruction logic.
     */
    private static final Map<String, Class<?>> TYPE_REGISTRY = new LinkedHashMap<>();

    static {
        TYPE_REGISTRY.put("OutputSchema", OutputSchema.class);
        TYPE_REGISTRY.put("CustomSchema", CustomSchema.class);
        TYPE_REGISTRY.put("TraceSchema", TraceSchema.class);
        TYPE_REGISTRY.put("InteractionOutput", InteractionOutput.class);
        TYPE_REGISTRY.put("WorkflowOutput", WorkflowOutput.class);
        TYPE_REGISTRY.put("DmqRequestMessage", DmqRequestMessage.class);
        TYPE_REGISTRY.put("DmqResponseMessage", DmqResponseMessage.class);
    }

    /**
     * Serialize a message to JSON bytes.
     */
    public static byte[] serializeMessage(Object msg) {
        Object data = serializePayload(msg, 0);
        try {
            return OBJECT_MAPPER.writeValueAsBytes(data);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize message", e);
        }
    }

    /**
     * Deserialize JSON bytes to a message object.
     */
    public static Object deserializeMessage(byte[] data) {
        try {
            Object obj = OBJECT_MAPPER.readValue(data, Object.class);
            return deserializePayload(obj, 0);
        } catch (IOException e) {
            throw new RuntimeException("Failed to deserialize message", e);
        }
    }

    /**
     * Serialize a payload recursively.
     */
    @SuppressWarnings("unchecked")
    static Object serializePayload(Object payload, int depth) {
        if (depth > MAX_RECURSE_DEPTH) {
            throw new StackOverflowError("Payload nested too deep (> " + MAX_RECURSE_DEPTH + ")");
        }

        if (payload == null) {
            return null;
        }

        // Enum
        if (payload instanceof Enum<?> e) {
            // For enums with getValue(), use getValue; otherwise use name
            if (payload instanceof DMessageType dmt) return dmt.getValue();
            if (payload instanceof ResultType rt) return rt.getValue();
            if (payload instanceof WorkflowExecutionState wes) return wes.getValue();
            return e.name();
        }

        // LocalDateTime
        if (payload instanceof LocalDateTime ldt) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__type__", "datetime");
            result.put("value", ldt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return result;
        }

        // DmqRequestMessage
        if (payload instanceof DmqRequestMessage req) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__class__", "DmqRequestMessage");
            result.put("message_id", req.getMessageId());
            result.put("payload", serializePayload(req.getPayload(), depth + 1));
            result.put("error_code", req.getErrorCode());
            result.put("error_msg", req.getErrorMsg());
            result.put("type", serializePayload(req.getType(), depth + 1));
            result.put("reply_topic", req.getReplyTopic());
            result.put("request_id", req.getRequestId());
            result.put("sender_id", req.getSenderId());
            result.put("receiver_id", req.getReceiverId());
            result.put("enable_stream", req.isEnableStream());
            result.put("expire_at", req.getExpireAt());
            return result;
        }

        // DmqResponseMessage
        if (payload instanceof DmqResponseMessage resp) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__class__", "DmqResponseMessage");
            result.put("message_id", resp.getMessageId());
            result.put("payload", serializePayload(resp.getPayload(), depth + 1));
            result.put("error_code", resp.getErrorCode());
            result.put("error_msg", resp.getErrorMsg());
            result.put("type", serializePayload(resp.getType(), depth + 1));
            result.put("result_type", serializePayload(resp.getResultType(), depth + 1));
            result.put("request_id", resp.getRequestId());
            result.put("sender_id", resp.getSenderId());
            result.put("receiver_id", resp.getReceiverId());
            result.put("seq", resp.getSeq());
            result.put("last_chunk", resp.isLastChunk());
            result.put("expire_at", resp.getExpireAt());
            return result;
        }

        // OutputSchema
        if (payload instanceof OutputSchema os) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__class__", "OutputSchema");
            result.put("type", os.type());
            result.put("index", os.index());
            result.put("payload", serializePayload(os.payload(), depth + 1));
            return result;
        }

        // CustomSchema
        if (payload instanceof CustomSchema cs) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__class__", "CustomSchema");
            for (Map.Entry<String, Object> entry : cs.getData().entrySet()) {
                result.put(entry.getKey(), serializePayload(entry.getValue(), depth + 1));
            }
            return result;
        }

        // TraceSchema
        if (payload instanceof TraceSchema ts) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__class__", "TraceSchema");
            result.put("type", ts.type());
            result.put("payload", serializePayload(ts.payload(), depth + 1));
            return result;
        }

        // InteractionOutput
        if (payload instanceof InteractionOutput io) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__class__", "InteractionOutput");
            result.put("id", io.id());
            result.put("value", serializePayload(io.value(), depth + 1));
            return result;
        }

        // WorkflowOutput
        if (payload instanceof WorkflowOutput wo) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("__class__", "WorkflowOutput");
            result.put("result", serializePayload(wo.getResult(), depth + 1));
            result.put("state", serializePayload(wo.getState(), depth + 1));
            return result;
        }

        // List
        if (payload instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(serializePayload(item, depth + 1));
            }
            return result;
        }

        // Map
        if (payload instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), serializePayload(entry.getValue(), depth + 1));
            }
            return result;
        }

        // Primitives (String, Number, Boolean, null)
        return payload;
    }

    /**
     * Deserialize a payload recursively.
     */
    @SuppressWarnings("unchecked")
    static Object deserializePayload(Object payload, int depth) {
        if (depth > MAX_RECURSE_DEPTH) {
            throw new StackOverflowError("Payload nested too deep (> " + MAX_RECURSE_DEPTH + ")");
        }

        if (payload == null) {
            return null;
        }

        // Dict with __type__ marker (datetime)
        if (payload instanceof Map<?, ?> map && map.containsKey("__type__")) {
            String type = String.valueOf(map.get("__type__"));
            if ("datetime".equals(type)) {
                String value = String.valueOf(map.get("value"));
                return LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            }
        }

        // Dict with __class__ marker - typed object
        if (payload instanceof Map<?, ?> rawMap && rawMap.containsKey("__class__")) {
            Map<String, Object> map = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                map.put(String.valueOf(entry.getKey()), entry.getValue());
            }

            String className = String.valueOf(map.remove("__class__"));
            if (!TYPE_REGISTRY.containsKey(className)) {
                throw new IllegalArgumentException("Unknown payload class: " + className);
            }

            // Deserialize fields recursively
            Map<String, Object> deserializedFields = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                deserializedFields.put(entry.getKey(), deserializePayload(entry.getValue(), depth + 1));
            }

            return reconstructObject(className, deserializedFields);
        }

        // List
        if (payload instanceof List<?> list) {
            List<Object> result = new ArrayList<>();
            for (Object item : list) {
                result.add(deserializePayload(item, depth + 1));
            }
            return result;
        }

        // Plain dict (no __class__ marker)
        if (payload instanceof Map<?, ?> rawMap) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : rawMap.entrySet()) {
                result.put(String.valueOf(entry.getKey()), deserializePayload(entry.getValue(), depth + 1));
            }
            return result;
        }

        // Primitives
        return payload;
    }

    /**
     * Reconstruct a typed object from deserialized fields.
     */
    private static Object reconstructObject(String className, Map<String, Object> fields) {
        return switch (className) {
            case "OutputSchema" -> new OutputSchema(
                    asString(fields.get("type")),
                    asInt(fields.get("index")),
                    fields.get("payload")
            );
            case "CustomSchema" -> new CustomSchema(fields);
            case "TraceSchema" -> new TraceSchema(
                    asString(fields.get("type")),
                    fields.get("payload")
            );
            case "InteractionOutput" -> new InteractionOutput(
                    asString(fields.get("id")),
                    fields.get("value")
            );
            case "WorkflowOutput" -> {
                WorkflowOutput wo = new WorkflowOutput();
                wo.setResult(fields.get("result"));
                Object stateObj = fields.get("state");
                if (stateObj instanceof String s) {
                    wo.setState(WorkflowExecutionState.valueOf(s));
                } else if (stateObj instanceof WorkflowExecutionState wes) {
                    wo.setState(wes);
                }
                yield wo;
            }
            case "DmqRequestMessage" -> {
                DmqRequestMessage msg = new DmqRequestMessage();
                msg.setMessageId(asString(fields.get("message_id")));
                msg.setPayload(fields.get("payload"));
                msg.setErrorCode(asInt(fields.get("error_code")));
                msg.setErrorMsg(asString(fields.get("error_msg")));
                msg.setType(asString(fields.get("type")));
                msg.setReplyTopic(asString(fields.get("reply_topic")));
                msg.setRequestId(asString(fields.get("request_id")));
                msg.setSenderId(asString(fields.get("sender_id")));
                msg.setReceiverId(asString(fields.get("receiver_id")));
                msg.setEnableStream(asBoolean(fields.get("enable_stream")));
                msg.setExpireAt(asDouble(fields.get("expire_at")));
                yield msg;
            }
            case "DmqResponseMessage" -> {
                DmqResponseMessage msg = new DmqResponseMessage();
                msg.setMessageId(asString(fields.get("message_id")));
                msg.setPayload(fields.get("payload"));
                msg.setErrorCode(asInt(fields.get("error_code")));
                msg.setErrorMsg(asString(fields.get("error_msg")));
                msg.setType(asString(fields.get("type")));
                Object rt = fields.get("result_type");
                if (rt instanceof String s) {
                    msg.setResultType(ResultType.valueOf(s));
                }
                msg.setRequestId(asString(fields.get("request_id")));
                msg.setSenderId(asString(fields.get("sender_id")));
                msg.setReceiverId(asString(fields.get("receiver_id")));
                msg.setSeq(asInt(fields.get("seq")));
                msg.setLastChunk(asBoolean(fields.get("last_chunk")));
                msg.setExpireAt(asDouble(fields.get("expire_at")));
                yield msg;
            }
            default -> throw new IllegalArgumentException("Unknown class: " + className);
        };
    }

    private static String asString(Object obj) {
        return obj != null ? String.valueOf(obj) : "";
    }

    private static int asInt(Object obj) {
        if (obj instanceof Number n) return n.intValue();
        if (obj instanceof String s) return Integer.parseInt(s);
        return 0;
    }

    private static boolean asBoolean(Object obj) {
        if (obj instanceof Boolean b) return b;
        if (obj instanceof String s) return Boolean.parseBoolean(s);
        return false;
    }

    private static Double asDouble(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number n) return n.doubleValue();
        if (obj instanceof String s) return Double.parseDouble(s);
        return null;
    }
}

