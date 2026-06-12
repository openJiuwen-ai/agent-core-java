/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.agent.coordination.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openjiuwen.agent_teams.agent.AgentConfigurator.AgentCard;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.ConfiguredTeamBackend;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamAgentSpec;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamInfra;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRole;
import com.openjiuwen.agent_teams.agent.AgentConfigurator.TeamRuntimeContext;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.schema.BroadcastEvent;
import com.openjiuwen.agent_teams.schema.MemberShutdownEvent;
import com.openjiuwen.agent_teams.schema.MessageEvent;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import com.openjiuwen.agent_teams.tools.TeamMessage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link MessageHandler}.
 *
 * <p>Mirrors Python's message handler behavior in
 * {@code openjiuwen/agent_teams/agent/coordination/handlers/message.py}.</p>
 */
class MessageHandlerTest {

    @Test
    void callbackMapPreservesPythonMessageOrder() {
        MessageHandler handler = newHandler(new RecordingHost(), TeamRole.TEAMMATE, "dev", new TeamInfra(), new RecordingPoll());

        assertEquals(
                List.of(
                        TeamEvent.MESSAGE,
                        TeamEvent.BROADCAST,
                        "coordination_poll_mailbox",
                        TeamEvent.MEMBER_SHUTDOWN
                ),
                handler.getCallbacks().keySet().stream().toList()
        );
    }

    @Test
    void messageEventResumesPollsProcessesUnreadAndMarksRead() {
        RecordingHost host = new RecordingHost();
        RecordingPoll poll = new RecordingPoll();
        RecordingMessageManager messageManager = new RecordingMessageManager();
        messageManager.direct = List.of(message("m2", 200L, false), message("m1", 100L, false));
        messageManager.broadcasts = List.of(message("b1", 300L, true));
        TeamInfra infra = new TeamInfra();
        infra.setMessageManager(messageManager);
        MessageHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", infra, poll);

        handler.onMessageOrBroadcast(transport(EventMessage.fromEvent(messageEvent("sender", "dev", "event-1"))))
                .toCompletableFuture()
                .join();

        assertEquals(1, poll.resumeCount);
        assertEquals(List.of("b1", "m2", "m1"), host.deliveredMessageIds());
        assertEquals(List.of("b1:dev", "m2:dev", "m1:dev"), messageManager.marked);
    }

    @Test
    void pendingInterruptDefersMailboxWithoutMarkingRead() {
        RecordingHost host = new RecordingHost();
        host.pendingInterrupt = true;
        RecordingMessageManager messageManager = new RecordingMessageManager();
        messageManager.direct = List.of(message("m1", 100L, false));
        TeamInfra infra = new TeamInfra();
        infra.setMessageManager(messageManager);
        MessageHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", infra, new RecordingPoll());

        handler.onPollMailbox(new InnerEventMessage(InnerEventType.POLL_MAILBOX)).toCompletableFuture().join();

        assertTrue(host.delivered.isEmpty());
        assertTrue(messageManager.marked.isEmpty());
    }

    @Test
    void teammateShutdownDrainOnlyRunsForOwnShutdownEvent() {
        RecordingHost host = new RecordingHost();
        RecordingMessageManager messageManager = new RecordingMessageManager();
        messageManager.direct = List.of(message("m1", 100L, false));
        TeamInfra infra = new TeamInfra();
        infra.setMessageManager(messageManager);
        MessageHandler handler = newHandler(host, TeamRole.TEAMMATE, "dev", infra, new RecordingPoll());

        handler.onMemberShutdownDrain(transport(EventMessage.fromEvent(memberShutdown("other"))))
                .toCompletableFuture()
                .join();
        handler.onMemberShutdownDrain(transport(EventMessage.fromEvent(memberShutdown("dev"))))
                .toCompletableFuture()
                .join();

        assertEquals(1, host.delivered.size());
        assertTrue(host.useSteerValues.getFirst());
    }

    @Test
    void leaderAutoAcksUserBoundDirectMessageAndNotifiesHumanRecipient() {
        RecordingMessageManager messageManager = new RecordingMessageManager();
        TeamMessage row = message("event-1", 123L, false);
        row.setContent("hello human");
        messageManager.byId.put("event-1", row);
        RecordingBackend backend = new RecordingBackend();
        backend.humanAgents = List.of("user");
        TeamInfra infra = new TeamInfra();
        infra.setMessageManager(messageManager);
        infra.setTeamBackend(backend);
        MessageHandler handler = newHandler(new RecordingHost(), TeamRole.LEADER, "leader", infra, new RecordingPoll());

        handler.onMessageOrBroadcast(transport(EventMessage.fromEvent(messageEvent("dev", "user", "event-1"))))
                .toCompletableFuture()
                .join();

        assertEquals(List.of("event-1:user"), messageManager.marked);
        assertEquals(1, backend.inboundEvents.size());
        assertEquals("user", backend.inboundEvents.getFirst().memberName());
        assertEquals("dev", backend.inboundEvents.getFirst().sender());
        assertFalse(backend.inboundEvents.getFirst().broadcast());
    }

