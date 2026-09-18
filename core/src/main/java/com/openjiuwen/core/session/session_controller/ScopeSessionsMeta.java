/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Session metadata collection under a specific session scope key.
 *
 * <p>Mirrors Python's {@code ScopeSessionsMeta} in
 * {@code openjiuwen/core/session/session_controller/schema.py}.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScopeSessionsMeta {

    @JsonProperty("session_scope_key")
    private String sessionScopeKey;

    @JsonProperty("active_session")
    private String activeSession;

    @JsonProperty("sessions")
    private List<SessionMeta> sessions = new ArrayList<>();

    public ScopeSessionsMeta() {
    }

    public ScopeSessionsMeta(String sessionScopeKey) {
        this(sessionScopeKey, null, List.of());
    }

    public ScopeSessionsMeta(String sessionScopeKey, String activeSession, List<SessionMeta> sessions) {
        this.sessionScopeKey = sessionScopeKey;
        this.activeSession = activeSession;
        setSessions(sessions);
    }

    public SessionMeta getSession(String sessionId) {
        for (SessionMeta session : sessions) {
            if (session.getSessionId() != null && session.getSessionId().equals(sessionId)) {
                return session;
            }
        }
        return null;
    }

    public void addSession(SessionMeta sessionMeta) {
        if (sessionMeta.isActive()) {
            deactivateAllSessions();
            activeSession = sessionMeta.getSessionId();
        }
        sessions.add(sessionMeta);
        sortSessions();
    }

    public SessionMeta removeSession(String sessionId) {
        for (int index = 0; index < sessions.size(); index++) {
            SessionMeta session = sessions.get(index);
            if (session.getSessionId() != null && session.getSessionId().equals(sessionId)) {
                SessionMeta removed = sessions.remove(index);
                if (sessionId.equals(activeSession)) {
                    activeSession = null;
                }
                return removed;
            }
        }
        return null;
    }

    public boolean activateSession(String sessionId) {
        SessionMeta session = getSession(sessionId);
        if (session == null) {
            return false;
        }
        deactivateAllSessions();
        session.setActive(true);
        session.updateTimestamp();
        activeSession = sessionId;
        sortSessions();
        return true;
    }

    public void deactivateAllSessions() {
        for (SessionMeta session : sessions) {
            session.setActive(false);
        }
        activeSession = null;
    }

    public void sortSessions() {
        sessions.sort(Comparator.comparingDouble(SessionMeta::getUpdatedAt).reversed());
    }

    public SessionMeta getActiveSessionMeta() {
        if (activeSession == null || activeSession.isEmpty()) {
            return null;
        }
        return getSession(activeSession);
    }

    public boolean updateSessionTimestamp(String sessionId) {
        SessionMeta session = getSession(sessionId);
        if (session == null) {
            return false;
        }
        session.updateTimestamp();
        sortSessions();
        return true;
    }

    public boolean incrementSessionVersion(String sessionId) {
        SessionMeta session = getSession(sessionId);
        if (session == null) {
            return false;
        }
        session.incrementVersion();
        return true;
    }

    public Map<String, Object> toMap() {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        result.put("session_scope_key", sessionScopeKey);
        result.put("active_session", activeSession);
        result.put("sessions", sessions.stream().map(SessionMeta::toMap).toList());
        return result;
    }

    public Map<String, Object> to_dict() {
        return toMap();
    }

    public static ScopeSessionsMeta fromMap(Map<String, Object> data) {
        List<SessionMeta> parsedSessions = new ArrayList<>();
        Object rawSessions = data.get("sessions");
        if (rawSessions instanceof List<?> values) {
            for (Object value : values) {
                if (value instanceof Map<?, ?> map) {
                    parsedSessions.add(SessionMeta.fromMap(stringObjectMap(map)));
                }
            }
        }
        return new ScopeSessionsMeta(
                stringValue(data.get("session_scope_key")),
                stringValue(data.get("active_session")),
                parsedSessions
        );
    }

    public static ScopeSessionsMeta from_dict(Map<String, Object> data) {
        return fromMap(data);
    }

    public String getSessionScopeKey() {
        return sessionScopeKey;
    }

    public void setSessionScopeKey(String sessionScopeKey) {
        this.sessionScopeKey = sessionScopeKey;
    }

    public String getActiveSession() {
        return activeSession;
    }

    public void setActiveSession(String activeSession) {
        this.activeSession = activeSession;
    }

    public List<SessionMeta> getSessions() {
        return List.copyOf(sessions);
    }

    public void setSessions(List<SessionMeta> sessions) {
        this.sessions = sessions == null ? new ArrayList<>() : new ArrayList<>(sessions);
    }

    private static Map<String, Object> stringObjectMap(Map<?, ?> map) {
        LinkedHashMap<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static String stringValue(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
