/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema.events;

import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.MemberCanceledEvent;
import com.openjiuwen.agent_teams.schema.MemberExecutionChangedEvent;
import com.openjiuwen.agent_teams.schema.MemberRestartedEvent;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.MemberSpawnedEvent;
import com.openjiuwen.agent_teams.schema.MemberStatusChangedEvent;
import com.openjiuwen.agent_teams.schema.TeamCleanedEvent;
import com.openjiuwen.agent_teams.schema.TeamCreatedEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamStandbyEvent;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Minimal event envelope for team messager transports.
 *
 * <p>Mirrors Python's {@code EventMessage} in
 * {@code openjiuwen.agent_teams.schema.events}.</p>
 */
public class EventMessage {

    private final String eventType;
    private final Map<String, Object> payload;
    private final Object payloadObject;
    private String senderId = "";

    public EventMessage(String eventType, Map<String, Object> payload) {
        this(eventType, payload, null);
    }

    private EventMessage(String eventType, Map<String, Object> payload, Object payloadObject) {
        this.eventType = eventType != null ? eventType : "";
        this.payload = payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
        this.payloadObject = payloadObject;
    }

    public String getEventType() {
        return eventType;
    }

    public Map<String, Object> getPayload() {
        return new LinkedHashMap<>(payload);
    }

    public Object getPayloadObject() {
        return payloadObject;
    }

    public String getSenderId() {
        return senderId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId != null ? senderId : "";
    }

    public static EventMessage fromEvent(BaseEventMessage event) {
        if (event == null) {
            throw new IllegalArgumentException("event is required");
        }
        return new EventMessage(inferEventType(event), serializeEventPayload(event), event);
    }

    private static String inferEventType(BaseEventMessage event) {
        if (event instanceof TeamCreatedEvent) {
            return TeamEvent.CREATED;
        }
        if (event instanceof TeamCleanedEvent) {
            return TeamEvent.CLEANED;
        }
        if (event instanceof TeamStandbyEvent) {
            return TeamEvent.STANDBY;
        }
        if (event instanceof MemberSpawnedEvent) {
            return TeamEvent.MEMBER_SPAWNED;
        }
        if (event instanceof MemberRestartedEvent) {
            return TeamEvent.MEMBER_RESTARTED;
        }
        if (event instanceof MemberStatusChangedEvent) {
            return TeamEvent.MEMBER_STATUS_CHANGED;
        }
        if (event instanceof MemberExecutionChangedEvent) {
            return TeamEvent.MEMBER_EXECUTION_CHANGED;
        }
        if (event instanceof MemberShutdownEvent) {
            return TeamEvent.MEMBER_SHUTDOWN;
        }
        if (event instanceof MemberCanceledEvent) {
            return TeamEvent.MEMBER_CANCELED;
        }
        return event.getClass().getSimpleName();
    }

    private static Map<String, Object> serializeEventPayload(BaseEventMessage event) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (Method method : event.getClass().getMethods()) {
            if (method.getParameterCount() != 0 || method.getDeclaringClass() == Object.class) {
                continue;
            }
            String name = method.getName();
            if (!name.startsWith("get") || "getClass".equals(name)) {
                continue;
            }
            try {
                Object value = method.invoke(event);
                if (value != null) {
                    values.put(toSnakeCase(name.substring(3)), value);
                }
            } catch (ReflectiveOperationException ignored) {
                // Best-effort payload extraction for lightweight transport envelopes.
            }
        }
        return values;
    }

    private static String toSnakeCase(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (Character.isUpperCase(ch) && i > 0) {
                builder.append('_');
            }
            builder.append(Character.toLowerCase(ch));
        }
        return builder.toString();
    }
}
