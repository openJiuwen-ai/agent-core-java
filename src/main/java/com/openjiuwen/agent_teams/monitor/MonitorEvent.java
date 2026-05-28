/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.monitor;

/**
 * Monitor event types.
 * <p>
 * Mirrors Python's {@code MonitorEvent} in
 * {@code openjiuwen.agent_teams.monitor.models}.
 */
public class MonitorEvent {

    private final String eventType;
    private final String teamId;
    private final String sessionId;
    private final Object data;
    private final long timestamp;

    public MonitorEvent(String eventType, String teamId, String sessionId, Object data) {
        this.eventType = eventType;
        this.teamId = teamId;
        this.sessionId = sessionId;
        this.data = data;
        this.timestamp = System.currentTimeMillis();
    }

    public String getEventType() { return eventType; }
    public String getTeamId() { return teamId; }
    public String getSessionId() { return sessionId; }
    public Object getData() { return data; }
    public long getTimestamp() { return timestamp; }

    public static MonitorEvent fromEventMessage(Object eventMessage) {
        // Placeholder: convert event message to monitor event
        return new MonitorEvent("unknown", "", "", eventMessage);
    }
}