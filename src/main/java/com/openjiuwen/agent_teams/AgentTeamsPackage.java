/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.TeamAgent;
import com.openjiuwen.agent_teams.constants.TeamConstants;
import com.openjiuwen.agent_teams.external.ExternalTeamClient;
import com.openjiuwen.agent_teams.external.TeamJoinDescriptor;
import com.openjiuwen.agent_teams.interaction.HumanAgentInbox;
import com.openjiuwen.agent_teams.interaction.HumanAgentNotEnabledError;
import com.openjiuwen.agent_teams.interaction.InteractionRouter;
import com.openjiuwen.agent_teams.interaction.UnknownHumanAgentError;
import com.openjiuwen.agent_teams.interaction.UserInbox;
import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.messager.Messager;
import com.openjiuwen.agent_teams.messager.MessagerPeerConfig;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.messager.Messagers;
import com.openjiuwen.agent_teams.messager.PyZmqMessager;
import com.openjiuwen.agent_teams.models.ModelPoolEntry;
import com.openjiuwen.agent_teams.runtime.RunAction;
import com.openjiuwen.agent_teams.runtime.RunActionKind;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeActivation;
import com.openjiuwen.agent_teams.runtime.TeamRuntimeManager;
import com.openjiuwen.agent_teams.schema.DeepAgentSpec;
import com.openjiuwen.agent_teams.schema.LeaderSpec;
import com.openjiuwen.agent_teams.schema.StorageSpec;
import com.openjiuwen.agent_teams.schema.TeamAgentSpec;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamLifecycle;
import com.openjiuwen.agent_teams.schema.TeamMemberSpec;
import com.openjiuwen.agent_teams.schema.TeamOutputSchema;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.TeamRuntimeContext;
import com.openjiuwen.agent_teams.schema.TeamSpec;
import com.openjiuwen.agent_teams.schema.TransportSpec;
import com.openjiuwen.agent_teams.spawn.InProcessSpawnHandle;
import com.openjiuwen.agent_teams.tools.MemoryDatabaseConfig;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * AgentTeam public interfaces facade.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams} in
 * {@code openjiuwen/agent_teams/__init__.py}.</p>
 */
public final class AgentTeamsPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/__init__.py";
    public static final String DESCRIPTION = "AgentTeam public interfaces.";

    public static final String DEFAULT_LEADER_MEMBER_NAME = TeamConstants.DEFAULT_LEADER_MEMBER_NAME;
    public static final String HUMAN_AGENT_MEMBER_NAME = TeamConstants.HUMAN_AGENT_MEMBER_NAME;
    public static final Set<String> RESERVED_MEMBER_NAMES = TeamConstants.RESERVED_MEMBER_NAMES;
    public static final String USER_PSEUDO_MEMBER_NAME = TeamConstants.USER_PSEUDO_MEMBER_NAME;
    public static final String TEAM_JOIN_ENV = TeamJoinDescriptor.TEAM_JOIN_ENV;

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "DEFAULT_LEADER_MEMBER_NAME",
            "DeepAgentSpec",
            "ExternalTeamClient",
            "HUMAN_AGENT_MEMBER_NAME",
            "TEAM_JOIN_ENV",
            "TeamJoinDescriptor",
            "HumanAgentInbox",
            "HumanAgentNotEnabledError",
            "UnknownHumanAgentError",
            "LeaderSpec",
            "ModelPoolEntry",
            "RESERVED_MEMBER_NAMES",
            "StorageSpec",
            "TeamAgentSpec",
            "TransportSpec",
            "USER_PSEUDO_MEMBER_NAME",
            "UserInbox",
            "is_reserved_name",
            "parse_mention",
            "Messager",
            "MessagerPeerConfig",
            "MessagerTransportConfig",
            "RunAction",
            "RunActionKind",
            "TeamAgent",
            "TeamEvent",
            "TeamLifecycle",
            "TeamMemberSpec",
            "TeamOutputSchema",
            "TeamRole",
            "TeamRuntimeActivation",
            "TeamRuntimeContext",
            "TeamRuntimeManager",
            "TeamSpec",
            "InProcessMessager",
            "PyZmqMessager",
            "create_messager",
            "InProcessSpawnHandle",
            "MemoryDatabaseConfig"
    );

    public static final Map<String, Class<?>> EXPORTED_TYPES = exportedTypes();

    private AgentTeamsPackage() {
    }

    public static List<String> all() {
        return EXPORTED_SYMBOLS;
    }

    public static boolean exports(String symbolName) {
        return EXPORTED_SYMBOLS.contains(symbolName);
    }

    public static Class<?> typeFor(String exportedName) {
        return EXPORTED_TYPES.get(exportedName);
    }

    public static boolean isReservedName(String name) {
        return InteractionRouter.isReservedName(name);
    }

    public static Optional<InteractionRouter.Mention> parseMention(String content) {
        return InteractionRouter.parseMention(content);
    }

    public static Messager createMessager(MessagerTransportConfig config) {
        return Messagers.createMessager(config);
    }

    private static Map<String, Class<?>> exportedTypes() {
        Map<String, Class<?>> exports = new LinkedHashMap<>();
        exports.put("DeepAgentSpec", DeepAgentSpec.class);
        exports.put("ExternalTeamClient", ExternalTeamClient.class);
        exports.put("TeamJoinDescriptor", TeamJoinDescriptor.class);
        exports.put("HumanAgentInbox", HumanAgentInbox.class);
        exports.put("HumanAgentNotEnabledError", HumanAgentNotEnabledError.class);
        exports.put("UnknownHumanAgentError", UnknownHumanAgentError.class);
        exports.put("LeaderSpec", LeaderSpec.class);
        exports.put("ModelPoolEntry", ModelPoolEntry.class);
        exports.put("StorageSpec", StorageSpec.class);
        exports.put("TeamAgentSpec", TeamAgentSpec.class);
        exports.put("TransportSpec", TransportSpec.class);
        exports.put("UserInbox", UserInbox.class);
        exports.put("Messager", Messager.class);
        exports.put("MessagerPeerConfig", MessagerPeerConfig.class);
        exports.put("MessagerTransportConfig", MessagerTransportConfig.class);
        exports.put("RunAction", RunAction.class);
        exports.put("RunActionKind", RunActionKind.class);
        exports.put("TeamAgent", TeamAgent.class);
        exports.put("TeamEvent", TeamEvent.class);
        exports.put("TeamLifecycle", TeamLifecycle.class);
        exports.put("TeamMemberSpec", TeamMemberSpec.class);
        exports.put("TeamOutputSchema", TeamOutputSchema.class);
        exports.put("TeamRole", TeamRole.class);
        exports.put("TeamRuntimeActivation", TeamRuntimeActivation.class);
        exports.put("TeamRuntimeContext", TeamRuntimeContext.class);
        exports.put("TeamRuntimeManager", TeamRuntimeManager.class);
        exports.put("TeamSpec", TeamSpec.class);
        exports.put("InProcessMessager", InProcessMessager.class);
        exports.put("PyZmqMessager", PyZmqMessager.class);
        exports.put("InProcessSpawnHandle", InProcessSpawnHandle.class);
        exports.put("MemoryDatabaseConfig", MemoryDatabaseConfig.class);
        return Collections.unmodifiableMap(exports);
    }
}
