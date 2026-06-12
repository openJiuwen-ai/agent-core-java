/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import java.util.List;

/**
 * Package bridge for the external interaction layer exports.
 *
 * <p>Mirrors Python's {@code openjiuwen.agent_teams.interaction} in
 * {@code openjiuwen/agent_teams/interaction/__init__.py}.</p>
 */
public final class InteractionPackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_teams/interaction/__init__.py";
    public static final String DESCRIPTION = "External interaction layer for agent teams.";

    public static final Class<BridgeAgentNotEnabledError> BRIDGE_AGENT_NOT_ENABLED_ERROR =
            BridgeAgentNotEnabledError.class;
    public static final Class<BridgeProtocolAdapter> BRIDGE_PROTOCOL_ADAPTER = BridgeProtocolAdapter.class;
    public static final Class<DeliverResult> DELIVER_RESULT = DeliverResult.class;
    public static final Class<GodViewMessage> GOD_VIEW_MESSAGE = GodViewMessage.class;
    public static final Class<HumanAgentInboundEvent> HUMAN_AGENT_INBOUND_EVENT = HumanAgentInboundEvent.class;
    public static final Class<HumanAgentMessage> HUMAN_AGENT_MESSAGE = HumanAgentMessage.class;
    public static final Class<InteractPayload> INTERACT_PAYLOAD = InteractPayload.class;
    public static final Class<OperatorMessage> OPERATOR_MESSAGE = OperatorMessage.class;
    public static final String REMOTE_UNAVAILABLE_SENTINEL = BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL;
    public static final Class<UnknownBridgeAgentError> UNKNOWN_BRIDGE_AGENT_ERROR =
            UnknownBridgeAgentError.class;

    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "BridgeAgentNotEnabledError",
            "BridgeProtocolAdapter",
            "DeliverResult",
            "GodViewMessage",
            "HumanAgentInbox",
            "HumanAgentInboundEvent",
            "HumanAgentMessage",
            "HumanAgentNotEnabledError",
            "InteractPayload",
            "OperatorMessage",
            "REMOTE_UNAVAILABLE_SENTINEL",
            "UnknownBridgeAgentError",
            "UnknownHumanAgentError",
            "UserInbox",
            "is_reserved_name",
            "parse_interact_str",
            "parse_mention"
    );

    private InteractionPackage() {
    }
}
