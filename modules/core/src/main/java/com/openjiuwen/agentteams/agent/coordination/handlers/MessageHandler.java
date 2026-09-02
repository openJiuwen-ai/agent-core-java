/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent.coordination.handlers;

import com.openjiuwen.agentteams.TeamConstants;
import com.openjiuwen.agentteams.agent.coordination.DispatcherHost;
import com.openjiuwen.agentteams.agent.coordination.InnerEventType;
import com.openjiuwen.agentteams.agent.coordination.PollController;
import com.openjiuwen.agentteams.agent.coordination.TeamAgentBlueprint;
import com.openjiuwen.agentteams.agent.coordination.TeamInfra;
import com.openjiuwen.agentteams.external.Format;
import com.openjiuwen.agentteams.interaction.HumanAgentInboundEvent;
import com.openjiuwen.agentteams.schema.events.CoordinationEvent;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.events.TeamEvent;
import com.openjiuwen.agentteams.schema.status.MemberStatus;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMessage;
import com.openjiuwen.agentteams.tools.TeamMessageManager;
import com.openjiuwen.agentteams.tools.database.MemberRecord;
import com.openjiuwen.core.common.logging.Loggers;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Handle MESSAGE / BROADCAST / POLL_MAILBOX + drain on member shutdown.
 *
 * <p>Mirrors Python {@code handlers/message.py}. Leader does extra work on
 * MESSAGE/BROADCAST: auto-acks teammate→user replies and notifies the SDK's
 * human-agent inbound callbacks. All members then resume polls and drain their
 * unread mailbox. On {@code MEMBER_SHUTDOWN} this handler drains the mailbox
 * before teardown (fan-out after {@link MemberHandler}).
 *
 * <p>Rendering is role-aware: a teammate/leader sees {@code dispatcher.msg_received}
 * via {@link Format#renderMessage}; a human_agent avatar sees
 * {@code hitt.msg_received_for_human}, which frames the message as a
 * notification for the controlling human and tells the avatar LLM not to
 * autonomously call {@code send_message}.
 *
 * @since 2026/7/9
 */
public class MessageHandler extends BaseCoordinationHandler {
    /**
     * Construct and register event bindings.
     *
     * @param host the owning TeamAgent
     * @param blueprint static config
     * @param infra per-process services
     * @param pollCtrl poll control surface
     */
    public MessageHandler(DispatcherHost host, TeamAgentBlueprint blueprint,
                          TeamInfra infra, PollController pollCtrl) {
        super(host, blueprint, infra, pollCtrl);
        callbacks.put(TeamEvent.MESSAGE, this::onMessageOrBroadcast);
        callbacks.put(TeamEvent.BROADCAST, this::onMessageOrBroadcast);
        callbacks.put(InnerEventType.POLL_MAILBOX.getValue(), this::onPollMailbox);
        callbacks.put(TeamEvent.MEMBER_SHUTDOWN, this::onMemberShutdownDrain);
    }

    /**
     * Handle MESSAGE / BROADCAST events.
     *
     * <p>Leader does extra work: auto-acks teammate→user replies (the
     * {@code user} pseudo-member has no agent process polling its mailbox) and
     * notifies the SDK's human-agent inbound callbacks. All members then resume
     * polls and drain their unread mailbox.
     *
     * @param event the message or broadcast event
     */
    public void onMessageOrBroadcast(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        if (memberName == null || memberName.isBlank()) {
            return;
        }
        Object mm = infra.messageManager();
        if (mm == null) {
            return;
        }
        if (blueprint.role().orElse(null) == TeamRole.LEADER && event instanceof EventMessage msg
                && TeamEvent.MESSAGE.equals(msg.getEventType())) {
            ackUserBoundMessage(msg);
        }
        if (blueprint.role().orElse(null) == TeamRole.LEADER && event instanceof EventMessage msg) {
            notifyHumanAgentInbound(msg);
        }
        poll.resumePolls();
        processUnreadMessages(memberName, true);
    }

    /**
     * Periodic mailbox sweep: drain any unread messages.
     *
     * @param event the poll mailbox event
     */
    public void onPollMailbox(CoordinationEvent event) {
        String memberName = blueprint.memberName().orElse(null);
        Loggers.AGENT.debug("poll mailbox: member_name={}", memberName);
        if (memberName != null && !memberName.isBlank()) {
            processUnreadMessages(memberName, true);
        }
    }

    /**
     * Drain own mailbox when this teammate is the one shutting down.
     *
     * <p>Teammate-only: the leader observes other members' shutdowns at the
     * lifecycle level, and a human agent has no autonomous round — draining its
     * mailbox would {@code deliver_input} and resurrect a round just as the
     * avatar is collapsing (its own teardown rides {@link MemberHandler} →
     * {@code shutdown_self} instead). Only the teammate whose own
     * {@code member_name} matches the event's payload drains. Steer mode
     * ensures the messages land even if the agent is mid-round.
     *
     * @param event the member shutdown event
     */
    public void onMemberShutdownDrain(CoordinationEvent event) {
        if (blueprint.role().orElse(null) != TeamRole.MEMBER) {
            return;
        }
        String memberName = blueprint.memberName().orElse(null);
        if (memberName == null || memberName.isBlank()) {
            return;
        }
        if (!(event instanceof EventMessage msg)) {
            return;
        }
        Map<String, Object> payload = msg.getPayload() != null ? msg.getPayload() : Map.of();
        String targetId = str(payload, "member_name");
        if (targetId == null || !targetId.equals(memberName)) {
            return;
        }
        processUnreadMessages(memberName, true);

        // After mailbox drain, if this member is still SHUTDOWN_REQUESTED and no
        // round is in flight (idle) nor pending interrupt, drive shutdownSelf
        // proactively. Otherwise the member blocks on stream_queue.poll forever,
        // the persisted status never transitions to SHUTDOWN, and the leader
        // spins polling list_members. Mirrors the Python _run_one_round finally
        // branch (stream_controller.py:204-206) but covers the idle-member case
        // where MEMBER_SHUTDOWN arrives between rounds.
        if (round.hasInFlightRound() || round.hasPendingInterrupt()) {
            return;
        }
        Object backend = infra.teamBackend();
        if (!(backend instanceof TeamBackend tb)) {
            return;
        }
        MemberRecord record = tb.getDb().member.getMember(memberName, tb.getTeamName());
        if (record == null) {
            return;
        }
        if (MemberStatus.SHUTDOWN_REQUESTED.value().equals(record.getStatus())) {
            Loggers.AGENT.info(
                    "[{}] MEMBER_SHUTDOWN received while idle, driving shutdownSelf",
                    memberName);
            lifecycle.shutdownSelf();
        }
    }

    /**
     * Read unread messages, feed to agent one by one, loop until no new messages.
     *
     * <p>The role lookup happens once up front: a member is or is not a
     * human-agent for the lifetime of this drain, so per-message
     * {@code is_human_agent} checks would just churn the same backend call.
     * The flag selects the harness-input template via {@link Format#renderMessage}.
     *
     * @param memberName current member ID
     * @param shouldUseSteer when {@code true}, use steer instead of follow_up
     */
    public void processUnreadMessages(String memberName, boolean shouldUseSteer) {
        if (memberName == null || memberName.isBlank()) {
            return;
        }
        Object mm = infra.messageManager();
        if (!(mm instanceof TeamMessageManager mgr)) {
            return;
        }
        boolean isHumanAgent = isHumanAgent(memberName);
        long nowMs = System.currentTimeMillis();
        Set<String> seenIds = new HashSet<>();
        List<String> deliveredIds = new ArrayList<>();
        try {
            MessageDeliveryContext ctx = new MessageDeliveryContext(
                    mgr, memberName, isHumanAgent, nowMs, shouldUseSteer);
            deliverUnreadMessages(ctx, seenIds, deliveredIds);
        } finally {
            // Batch read-state write — guaranteed even if deliverInput throws.
            // Mirrors Python's finally block in _process_unread_messages.
            for (String id : deliveredIds) {
                mgr.markMessageRead(id, memberName);
            }
        }
    }

    /**
     * Drain unread messages and deliver each one to the agent round.
     *
     * <p>Loops until no new unread messages remain or a pending interrupt
     * defers further delivery. Populates {@code deliveredIds} for the
     * caller to batch-mark as read in the finally block.</p>
     *
     * @param ctx delivery context bundling message-manager, member info, and flags
     * @param seenIds accumulator for message IDs already processed in this call
     * @param deliveredIds accumulator for message IDs successfully delivered
     */
    private void deliverUnreadMessages(
            MessageDeliveryContext ctx, Set<String> seenIds, List<String> deliveredIds) {
        while (true) {
            List<TeamMessage> unread = readAllUnread(ctx.mgr, ctx.memberName).stream()
                    .filter(message -> seenIds.add(message.getMessageId()))
                    .toList();
            Loggers.AGENT.debug("processUnreadMessages: member={} found {} unread message(s)",
                    ctx.memberName, unread.size());
            if (unread.isEmpty()) {
                break;
            }
            boolean isInterrupted = false;
            for (TeamMessage message : unread) {
                seenIds.add(message.getMessageId());
                if (round.hasPendingInterrupt()) {
                    Loggers.AGENT.info(
                            "[{}] deferring mailbox message {} until pending interrupt is resolved",
                            ctx.memberName, message.getMessageId());
                    isInterrupted = true;
                    break;
                }
                String text = Format.renderMessage(message, ctx.isHumanAgent, ctx.nowMs);
                Loggers.AGENT.debug("[{}] message from={}, id={}",
                        ctx.memberName, message.getFromMemberName(), message.getMessageId());
                round.deliverInput(text, ctx.shouldUseSteer);
                deliveredIds.add(message.getMessageId());
            }
            if (isInterrupted) {
                return;
            }
        }
    }

    private record MessageDeliveryContext(
            TeamMessageManager mgr, String memberName,
            boolean isHumanAgent, long nowMs, boolean shouldUseSteer) {
    }

    /**
     * Single-arg overload, defaults {@code useSteer=true}.
     *
     * @param memberName current member ID
     */
    public void processUnreadMessages(String memberName) {
        processUnreadMessages(memberName, true);
    }

    /**
     * Auto-ack teammate→user direct messages on the leader.
     *
     * <p>The leader observes every direct-message event on the team topic;
     * when the recipient is the {@code user} pseudo-member (no real polling
     * process), the leader flips {@code is_read} so the message does not
     * accumulate as unread and keep waking the dispatcher.
     *
     * @param event the event message to check
     * @return {@code true} if the message was successfully isMarked as read
     */
    public boolean ackUserBoundMessage(EventMessage event) {
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
        String toMemberName = str(payload, "to_member_name");
        String messageId = str(payload, "message_id");
        if (!TeamConstants.USER_PSEUDO_MEMBER_NAME.equals(toMemberName)
                || messageId == null || messageId.isBlank()) {
            return false;
        }
        Object mm = infra.messageManager();
        if (mm instanceof TeamMessageManager mgr) {
            boolean isMarked = mgr.markMessageRead(messageId, TeamConstants.USER_PSEUDO_MEMBER_NAME);
            Loggers.AGENT.debug("leader auto-acked user-bound message {} from {}",
                    messageId, str(payload, "from_member_name"));
            return isMarked;
        }
        return false;
    }

    /**
     * Forward a team-side message to the SDK's human-agent callbacks.
     *
     * <p>The leader observes every MESSAGE/BROADCAST event on the team topic.
     * For point-to-point messages addressed to a human agent we fire the
     * recipient's callback; for broadcasts we fire every registered callback
     * whose owner is not the broadcast sender (so a human agent doesn't get
     * its own broadcast echoed back). Missing message metadata is logged and
     * swallowed so a notification glitch never breaks the dispatch loop.
     *
     * @param event the event message to forward
     */
    public void notifyHumanAgentInbound(EventMessage event) {
        Object backend = infra.teamBackend();
        if (!(backend instanceof TeamBackend tb)) {
            return;
        }
        Object mm = infra.messageManager();
        if (!(mm instanceof TeamMessageManager mgr)) {
            return;
        }
        Map<String, Object> payload = event.getPayload() != null ? event.getPayload() : Map.of();
        String messageId = str(payload, "message_id");
        if (messageId == null) {
            return;
        }
        Optional<TeamMessage> row = findMessage(mgr, messageId);
        if (row.isEmpty()) {
            return;
        }
        boolean isBroadcast = TeamEvent.BROADCAST.equals(event.getEventType());
        String sender = str(payload, "from_member_name");
        List<String> recipients = resolveHumanAgentRecipients(tb, payload, isBroadcast, sender);
        if (recipients.isEmpty()) {
            return;
        }
        TeamMessage message = row.get();
        InboundMessageContext msgCtx = new InboundMessageContext(
                message, sender, isBroadcast, messageId);
        dispatchHumanAgentCallbacks(tb, recipients, msgCtx);
    }

    private List<String> resolveHumanAgentRecipients(
            TeamBackend tb, Map<String, Object> payload, boolean isBroadcast, String sender) {
        if (isBroadcast) {
            List<String> recipients = new ArrayList<>();
            for (String name : tb.humanAgentNames()) {
                if (!name.equals(sender)) {
                    recipients.add(name);
                }
            }
            return recipients;
        }
        String target = str(payload, "to_member_name");
        if (target == null || !tb.isHumanAgent(target)) {
            return List.of();
        }
        return List.of(target);
    }

    private void dispatchHumanAgentCallbacks(
            TeamBackend tb, List<String> recipients,
            InboundMessageContext msgCtx) {
        for (String recipient : recipients) {
            Optional<Function<HumanAgentInboundEvent, ?>> callbackOpt = tb.getHumanAgentInbound(recipient);
            if (callbackOpt.isEmpty()) {
                continue;
            }
            Function<HumanAgentInboundEvent, ?> callback = callbackOpt.get();
            long ts = msgCtx.message.getTimestamp();
            String body = msgCtx.message.getContent();
            HumanAgentInboundEvent evt = new HumanAgentInboundEvent(
                    recipient, msgCtx.sender, body, msgCtx.isBroadcast, msgCtx.messageId, ts);
            try {
                callback.apply(evt);
            } catch (IllegalStateException | NullPointerException
                    | IllegalArgumentException | UnsupportedOperationException e) {
                Loggers.AGENT.warn("human_agent on_inbound callback for {} raised: {}",
                        recipient, e.getMessage(), e);
            }
        }
    }

    private record InboundMessageContext(
            TeamMessage message, String sender,
            boolean isBroadcast, String messageId) {
    }

    private boolean isHumanAgent(String memberName) {
        Object backend = infra.teamBackend();
        return backend instanceof TeamBackend tb && tb.isHumanAgent(memberName);
    }

    private List<TeamMessage> readAllUnread(TeamMessageManager mgr, String memberName) {
        List<TeamMessage> unread = new ArrayList<>();
        unread.addAll(mgr.getMessages(memberName, true));
        unread.addAll(mgr.getBroadcastMessages(true));
        unread.sort((a, b) -> Long.compare(b.getTimestamp(), a.getTimestamp()));
        return unread;
    }

    private Optional<TeamMessage> findMessage(TeamMessageManager mgr, String messageId) {
        if (messageId == null) {
            return Optional.empty();
        }
        return mgr.listAllMessages().stream()
                .filter(message -> messageId.equals(message.getMessageId()))
                .findFirst();
    }
}
