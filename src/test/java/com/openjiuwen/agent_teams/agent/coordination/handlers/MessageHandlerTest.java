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
import com.openjiuwen.agent_teams.agent.BridgeInboundCompose;
import com.openjiuwen.agent_teams.agent.BridgeOutboundWrap;
import com.openjiuwen.agent_teams.agent.BridgeOutboundWrap.BridgeMailboxInjectMode;
import com.openjiuwen.agent_teams.agent.TeamAgentBlueprint;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.DispatcherHost;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventMessage;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.InnerEventType;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.PollController;
import com.openjiuwen.agent_teams.agent.coordination.EventDispatcher.TransportEvent;
import com.openjiuwen.agent_teams.interaction.BridgeProtocol;
import com.openjiuwen.agent_teams.interaction.BridgeProtocolAdapter;
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
 *
 * <p>Also mirrors Python's bridge inbound deliver tests in
 * {@code tests/unit_tests/agent_teams/agent/test_bridge_inbound_deliver.py}.</p>
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
        assertTrue(host.useSteerValues.get(0));
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
        assertEquals("user", backend.inboundEvents.get(0).memberName());
        assertEquals("dev", backend.inboundEvents.get(0).sender());
        assertFalse(backend.inboundEvents.get(0).broadcast());
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

    @Test
    void bridgeDeliverableWithAdapterCarriesRemoteReply() {
        RecordingBridgeBackend backend = new RecordingBridgeBackend();
        CapturingBridgeAdapter adapter = new CapturingBridgeAdapter("diff looks clean. lgtm.");
        backend.adapter = adapter;
        MessageHandler handler = bridgeHandler(backend);
        TeamMessage message = bridgeMessage("leader", "review pr 42", false);

        String text = handler.bridgeDeliverableFor("codex", message).toCompletableFuture().join();

        assertEquals("codex", adapter.lastMember);
        assertTrue(adapter.lastText.contains("review pr 42"));
        assertTrue(adapter.lastText.contains("["));
        assertTrue(text.contains("review pr 42"));
        assertTrue(text.contains("diff looks clean. lgtm."));
        assertTrue(text.toLowerCase().contains("verbatim"));
    }

    @Test
    void bridgeDeliverableWithoutAdapterUsesSentinel() {
        RecordingBridgeBackend backend = new RecordingBridgeBackend();
        MessageHandler handler = bridgeHandler(backend);

        String text = handler.bridgeDeliverableFor("codex", bridgeMessage("leader", "status?", false))
                .toCompletableFuture()
                .join();

        assertTrue(text.contains("status?"));
        assertTrue(text.contains(BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL));
    }

    @Test
    void bridgeDeliverableSwallowsAdapterException() {
        RecordingBridgeBackend backend = new RecordingBridgeBackend();
        backend.adapter = new RaisingBridgeAdapter();
        MessageHandler handler = bridgeHandler(backend);

        String text = handler.bridgeDeliverableFor("codex", bridgeMessage("leader", "hi", false))
                .toCompletableFuture()
                .join();

        assertTrue(text.contains("hi"));
        assertTrue(text.contains(BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL));
    }

    @Test
    void bridgeDeliverableRephraseModeIncludesSenderContext() {
        RecordingBridgeBackend backend = new RecordingBridgeBackend();
        backend.mode = BridgeMailboxInjectMode.REPHRASE;
        CapturingBridgeAdapter adapter = new CapturingBridgeAdapter("ok");
        backend.adapter = adapter;
        MessageHandler handler = bridgeHandler(backend);

        handler.bridgeDeliverableFor("codex", bridgeMessage("team_leader", "please review pr 42", false))
                .toCompletableFuture()
                .join();

        assertTrue(adapter.lastText.toLowerCase().contains("leader"));
        assertFalse(adapter.lastText.contains("human_agent"));
        assertTrue(adapter.lastText.contains("leader persona") || adapter.lastText.contains("L"));
    }

    private static MessageHandler bridgeHandler(RecordingBridgeBackend backend) {
        TeamInfra infra = new TeamInfra();
        infra.setTeamBackend(backend);
        return newHandler(new RecordingHost(), TeamRole.BRIDGE_AGENT, "codex", infra, new RecordingPoll());
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

    private static TeamMessage bridgeMessage(String sender, String content, boolean broadcast) {
        return new TeamMessage("m1", "bt", sender, "codex", content, 123456789L, broadcast, false);
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

    private static class RecordingBridgeBackend extends ConfiguredTeamBackend implements MessageHandler.BridgeDeliveryBackend {
        private BridgeProtocolAdapter adapter;
        private BridgeMailboxInjectMode mode = BridgeMailboxInjectMode.PASSTHROUGH;

        private RecordingBridgeBackend() {
            super("bt", "team_leader", true, Map.of(), null, "", List.of(), null, null, false, true, List.of(), null, null, "team_leader");
        }

        @Override
        public CompletionStage<String> bridgeDeliverableFor(String memberName, TeamMessage message) {
            String outbound = BridgeOutboundWrap.wrapOutboundToRemote(
                    message.getFromMemberName(),
                    "team_leader".equals(message.getFromMemberName()) ? "L" : message.getFromMemberName(),
                    "team_leader".equals(message.getFromMemberName()) ? TeamRole.LEADER : TeamRole.TEAMMATE,
                    "team_leader".equals(message.getFromMemberName()) ? "leader persona" : null,
                    message.getContent(),
                    Boolean.TRUE.equals(message.getBroadcast()),
                    null,
                    mode,
                    "en"
            );
            CompletionStage<String> replyStage = adapter == null
                    ? CompletableFuture.completedFuture(BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL)
                    : adapter.relay(memberName, outbound).exceptionally(ignored -> BridgeProtocol.REMOTE_UNAVAILABLE_SENTINEL);
            return replyStage.thenApply(reply -> BridgeInboundCompose.composeBridgeInbound(
                    message.getFromMemberName(),
                    message.getContent(),
                    reply,
                    "en",
                    "now"
            ));
        }
    }

    private static class CapturingBridgeAdapter implements BridgeProtocolAdapter {
        private final String reply;
        private String lastMember;
        private String lastText;

        private CapturingBridgeAdapter(String reply) {
            this.reply = reply;
        }

        @Override
        public CompletionStage<Void> connect(
                String memberName,
                Map<String, Object> adapterConfig,
                String bridgePersona,
                String teamOverview
        ) {
            return CompletableFuture.completedFuture(null);
        }

        @Override
        public CompletionStage<String> relay(String memberName, String text) {
            lastMember = memberName;
            lastText = text;
            return CompletableFuture.completedFuture(reply);
        }

        @Override
        public CompletionStage<Void> close() {
            return CompletableFuture.completedFuture(null);
        }
    }

    private static final class RaisingBridgeAdapter extends CapturingBridgeAdapter {
        private RaisingBridgeAdapter() {
            super("");
        }

        @Override
        public CompletionStage<String> relay(String memberName, String text) {
            CompletableFuture<String> failed = new CompletableFuture<>();
            failed.completeExceptionally(new RuntimeException("remote down"));
            return failed;
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
