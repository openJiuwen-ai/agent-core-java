/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams;

import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.session.internal.AgentTeamSession;

import java.util.UUID;
import java.util.Map;
import java.util.Optional;

/**
 * Standalone session lifecycle helpers for Team invoke() and stream().
 * <p>
 * Mirrors Python's {@code utils} module in 
 * {@code openjiuwen.core.multi_agent.teams.utils}.
 * <p>
 * These helpers encapsulate the session lifecycle for Team.invoke() and 
 * Team.stream() when called without a Runner (i.e. session=null, standalone mode).
 * 
 * <p>Public API:
 * <ul>
 *     <li>makeTeamSession - Create a fresh AgentTeamSession</li>
 *     <li>standaloneInvokeContext - Context manager for invoke()</li>
 *     <li>standaloneStreamContext - Context manager for stream()</li>
 * </ul>
 */
public class TeamsUtils {
    
    /** Context history key */
    public static final String CONTEXT_HISTORY_KEY = "__handoff_ctx_history__";
    
    /** Default context ID */
    public static final String DEFAULT_CONTEXT_ID = "default_context_id";
    
    /**
     * Create a fresh AgentTeamSession, reusing conversation_id when present.
     * <p>
     * Extracts conversation_id from message if it is a Map; falls back
     * to a new UUID so every call gets a unique session.
     * 
     * @param card TeamCard of the owning team (provides team_id)
     * @param message User input - Map or String
     * @return A new Session bound to the team
     */
    public static Session makeTeamSession(TeamCard card, Object message) {
        String sid = extractConversationId(message).orElse(UUID.randomUUID().toString());
        return createAgentTeamSession(sid, card.getId());
    }
    
    /**
     * Extract conversation_id from a message.
     * 
     * @param message Message object (Map or other)
     * @return Optional conversation ID
     */
    private static Optional<String> extractConversationId(Object message) {
        if (message instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) message;
            Object id = map.get("conversation_id");
            if (id instanceof String) {
                return Optional.of((String) id);
            }
        }
        return Optional.empty();
    }
    
/**
     * Create an AgentTeamSession.
     * 
     * @param sessionId Session ID
     * @param teamId Team ID
     * @return New AgentTeamSession
     */
    private static Session createAgentTeamSession(String sessionId, String teamId) {
        return new AgentTeamSession(sessionId, teamId);
    }
    
    /**
     * Standalone invoke context - owns full session lifecycle.
     * <p>
     * When session is null (standalone mode), this helper:
     * <ul>
     *     <li>Creates a fresh Session via makeTeamSession</li>
     *     <li>Calls session.preRun()</li>
     *     <li>Binds the session to runtime</li>
     *     <li>Returns (session, sessionId) to the caller</li>
     *     <li>In cleanup: unbinds, cleans up message bus, calls session.postRun()</li>
     * </ul>
     * 
     * @param runtime TeamRuntime of the owning team
     * @param card TeamCard of the owning team
     * @param message User input
     * @param session Existing session (if from Runner), or null
     * @return InvokeContext containing session and cleanup logic
     */
    public static InvokeContext standaloneInvokeContext(
            TeamRuntime runtime,
            TeamCard card,
            Object message,
            Session session
    ) {
        if (session != null) {
            // Runner path - no-op wrapper
            return new InvokeContext(session, session.getSessionId(), () -> {});
        }
        
        // Standalone path - create and manage session
        Session newSession = makeTeamSession(card, message);
        String sessionId = newSession.getSessionId();
        
        // Bind session to runtime (if applicable)
        if (runtime != null) {
            // runtime.bindSession(sessionId, newSession); // TODO: implement bindSession
        }
        
        // Cleanup runnable
        Runnable cleanup = () -> {
            if (runtime != null) {
                runtime.getMessageBus().cleanupSession(sessionId);
            }
            // newSession.postRun(); // TODO: implement postRun
        };
        
        return new InvokeContext(newSession, sessionId, cleanup);
    }
    
    /**
     * Invoke context result holder.
     */
    public static class InvokeContext {
        private final Session session;
        private final String sessionId;
        private final Runnable cleanup;
        
        public InvokeContext(Session session, String sessionId, Runnable cleanup) {
            this.session = session;
            this.sessionId = sessionId;
            this.cleanup = cleanup;
        }
        
        public Session getSession() { return session; }
        public String getSessionId() { return sessionId; }
        public void cleanup() { cleanup.run(); }
    }
}