    @Test
    void broadcastNotifiesEveryHumanExceptSender() {
        RecordingMessageManager messageManager = new RecordingMessageManager();
        messageManager.byId.put("b1", message("b1", 123L, true));
        RecordingBackend backend = new RecordingBackend();
        backend.humanAgents = List.of("human-a", "human-b", "sender");
        TeamInfra infra = new TeamInfra();
        infra.setMessageManager(messageManager);
        infra.setTeamBackend(backend);
        MessageHandler handler = newHandler(new RecordingHost(), TeamRole.LEADER, "leader", infra, new RecordingPoll());

        handler.notifyHumanAgentInbound(transport(EventMessage.fromEvent(broadcastEvent("sender", "b1"))))
                .toCompletableFuture()
                .join();

        assertEquals(List.of("human-a", "human-b"), backend.inboundEvents.stream().map(MessageHandler.HumanAgentInboundEvent::memberName).toList());
    }

    private static MessageHandler newHandler(
            RecordingHost host,
            TeamRole role,
            String memberName,
            TeamInfra infra,
            RecordingPoll poll
    ) {
        return new MessageHandler(host, blueprint(role, memberName), infra, poll);
    }

    private static TeamAgentBlueprint blueprint(TeamRole role, String memberName) {
        TeamRuntimeContext ctx = new TeamRuntimeContext();
        ctx.setRole(role);
        ctx.setMemberName(memberName);
        return new TeamAgentBlueprint(
                new AgentCard("agent", "Agent", "description"),
                new TeamAgentSpec(),
                ctx,
                "",
                "cn"
        );
    }

    private static TransportEvent transport(EventMessage message) {
        return new TransportEvent(message);
    }

    private static TeamMessage message(String id, long timestamp, boolean broadcast) {
        return new TeamMessage(id, "team", "sender", "dev", "content-" + id, timestamp, broadcast, false);
    }

    private static MessageEvent messageEvent(String from, String to, String messageId) {
        MessageEvent event = new MessageEvent();
        event.setFromMemberName(from);
        event.setToMemberName(to);
        event.setMessageId(messageId);
        return event;
    }

    private static BroadcastEvent broadcastEvent(String from, String messageId) {
        BroadcastEvent event = new BroadcastEvent();
        event.setFromMemberName(from);
        event.setMessageId(messageId);
        return event;
    }

    private static MemberShutdownEvent memberShutdown(String memberName) {
        MemberShutdownEvent event = new MemberShutdownEvent();
        event.setMemberName(memberName);
        return event;
    }

    private static final class RecordingHost implements DispatcherHost {
        private final List<String> delivered = new ArrayList<>();
        private final List<Boolean> useSteerValues = new ArrayList<>();
        private boolean pendingInterrupt;

        private List<String> deliveredMessageIds() {
            return delivered.stream()
                    .map(text -> {
                        int start = text.indexOf("message_id=");
                        int comma = text.indexOf(",", start);
                        return text.substring(start + "message_id=".length(), comma);
                    })
                    .toList();
        }

        @Override
        public boolean isAgentReady() {
            return true;
        }

        @Override
        public boolean isAgentRunning() {
            return false;
        }

        @Override
        public boolean hasInFlightRound() {
            return false;
        }

        @Override
        public boolean hasPendingInterrupt() {
            return pendingInterrupt;
        }

        @Override
        public CompletionStage<Void> cancelAgent() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> deliverInput(Object content, boolean useSteer) {
            delivered.add(String.valueOf(content));
            useSteerValues.add(useSteer);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumeInterrupt(Object userInput) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> shutdownSelf() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> concludeCompletedRound(int memberCount, int taskCount) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingPoll implements PollController {
        private int resumeCount;

        @Override
        public CompletionStage<Void> pausePolls() {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Void> resumePolls() {
            resumeCount++;
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RecordingMessageManager implements MessageHandler.MessageManager {
        private List<TeamMessage> direct = List.of();
        private List<TeamMessage> broadcasts = List.of();
        private final List<String> marked = new ArrayList<>();
        private final Map<String, TeamMessage> byId = new LinkedHashMap<>();

        @Override
        public CompletionStage<List<TeamMessage>> getMessages(String memberName, boolean unreadOnly) {
            return CompletableFuture.completedFuture(direct);
        }

        @Override
        public CompletionStage<List<TeamMessage>> getBroadcastMessages(String memberName, boolean unreadOnly) {
            return CompletableFuture.completedFuture(broadcasts);
        }

        @Override
        public CompletionStage<Void> markMessageRead(String messageId, String memberName) {
            marked.add(messageId + ":" + memberName);
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<Optional<TeamMessage>> getMessage(String messageId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(byId.get(messageId)));
        }
    }

    private static final class RecordingBackend extends ConfiguredTeamBackend implements MessageHandler.TeamBackendView {
        private List<String> humanAgents = List.of();
        private final List<MessageHandler.HumanAgentInboundEvent> inboundEvents = new ArrayList<>();

        private RecordingBackend() {
            super("team", "leader", true, Map.of(), null, "", List.of(), null, null, true, false, List.of(), null, null, "leader");
        }

        @Override
        public CompletionStage<Boolean> isHumanAgent(String memberName) {
            return CompletableFuture.completedFuture(humanAgents.contains(memberName));
        }

        @Override
        public CompletionStage<List<String>> humanAgentNames() {
            return CompletableFuture.completedFuture(humanAgents);
        }

        @Override
        public MessageHandler.HumanAgentInboundCallback getHumanAgentInbound(String memberName) {
            if (!humanAgents.contains(memberName)) {
                return null;
            }
            return event -> {
                inboundEvents.add(event);
                return CompletableFuture.completedFuture(null);
            };
        }
    }
}
