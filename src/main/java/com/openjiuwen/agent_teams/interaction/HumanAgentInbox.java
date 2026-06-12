/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import com.openjiuwen.agent_teams.constants.TeamConstants;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Human-agent-side inbox that routes typed {@link HumanAgentMessage} payloads.
 *
 * <p>Mirrors Python's {@code HumanAgentInbox} in
 * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
 */
public final class HumanAgentInbox {

    public static final Set<String> BROADCAST_TARGETS = Set.of("all", "*");

    private final TeamBackendView team;
    private final MessageManagerView messageManager;
    private final AgentLookup agentLookup;
    private final OnInbound onInbound;

    public HumanAgentInbox(TeamBackendView team, MessageManagerView messageManager) {
        this(team, messageManager, null, null);
    }

    public HumanAgentInbox(
            TeamBackendView team,
            MessageManagerView messageManager,
            AgentLookup agentLookup
    ) {
        this(team, messageManager, agentLookup, null);
    }

    public HumanAgentInbox(
            TeamBackendView team,
            MessageManagerView messageManager,
            AgentLookup agentLookup,
            OnInbound onInbound
    ) {
        this.team = Objects.requireNonNull(team, "team");
        this.messageManager = Objects.requireNonNull(messageManager, "messageManager");
        this.agentLookup = agentLookup;
        this.onInbound = onInbound;
    }

    public OnInbound getOnInbound() {
        return onInbound;
    }

    public CompletionStage<DeliverResult> send(String body) {
        return send(body, null, null);
    }

    public CompletionStage<DeliverResult> send(String body, String to) {
        return send(body, to, null);
    }

    public CompletionStage<DeliverResult> send(String body, String to, String sender) {
        String safeBody = body == null ? "" : body;
        return resolveSender(sender).thenCompose(resolvedSender -> {
            if (to == null) {
                return driveAgent(safeBody, resolvedSender);
            }
            if (BROADCAST_TARGETS.contains(to)) {
                return messageManager.broadcastMessage(safeBody, resolvedSender)
                        .thenApply(messageId -> messageId == null
                                ? DeliverResult.failure("broadcast_failed")
                                : DeliverResult.success(messageId));
            }
            return deliverDirect(safeBody, resolvedSender, to);
        });
    }

    CompletionStage<String> resolveSender(String sender) {
        return team.humanAgentNames().thenApply(names -> {
            List<String> registered = names == null ? List.of() : new ArrayList<>(names);
            if (registered.isEmpty()) {
                throw new HumanAgentNotEnabledError(
                        "No human-agent member is registered on this team; create the team with enable_hitt=true "
                                + "or declare TeamMemberSpec(role_type=TeamRole.HUMAN_AGENT, ...) entries "
                                + "in predefined_members"
                );
            }
            if (sender == null) {
                if (registered.contains(TeamConstants.HUMAN_AGENT_MEMBER_NAME)) {
                    return TeamConstants.HUMAN_AGENT_MEMBER_NAME;
                }
                return registered.stream().sorted(Comparator.naturalOrder()).findFirst().orElseThrow();
            }
            if (!registered.contains(sender)) {
                throw new UnknownHumanAgentError(
                        "'" + sender + "' is not a registered human-agent member; registered members: "
                                + registered.stream().sorted().toList()
                );
            }
            return sender;
        });
    }

    private CompletionStage<DeliverResult> driveAgent(String body, String sender) {
        if (agentLookup == null) {
            return CompletableFuture.completedFuture(DeliverResult.failure("agent_unavailable"));
        }
        return agentLookup.lookup(sender).thenCompose(agent -> {
            if (agent == null) {
                return CompletableFuture.completedFuture(DeliverResult.failure("agent_unavailable"));
            }
            return agent.deliverInput(body).thenApply(ignored -> DeliverResult.success());
        });
    }

    private CompletionStage<DeliverResult> deliverDirect(String body, String sender, String target) {
        return team.getMember(target).thenCompose(member -> {
            if (member == null) {
                return CompletableFuture.completedFuture(DeliverResult.failure("unknown_member:" + target));
            }
            return messageManager.sendMessage(body, target, sender)
                    .thenApply(messageId -> messageId == null
                            ? DeliverResult.failure("send_failed:" + target)
                            : DeliverResult.success(messageId));
        });
    }

    /**
     * Team lookup surface used by the human-agent inbox.
     *
     * <p>Mirrors Python's {@code TeamBackend} calls in
     * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
     */
    public interface TeamBackendView {
        CompletionStage<List<String>> humanAgentNames();

        CompletionStage<Object> getMember(String name);
    }

    /**
     * Message persistence surface used by the human-agent inbox.
     *
     * <p>Mirrors Python's {@code TeamMessageManager} calls in
     * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
     */
    public interface MessageManagerView {
        CompletionStage<String> broadcastMessage(String content, String fromMemberName);

        CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName);
    }

    /**
     * Resolve a human-agent member name to a live runtime.
     *
     * <p>Mirrors Python's {@code AgentLookup} in
     * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
     */
    @FunctionalInterface
    public interface AgentLookup {
        CompletionStage<AgentRuntime> lookup(String memberName);
    }

    /**
     * Live human-agent avatar runtime.
     *
     * <p>Mirrors Python's {@code TeamAgent.deliver_input} use in
     * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
     */
    @FunctionalInterface
    public interface AgentRuntime {
        CompletionStage<Void> deliverInput(String body);
    }

    /**
     * Callback fired for inbound team messages addressed to a human agent.
     *
     * <p>Mirrors Python's {@code OnInbound} in
     * {@code openjiuwen/agent_teams/interaction/human_agent_inbox.py}.</p>
     */
    @FunctionalInterface
    public interface OnInbound {
        CompletionStage<Void> onInbound(HumanAgentInboundEvent event);
    }
}
