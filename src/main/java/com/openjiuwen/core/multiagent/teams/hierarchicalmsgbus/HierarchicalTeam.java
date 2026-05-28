/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2025-2026. All rights reserved.
 */

package com.openjiuwen.core.multiagent.teams.hierarchicalmsgbus;

import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.multiagent.BaseTeam;
import com.openjiuwen.core.multiagent.schema.TeamCard;
import com.openjiuwen.core.singleagent.schema.AgentCard;

import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Hierarchical multi-agent team driven by supervisor agent.
 * <p>
 * Mirrors Python's {@code HierarchicalTeam} in 
 * {@code openjiuwen.core.multi_agent.teams.hierarchical_msgbus.hierarchical_team}.
 *
 * <p>Uses message bus for P2P communication between supervisor and sub-agents.
 * This is the messagebus-driven version (different from the tools-driven version
 * in hierarchicaltools package).
 */
public class HierarchicalTeam extends BaseTeam {
    
    private static final Logger LOG = LoggerFactory.getLogger(HierarchicalTeam.class);
    
    private final HierarchicalTeamConfig hierarchicalConfig;
    private String supervisorId;
    
    public HierarchicalTeam(TeamCard card, HierarchicalTeamConfig config) {
        super(card, config);
        this.hierarchicalConfig = config;
        this.supervisorId = config.getSupervisorAgent() != null ? 
            config.getSupervisorAgent().getId() : null;
    }
    
    @Override
    public HierarchicalTeam addAgent(AgentCard card, Supplier<?> provider) {
        super.addAgent(card, provider);
        
        if (card.getId().equals(supervisorId)) {
            if (hierarchicalConfig.getTimeout().isPresent()) {
                runtime.setP2pTimeout(hierarchicalConfig.getTimeout().get());
            }
            LOG.info("[HierarchicalTeam] Registered supervisor '{}' in team '{}'", 
                card.getId(), getTeamId());
        }
        
        return this;
    }
    
    /**
     * Assert team is ready for execution.
     * <p>
     * Mirrors Python's {@code _assert_ready}.
     */
    private void assertReady() {
        if (supervisorId == null) {
            ErrorHelper.raiseError(StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                "No supervisor configured in HierarchicalTeamConfig.", null, null, null);
        }
        if (!runtime.hasAgent(supervisorId)) {
            ErrorHelper.raiseError(StatusCode.AGENT_TEAM_EXECUTION_ERROR,
                "Supervisor '" + supervisorId + "' is not registered in runtime. " +
                "Call add_agent(supervisor_card, supervisor_provider) before invoke()/stream().",
                null, null, null);
        }
    }
    
    /**
     * Invoke the hierarchical team - run supervisor and return final result.
     * <p>
     * Mirrors Python's {@code invoke} method which uses runtime.send.
     *
     * @param input User input (map or string)
     * @return CompletableFuture with final result
     */
    @Override
    public CompletableFuture<Object> invoke(Object input) {
        assertReady();
        
        String sessionId = "hierarchical_msgbus_" + java.util.UUID.randomUUID().toString().replace("-", "");
        
        LOG.debug("[HierarchicalTeam] invoke start session_id={} supervisor={}", sessionId, supervisorId);
        
        // Get timeout from config
        double timeout = hierarchicalConfig.getTimeout().orElse(30.0);
        
        // Use runtime.send to invoke supervisor agent
        return runtime.getMessageBus().send(input, supervisorId,
                Optional.ofNullable(getTeamId()),
                Optional.of(sessionId),
                Optional.of(timeout))
            .whenComplete((result, error) -> {
                if (error != null) {
                    LOG.error("[HierarchicalTeam] invoke failed session_id={}: {}", sessionId, error.getMessage());
                } else {
                    LOG.debug("[HierarchicalTeam] invoke end session_id={}", sessionId);
                }
            });
    }
    
    /**
     * Stream the hierarchical team execution - run supervisor with real-time output.
     * <p>
     * Mirrors Python's {@code stream} method.
     *
     * @param input User input (map or string)
     * @return Stream of intermediate and final results
     */
    @Override
    public Stream<Object> stream(Object input) {
        assertReady();
        
        String sessionId = "hierarchical_stream_" + java.util.UUID.randomUUID().toString().replace("-", "");
        
        LOG.debug("[HierarchicalTeam] stream start session_id={} supervisor={}", sessionId, supervisorId);
        
        try {
            Object result = invoke(input).join();
            LOG.debug("[HierarchicalTeam] stream completed session_id={}", sessionId);
            return Stream.of(result);
        } catch (Exception e) {
            LOG.error("[HierarchicalTeam] stream failed session_id={}: {}", sessionId, e.getMessage());
            return Stream.empty();
        }
    }
    
    public String getSupervisorId() {
        return supervisorId;
    }
}
