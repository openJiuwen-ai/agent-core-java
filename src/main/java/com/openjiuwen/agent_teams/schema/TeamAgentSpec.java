/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.schema;

import com.openjiuwen.agent_teams.AgentTeamPaths;
import com.openjiuwen.agent_teams.agent.AgentConfigurator;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.DeepAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamMemberSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.models.Allocation;
import com.openjiuwen.agent_teams.models.ModelAllocator;
import com.openjiuwen.agent_teams.models.ModelAllocators;
import com.openjiuwen.agent_teams.models.ModelPoolEntry;
import com.openjiuwen.agent_teams.models.ModelRouterConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseConfig;
import com.openjiuwen.agent_teams.tools.database.DatabaseType;
import com.openjiuwen.harness.prompts.HarnessPromptsPackage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * JSON-serializable specification for constructing a configured {@link TeamAgent}.
 *
 * <p>Mirrors Python's {@code TeamAgentSpec} in
 * {@code openjiuwen/agent_teams/schema/blueprint.py}.</p>
 */
public class TeamAgentSpec extends AgentConfigurator.TeamAgentSpec {

    private boolean enableTeamPlan;
    private LeaderSpec leader = new LeaderSpec();
    private List<ModelPoolEntry> modelPool = new ArrayList<>();
    private ModelRouterConfig modelRouter;
    private String modelPoolStrategy = "round_robin";
    private String language;
    private TransportSpec transport;
    private StorageSpec storage;

    public TeamAgentSpec() {
        super();
    }

    public Object resolveDbConfig() {
        Object config = storage == null ? new DatabaseConfig() : storage.build();
        if (config instanceof DatabaseConfig databaseConfig) {
            if (databaseConfig.getDbType() == DatabaseType.SQLITE
                    && (databaseConfig.getConnectionString() == null
                    || databaseConfig.getConnectionString().isBlank())) {
                databaseConfig.setConnectionString(
                        AgentTeamPaths.getAgentTeamsHome().resolve("team.db").toString());
            }
        }
        return config;
    }

    public TeamAgent build() {
        normalizeTransportForSpawnMode();
        validatePoolRouterExclusive();
        validateExternalCliUnique();
        validateReservedNames();
        validateHittConsistency();
        validateBridgeConsistency();

        DeepAgentSpec leaderAgent = getAgents().get("leader");
        if (leaderAgent == null) {
            throw new IllegalArgumentException("agents dict must contain a 'leader' key");
        }

        String resolvedLanguage = HarnessPromptsPackage.resolveLanguage(language);
        for (DeepAgentSpec roleSpec : getAgents().values()) {
            if (roleSpec.getLanguage() == null) {
                roleSpec.setLanguage(resolvedLanguage);
            }
        }

        List<ModelPoolEntry> teamPool;
        String teamStrategy;
        if (modelRouter != null) {
            teamPool = modelRouter.toPoolEntries();
            teamStrategy = "router";
        } else {
            teamPool = new ArrayList<>(modelPool);
            teamStrategy = modelPoolStrategy;
        }

        AgentConfigurator.TeamSpec teamSpec = new AgentConfigurator.TeamSpec(
                getTeamName(),
                getTeamName(),
                leader.getMemberName()
        );
        teamSpec.setLanguage(resolvedLanguage);
        teamSpec.setMetadata(getMetadata());
        teamSpec.setModelPool(teamPool);
        teamSpec.setModelPoolStrategy(teamStrategy);

        MessagerTransportConfig messagerConfig = null;
        if (transport != null) {
            Object builtTransport = transport.build();
            if (!(builtTransport instanceof MessagerTransportConfig config)) {
                throw new IllegalStateException("TransportSpec.build() must return MessagerTransportConfig");
            }
            messagerConfig = config;
        }

        AgentConfigurator.TeamRuntimeContext context = new AgentConfigurator.TeamRuntimeContext();
        context.setRole(TeamRole.LEADER);
        context.setMemberName(leader.getMemberName());
        context.setPersona(leader.getPersona());
        context.setTeamSpec(teamSpec);
        context.setMessagerConfig(messagerConfig);
        context.setDbConfig(dbConfigMap(resolveDbConfig()));

        ModelAllocator modelAllocator = ModelAllocators.buildModelAllocator(this, teamSpec);
        Allocation leaderAllocation = modelAllocator == null ? null : modelAllocator.allocate(leader.getModelName());
        Object leaderMemberModel = leaderAllocation == null ? null : leaderAllocation.toTeamModelConfig();
        context.setMemberModel(leaderMemberModel);
        validateLeaderModelResolved(leaderAgent, leaderAllocation, teamPool, teamStrategy);

        String leaderCardId = getTeamName() + "_" + leader.getMemberName();
        AgentCard leaderCard = new AgentCard(
                leaderCardId,
                leader.getDisplayName(),
                "Leader of team " + getTeamName()
        );
        TeamAgent agent = new TeamAgent(leaderCard);
        agent.attachModelAllocator(modelAllocator, leaderAllocation);
        agent.configure(this, context);
        return agent;
    }

