/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicaltools;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.multiagent.teamruntime.TeamRuntime;
import com.openjiuwen.core.session.Session;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Agents-as-Tools hierarchical multi-agent team.
 * <p>
 * Mirrors Python's {@code HierarchicalTeam} in 
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus.hierarchical_team}.
 *
 * <p>Python features:
 * <ul>
 *   <li>Supervisor agent drives hierarchical execution</li>
 *   <li>invoke() uses runtime.send to supervisor agent</li>
 *   <li>stream() uses runtime streaming for real-time output</li>
 *   <li>Configurable timeout for P2P messaging</li>
 * </ul>
 */
public class HierarchicalTeam extends BaseTeam {
    
    private static final Logger LOG = LoggerFactory.getLogger(HierarchicalTeam.class);
    
    private final HierarchicalTeamConfig hierarchicalConfig;
    private final String rootAgentId;
    private final ConcurrentHashMap<String, java.util.List<AgentCard>> pendingChildren = new ConcurrentHashMap<>();
    
    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config) {
        this(card, config, null);
    }

    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config, TeamRuntime runtime) {
        super(card, config, runtime);
        if (config == null || config.getRootAgent() == null) {
            throw new IllegalArgumentException("rootAgent is required");
        }
        this.hierarchicalConfig = config;
        this.rootAgentId = config.getRootAgent().getId();
    }

    @Override
    public HierarchicalTeam addAgent(AgentCard card, Supplier<?> provider) {
        return addAgent(card, provider, null);
    }
    
    /**
     * Add agent with optional parent.
     */
    public HierarchicalTeam addAgent(AgentCard card, Supplier<?> provider, String parentAgentId) {
        super.addAgent(card, provider);
        
        if (parentAgentId != null) {
            pendingChildren.computeIfAbsent(parentAgentId, k -> new java.util.ArrayList<>()).add(card);
        }
        
        return this;
    }

    /**
     * Wire pending child cards into their parent agents' ability managers.
     * <p>
     * Mirrors Python's protected {@code _setup_hierarchy}.
     */
    public void setupHierarchy() {
        if (pendingChildren.isEmpty()) {
            return;
        }
        pendingChildren.forEach((parentId, childCards) -> {
            Object parentAgent = runtime.createAgent(parentId);
            Object abilityManager = resolveAbilityManager(parentAgent);
            if (abilityManager == null) {
                return;
            }
            for (AgentCard childCard : childCards) {
                invokeAbilityManagerAdd(abilityManager, childCard);
            }
        });
        pendingChildren.clear();
    }
    
    /**
     * Assert team is ready for execution.
     * <p>
     * Mirrors Python's {@code _assert_ready}.
     */
    private void assertReady() {
        if (rootAgentId == null || rootAgentId.isEmpty()) {
            ErrorHelper.raiseError(StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                "No root agent configured in HierarchicalTeamConfig.", null, null, null);
        }
        if (!runtime.hasAgent(rootAgentId)) {
            ErrorHelper.raiseError(StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                "Root agent '" + rootAgentId + "' is not registered in runtime. " +
                "Call add_agent(root_card, root_provider) before invoke()/stream().",
                null, null, null);
        }
    }
    
    /**
     * Invoke the hierarchical team - run root agent and return final result.
     * <p>
     * Mirrors Python's {@code invoke} method which:
     * <ul>
     *   <li>Asserts supervisor is configured and registered</li>
     *   <li>Uses standalone_invoke_context for session management</li>
     *   <li>Calls runtime.send to supervisor agent</li>
     *   <li>Returns the final result</li>
     * </ul>
     *
     * @param input User input (map or string)
     * @return CompletableFuture with final result
     */
    @Override
    public CompletableFuture<Object> invoke(Object input) {
        return invoke(input, null);
    }

    @Override
    public CompletableFuture<Object> invoke(Object input, Session session) {
        assertReady();
        setupHierarchy();

        String sessionId = session != null ? session.getSessionId() : resolveSessionId(input, "hierarchical_");
        
        LOG.debug("[HierarchicalTeam] invoke start session_id={} root={}", sessionId, rootAgentId);
        
        double timeout = hierarchicalConfig.getP2pTimeout();
        
        return runtime.send(input, rootAgentId, card.getId(), sessionId, timeout)
            .whenComplete((result, error) -> {
                if (error != null) {
                    LOG.error("[HierarchicalTeam] invoke failed session_id={}: {}", sessionId, error.getMessage());
                } else {
                    LOG.debug("[HierarchicalTeam] invoke end session_id={}", sessionId);
                }
            });
    }
    
    /**
     * Stream the hierarchical team execution - run root agent with real-time output.
     * <p>
     * Mirrors Python's {@code stream} method which:
     * <ul>
     *   <li>Asserts supervisor is configured and registered</li>
     *   <li>Uses standalone_stream_context for session management</li>
     *   <li>Streams from runtime to supervisor agent</li>
     *   <li>Returns AsyncIterator of results</li>
     * </ul>
     *
     * @param input User input (map or string)
     * @return Stream of intermediate and final results
     */
    @Override
    public Stream<Object> stream(Object input) {
        return stream(input, null);
    }

    @Override
    public Stream<Object> stream(Object input, Session session) {
        assertReady();
        setupHierarchy();

        String sessionId = session != null ? session.getSessionId() : resolveSessionId(input, "hierarchical_stream_");
        
        LOG.debug("[HierarchicalTeam] stream start session_id={} root={}", sessionId, rootAgentId);

        Object result = runtime.send(input, rootAgentId, card.getId(), sessionId, hierarchicalConfig.getP2pTimeout()).join();
        LOG.debug("[HierarchicalTeam] stream completed session_id={}", sessionId);
        return Stream.of(result);
    }
    
    public String getRootAgentId() {
        return rootAgentId;
    }
    
    /**
     * Get pending children for an agent.
     */
    public java.util.List<AgentCard> getPendingChildren(String agentId) {
        return pendingChildren.getOrDefault(agentId, java.util.Collections.emptyList());
    }

    private static String resolveSessionId(Object input, String prefix) {
        if (input instanceof Map<?, ?> map) {
            Object conversationId = map.get("conversation_id");
            if (conversationId != null) {
                return conversationId.toString();
            }
        }
        return prefix + java.util.UUID.randomUUID().toString().replace("-", "");
    }

    private static Object resolveAbilityManager(Object parentAgent) {
        if (parentAgent == null) {
            return null;
        }
        try {
            Method method = parentAgent.getClass().getMethod("getAbilityManager");
            return method.invoke(parentAgent);
        } catch (ReflectiveOperationException ignored) {
            try {
                Field field = parentAgent.getClass().getDeclaredField("abilityManager");
                field.setAccessible(true);
                return field.get(parentAgent);
            } catch (ReflectiveOperationException ignoredAgain) {
                return null;
            }
        }
    }

    private static void invokeAbilityManagerAdd(Object abilityManager, AgentCard childCard) {
        try {
            Method method = abilityManager.getClass().getMethod("add", AgentCard.class);
            method.setAccessible(true);
            method.invoke(abilityManager, childCard);
        } catch (NoSuchMethodException e) {
            try {
                Method method = abilityManager.getClass().getMethod("add", Object.class);
                method.setAccessible(true);
                method.invoke(abilityManager, childCard);
            } catch (ReflectiveOperationException ex) {
                throw new IllegalStateException("abilityManager.add failed", ex);
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("abilityManager.add failed", e);
        }
    }
}
