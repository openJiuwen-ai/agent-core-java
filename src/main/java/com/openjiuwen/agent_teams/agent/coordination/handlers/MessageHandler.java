/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import com.openjiuwen.agent_teams.AgentTeamI18n;
import com.openjiuwen.agent_teams.AgentTeamTimefmt;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.BaseEventMessage;
import com.openjiuwen.agent_teams.schema.BroadcastEvent;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.MessageEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Handles message, broadcast, mailbox poll, and shutdown mailbox-drain events.
 *
 * <p>Mirrors Python's {@code MessageHandler} in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/message.py}.</p>
 */
public class MessageHandler extends BaseCoordinationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(MessageHandler.class);

    public MessageHandler(
            DispatcherHost host,
            TeamAgentBlueprint blueprint,
            TeamInfra infra,
            PollController pollController
    ) {
        super(host, blueprint, infra, pollController);
    }

    @Override
    public Map<String, String> getEventMethodMap() {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(TeamEvent.MESSAGE, "onMessageOrBroadcast");
        map.put(TeamEvent.BROADCAST, "onMessageOrBroadcast");
        map.put(InnerEventType.POLL_MAILBOX.value(), "onPollMailbox");
        map.put(TeamEvent.MEMBER_SHUTDOWN, "onMemberShutdownDrain");
        return map;
    }

    @Override
    protected EventCallback resolveCallback(String methodName) {
        return switch (methodName) {
            case "onMessageOrBroadcast" -> this::onMessageOrBroadcast;
            case "onPollMailbox" -> this::onPollMailbox;
            case "onMemberShutdownDrain" -> this::onMemberShutdownDrain;
            default -> throw new IllegalArgumentException("Unknown method: " + methodName);
        };
    }

    public CompletionStage<Void> onMessageOrBroadcast(CoordinationEvent event) {
        String memberName = blueprint.getMemberName();
        if (memberName == null || memberName.isEmpty() || !(infra.getMessageManager() instanceof MessageManager)) {
            return CompletableFuture.completedFuture(null);
        }

        CompletionStage<Void> leaderWork = CompletableFuture.completedFuture(null);
        if (blueprint.getRole() == TeamRole.LEADER) {
            if (TeamEvent.MESSAGE.equals(event.eventKey())) {
                leaderWork = leaderWork.thenCompose(ignored -> ackUserBoundMessage(event));
            }
            leaderWork = leaderWork.thenCompose(ignored -> notifyHumanAgentInbound(event));
        }
        return leaderWork
                .thenCompose(ignored -> poll.resumePolls())
                .thenCompose(ignored -> processUnreadMessages(memberName, true));
    }

    public CompletionStage<Void> onPollMailbox(CoordinationEvent event) {
        String memberName = blueprint.getMemberName();
        LOGGER.debug("poll mailbox: member_name={}", memberName);
        if (memberName == null || memberName.isEmpty() || !(infra.getMessageManager() instanceof MessageManager)) {
            return CompletableFuture.completedFuture(null);
        }
        return processUnreadMessages(memberName, true);
    }

    public CompletionStage<Void> onMemberShutdownDrain(CoordinationEvent event) {
        if (blueprint.getRole() != TeamRole.TEAMMATE) {
            return CompletableFuture.completedFuture(null);
        }
        String memberName = blueprint.getMemberName();
        if (memberName == null || memberName.isEmpty() || !(infra.getMessageManager() instanceof MessageManager)) {
            return CompletableFuture.completedFuture(null);
        }
        BaseEventMessage payload = messageOf(event).getPayload();
        if (!(payload instanceof MemberShutdownEvent) || !memberName.equals(payload.getMemberName())) {
            return CompletableFuture.completedFuture(null);
        }
        return processUnreadMessages(memberName, true);
    }

    public CompletionStage<Void> processUnreadMessages(String memberName, boolean useSteer) {
        Set<String> seenIds = new LinkedHashSet<>();
        return isHumanAgent(memberName).thenCompose(isHumanAgent -> processUnreadLoop(memberName, useSteer, isHumanAgent, seenIds));
    }

    public CompletionStage<String> bridgeDeliverableFor(String memberName, TeamMessage message) {
        if (blueprint.getRole() == TeamRole.BRIDGE_AGENT && infra.getTeamBackend() instanceof BridgeDeliveryBackend backend) {
            return backend.bridgeDeliverableFor(memberName, message);
        }
        return CompletableFuture.completedFuture(formatMessage(message, false, System.currentTimeMillis()));
    }

    public CompletionStage<Void> ackUserBoundMessage(CoordinationEvent event) {
        BaseEventMessage payload = messageOf(event).getPayload();
        if (!(payload instanceof MessageEvent messagePayload) || !"user".equals(messagePayload.getToMemberName())) {
            return CompletableFuture.completedFuture(null);
        }
        if (!(infra.getMessageManager() instanceof MessageManager messageManager)) {
            return CompletableFuture.completedFuture(null);
        }
        return messageManager.markMessageRead(messagePayload.getMessageId(), "user")
                .thenRun(() -> LOGGER.debug(
                        "leader auto-acked user-bound message {} from {}",
                        messagePayload.getMessageId(),
                        messagePayload.getFromMemberName()
                ));
    }

    public CompletionStage<Void> notifyHumanAgentInbound(CoordinationEvent event) {
        if (!(infra.getTeamBackend() instanceof TeamBackendView backend)
                || !(infra.getMessageManager() instanceof MessageManager messageManager)) {
            return CompletableFuture.completedFuture(null);
        }
        BaseEventMessage payload = messageOf(event).getPayload();
        String messageId = messageIdOf(payload);
        String sender = senderOf(payload);
        boolean broadcast = TeamEvent.BROADCAST.equals(event.eventKey());
        if (messageId == null) {
            return CompletableFuture.completedFuture(null);
        }

        return messageManager.getMessage(messageId)
                .thenCompose(optional -> optional
                        .map(row -> recipientsForInbound(backend, payload, sender, broadcast)
                                .thenCompose(recipients -> notifyRecipients(backend, recipients, row, sender, broadcast)))
                        .orElseGet(() -> CompletableFuture.completedFuture(null)));
    }

    public CompletionStage<List<TeamMessage>> readAllUnread(String memberName) {
        if (!(infra.getMessageManager() instanceof MessageManager messageManager)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return messageManager.getMessages(memberName, true)
                .thenCombine(messageManager.getBroadcastMessages(memberName, true), (direct, broadcasts) -> {
                    List<TeamMessage> merged = new ArrayList<>();
                    merged.addAll(direct == null ? List.of() : direct);
                    merged.addAll(broadcasts == null ? List.of() : broadcasts);
                    merged.sort(Comparator.comparing(
                            TeamMessage::getTimestamp,
                            Comparator.nullsFirst(Comparator.naturalOrder())
                    ).reversed());
                    return merged;
                });
    }

    public String formatMessage(TeamMessage message, boolean humanAgent, long nowMs) {
        String messageType = Boolean.TRUE.equals(message.getBroadcast())
                ? AgentTeamI18n.t("dispatcher.msg_type_broadcast")
                : AgentTeamI18n.t("dispatcher.msg_type_direct");
        String key = humanAgent ? "hitt.msg_received_for_human" : "dispatcher.msg_received";
        return AgentTeamI18n.t(
                key,
                "msg_type",
                messageType,
                "message_id",
                message.getMessageId(),
                "sender",
                message.getFromMemberName(),
                "content",
                message.getContent(),
                "time_info",
                AgentTeamTimefmt.formatTimeContext(message.getTimestamp(), nowMs)
        );
    }

    private CompletionStage<Void> processUnreadLoop(
            String memberName,
            boolean useSteer,
            boolean humanAgent,
            Set<String> seenIds
    ) {
        return readAllUnread(memberName).thenCompose(allUnread -> {
            List<TeamMessage> newMessages = allUnread.stream()
                    .filter(message -> seenIds.add(message.getMessageId()))
                    .toList();
            if (newMessages.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            LOGGER.info("[{}] processing {} unread messages (steer={})", memberName, newMessages.size(), useSteer);
            return processMessageList(memberName, newMessages, humanAgent, useSteer)
                    .thenCompose(continueLoop -> continueLoop
                            ? processUnreadLoop(memberName, useSteer, humanAgent, seenIds)
                            : CompletableFuture.completedFuture(null));
        });
    }

    private CompletionStage<Boolean> processMessageList(
            String memberName,
            List<TeamMessage> messages,
            boolean humanAgent,
            boolean useSteer
    ) {
        CompletionStage<Boolean> chain = CompletableFuture.completedFuture(true);
        for (TeamMessage message : messages) {
            chain = chain.thenCompose(continueProcessing -> {
                if (!continueProcessing || round.hasPendingInterrupt()) {
                    if (round.hasPendingInterrupt()) {
                        LOGGER.info(
                                "[{}] deferring mailbox message {} until pending interrupt is resolved",
                                memberName,
                                message.getMessageId()
                        );
                    }
                    return CompletableFuture.completedFuture(false);
                }
                CompletionStage<String> textStage = blueprint.getRole() == TeamRole.BRIDGE_AGENT
                        ? bridgeDeliverableFor(memberName, message)
                        : CompletableFuture.completedFuture(formatMessage(message, humanAgent, System.currentTimeMillis()));
                return textStage
                        .thenCompose(text -> round.deliverInput(text, useSteer))
                        .thenCompose(ignored -> ((MessageManager) infra.getMessageManager())
                                .markMessageRead(message.getMessageId(), memberName))
                        .thenApply(ignored -> true);
            });
        }
        return chain;
    }

    private CompletionStage<Boolean> isHumanAgent(String memberName) {
        if (infra.getTeamBackend() instanceof TeamBackendView backend) {
            return backend.isHumanAgent(memberName);
        }
        return CompletableFuture.completedFuture(false);
    }

    private CompletionStage<List<String>> recipientsForInbound(
            TeamBackendView backend,
            BaseEventMessage payload,
            String sender,
            boolean broadcast
    ) {
        if (broadcast) {
            return backend.humanAgentNames()
                    .thenApply(names -> names.stream().filter(name -> !Objects.equals(name, sender)).toList());
        }
        if (!(payload instanceof MessageEvent messagePayload)) {
            return CompletableFuture.completedFuture(List.of());
        }
        String target = messagePayload.getToMemberName();
        return backend.isHumanAgent(target)
                .thenApply(isHuman -> isHuman ? List.of(target) : List.of());
    }

    private CompletionStage<Void> notifyRecipients(
            TeamBackendView backend,
            List<String> recipients,
            TeamMessage row,
            String sender,
            boolean broadcast
    ) {
        CompletionStage<Void> chain = CompletableFuture.completedFuture(null);
        for (String recipient : recipients) {
            chain = chain.thenCompose(ignored -> {
                HumanAgentInboundCallback callback = backend.getHumanAgentInbound(recipient);
                if (callback == null) {
                    return CompletableFuture.completedFuture(null);
                }
                HumanAgentInboundEvent event = new HumanAgentInboundEvent(
                        recipient,
                        sender,
                        row.getContent(),
                        broadcast,
                        row.getMessageId(),
                        row.getTimestamp() == null ? 0L : row.getTimestamp()
                );
                return callback.onInbound(event).exceptionally(exception -> {
                    LOGGER.warn("human_agent on_inbound callback for {} raised: {}", recipient, exception.toString());
                    return null;
                });
            });
        }
        return chain;
    }

    private static EventMessage messageOf(CoordinationEvent event) {
        return ((TransportEvent) event).getMessage();
    }

    private static String messageIdOf(BaseEventMessage payload) {
        if (payload instanceof MessageEvent messageEvent) {
            return messageEvent.getMessageId();
        }
        if (payload instanceof BroadcastEvent broadcastEvent) {
            return broadcastEvent.getMessageId();
        }
        return null;
    }

    private static String senderOf(BaseEventMessage payload) {
        if (payload instanceof MessageEvent messageEvent) {
            return messageEvent.getFromMemberName();
        }
        if (payload instanceof BroadcastEvent broadcastEvent) {
            return broadcastEvent.getFromMemberName();
        }
        return null;
    }

    /**
     * Message manager surface used by mailbox drains.
     *
     * <p>Mirrors Python's message-manager calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/message.py}.</p>
     */
    public interface MessageManager {
        CompletionStage<List<TeamMessage>> getMessages(String memberName, boolean unreadOnly);

        CompletionStage<List<TeamMessage>> getBroadcastMessages(String memberName, boolean unreadOnly);

        CompletionStage<Void> markMessageRead(String messageId, String memberName);

        CompletionStage<Optional<TeamMessage>> getMessage(String messageId);
    }

    /**
     * Team backend surface used by message routing.
     *
     * <p>Mirrors Python's backend calls in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/message.py}.</p>
     */
    public interface TeamBackendView {
        CompletionStage<Boolean> isHumanAgent(String memberName);

        CompletionStage<List<String>> humanAgentNames();

        HumanAgentInboundCallback getHumanAgentInbound(String memberName);
    }

    /**
     * Optional bridge backend surface for bridge-agent mailbox delivery.
     *
     * <p>Mirrors Python's bridge delivery path in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/message.py}.</p>
     */
    public interface BridgeDeliveryBackend {
        CompletionStage<String> bridgeDeliverableFor(String memberName, TeamMessage message);
    }

    /**
     * Human-agent inbound callback.
     *
     * <p>Mirrors Python's human-agent inbound callback in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/message.py}.</p>
     */
    @FunctionalInterface
    public interface HumanAgentInboundCallback {
        CompletionStage<Void> onInbound(HumanAgentInboundEvent event);
    }

    /**
     * Human-agent inbound event payload.
     *
     * <p>Mirrors Python's {@code HumanAgentInboundEvent} use in
     * {@code openjiuwen/agent_teams/agent/coordination/handlers/message.py}.</p>
     */
    public record HumanAgentInboundEvent(
            String memberName,
            String sender,
            String body,
            boolean broadcast,
            String messageId,
            long timestamp
    ) {
    }
}