    private void normalizeTransportForSpawnMode() {
        if (transport == null && Objects.equals(getSpawnMode(), "inprocess")) {
            transport = new TransportSpec("inprocess");
        }
    }

    private void validatePoolRouterExclusive() {
        if (modelRouter != null && !modelPool.isEmpty()) {
            throw new IllegalArgumentException(
                    "model_pool and model_router are mutually exclusive; configure one or the other");
        }
    }

    private void validateExternalCliUnique() {
        Set<String> seen = new LinkedHashSet<>();
        Set<String> duplicates = new LinkedHashSet<>();
        for (Object entry : getExternalCliAgents()) {
            String cliAgent = cliAgentName(entry);
            if (cliAgent == null) {
                continue;
            }
            if (!seen.add(cliAgent)) {
                duplicates.add(cliAgent);
            }
        }
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException(
                    "external_cli_agents has duplicate cli_agent name(s) " + duplicates
                            + "; declare each CLI kind at most once");
        }
    }

    private void validateReservedNames() {
        Set<String> leaderForbidden = new LinkedHashSet<>(TeamConstants.RESERVED_MEMBER_NAMES);
        leaderForbidden.remove(TeamConstants.DEFAULT_LEADER_MEMBER_NAME);
        if (leaderForbidden.contains(leader.getMemberName())) {
            throw new IllegalArgumentException(
                    "LeaderSpec.member_name '" + leader.getMemberName() + "' is reserved; pick a different name");
        }
        for (TeamMemberSpec member : getPredefinedMembers()) {
            if (member.getRoleType() == TeamRole.HUMAN_AGENT) {
                continue;
            }
            if (TeamConstants.RESERVED_MEMBER_NAMES.contains(member.getMemberName())) {
                throw new IllegalArgumentException(
                        "predefined member '" + member.getMemberName()
                                + "' uses a reserved name (reserved: "
                                + TeamConstants.RESERVED_MEMBER_NAMES + ")");
            }
        }
    }

    private void validateHittConsistency() {
        if (isEnableHitt()) {
            return;
        }
        List<String> offenders = getPredefinedMembers().stream()
                .filter(member -> member.getRoleType() == TeamRole.HUMAN_AGENT)
                .map(TeamMemberSpec::getMemberName)
                .toList();
        if (!offenders.isEmpty()) {
            throw new IllegalArgumentException(
                    "predefined_members contains HUMAN_AGENT role(s) " + offenders
                            + " but enable_hitt=False; set enable_hitt=True or remove the human member(s)");
        }
    }

    private void validateBridgeConsistency() {
        if (isEnableBridge()) {
            return;
        }
        List<String> offenders = getPredefinedMembers().stream()
                .filter(member -> member.getRoleType() == TeamRole.BRIDGE_AGENT)
                .map(TeamMemberSpec::getMemberName)
                .toList();
        if (!offenders.isEmpty()) {
            throw new IllegalArgumentException(
                    "predefined_members contains BRIDGE_AGENT role(s) " + offenders
                            + " but enable_bridge=False; set enable_bridge=True or remove the bridge member(s)");
        }
    }

    private void validateLeaderModelResolved(
            DeepAgentSpec leaderAgent,
            Allocation leaderAllocation,
            List<ModelPoolEntry> teamPool,
            String teamStrategy
    ) {
        if (leaderAllocation != null || leaderAgent.getModel() != null || teamPool.isEmpty()) {
            return;
        }
        List<String> availableNames = teamPool.stream().map(ModelPoolEntry::getModelName).sorted().toList();
        String leaderName = leader.getModelName();
        String cause;
        if (leaderName != null && !availableNames.contains(leaderName)) {
            String scope = "router".equals(teamStrategy) ? "router" : "pool";
            cause = "leader.model_name='" + leaderName + "' is not present in the " + scope
                    + " (available names: " + availableNames + ")";
        } else if ("by_model_name".equals(teamStrategy)) {
            cause = "model_pool_strategy='by_model_name' requires leader.model_name to be set to one of the pool names";
        } else {
            cause = "the allocator did not produce a model for the leader";
        }
        throw new IllegalArgumentException(cause);
    }

    private static Map<String, Object> dbConfigMap(Object config) {
        if (config instanceof DatabaseConfig databaseConfig) {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("db_type", databaseConfig.getDbType().value());
            values.put("connection_string", databaseConfig.getConnectionString());
            values.put("db_timeout", databaseConfig.getDbTimeout());
            values.put("db_enable_wal", databaseConfig.isDbEnableWal());
            return values;
        }
        if (config instanceof Map<?, ?> map) {
            Map<String, Object> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                values.put(String.valueOf(entry.getKey()), entry.getValue());
            }
            return values;
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("config", config);
        return values;
    }

    private static String cliAgentName(Object entry) {
        if (entry instanceof Map<?, ?> map) {
            Object value = map.containsKey("cli_agent") ? map.get("cli_agent") : map.get("cliAgent");
            return value == null ? null : String.valueOf(value);
        }
        try {
            Object value = entry.getClass().getMethod("getCliAgent").invoke(entry);
            return value == null ? null : String.valueOf(value);
        } catch (ReflectiveOperationException ignored) {
            return null;
        }
    }

    public boolean isEnableTeamPlan() {
        return enableTeamPlan;
    }

    public void setEnableTeamPlan(boolean enableTeamPlan) {
        this.enableTeamPlan = enableTeamPlan;
    }

    public LeaderSpec getLeader() {
        return leader;
    }

    public void setLeader(LeaderSpec leader) {
        this.leader = leader == null ? new LeaderSpec() : leader;
    }

    public List<ModelPoolEntry> getModelPool() {
        return new ArrayList<>(modelPool);
    }

    public void setModelPool(List<ModelPoolEntry> modelPool) {
        this.modelPool = modelPool == null ? new ArrayList<>() : new ArrayList<>(modelPool);
    }

    public ModelRouterConfig getModelRouter() {
        return modelRouter;
    }

    public void setModelRouter(ModelRouterConfig modelRouter) {
        this.modelRouter = modelRouter;
    }

    public String getModelPoolStrategy() {
        return modelPoolStrategy;
    }

    public void setModelPoolStrategy(String modelPoolStrategy) {
        this.modelPoolStrategy = modelPoolStrategy;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public TransportSpec getTransport() {
        return transport;
    }

    public void setTransport(TransportSpec transport) {
        this.transport = transport;
    }

    public StorageSpec getStorage() {
        return storage;
    }

    public void setStorage(StorageSpec storage) {
        this.storage = storage;
    }
}
