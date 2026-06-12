/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.interaction.InteractionRouter.MemberExistsCheck;
import com.openjiuwen.agent_teams.interaction.InteractionRouter.Mention;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import org.junit.jupiter.api.Test;

/**
 * Pure parser tests for {@link InteractionRouter}.
 *
 * <p>Mirrors Python's {@code test_router.py} for
 * {@code openjiuwen/agent_teams/interaction/router.py}.</p>
 */
class InteractionRouterTest {

    @Test
    void godViewNoRecipientsEmitsGodView() {
        assertThat(InteractionRouter.parseInteractStr("# what is the plan?"))
                .containsExactly(new GodViewMessage("what is the plan?"));
    }

    @Test
    void noPrefixDefaultsToGodView() {
        assertThat(InteractionRouter.parseInteractStr("just a plain question"))
                .containsExactly(new GodViewMessage("just a plain question"));
    }

    @Test
    void godViewSingleRecipientEmitsOperatorDirect() {
        assertThat(InteractionRouter.parseInteractStr("# @dev-1 ship the patch"))
                .containsExactly(new OperatorMessage("ship the patch", "dev-1"));
    }

    @Test
    void defaultChannelWithMentionStillGodViewSender() {
        assertThat(InteractionRouter.parseInteractStr("@dev-1 ship the patch"))
                .containsExactly(new OperatorMessage("ship the patch", "dev-1"));
    }

    @Test
    void godViewMultiRecipientFansOut() {
        assertThat(InteractionRouter.parseInteractStr("# @m1 @m2 @m3 stand-up in 5"))
                .containsExactly(
                        new OperatorMessage("stand-up in 5", "m1"),
                        new OperatorMessage("stand-up in 5", "m2"),
                        new OperatorMessage("stand-up in 5", "m3")
                );
    }

    @Test
    void godViewAtAllCollapsesToBroadcast() {
        assertThat(InteractionRouter.parseInteractStr("# @all heads up"))
                .containsExactly(new OperatorMessage("heads up"));
    }

    @Test
    void godViewAtStarAlsoBroadcasts() {
        assertThat(InteractionRouter.parseInteractStr("# @* heads up"))
                .containsExactly(new OperatorMessage("heads up"));
    }

    @Test
    void broadcastTokenSupersedesOtherRecipients() {
        assertThat(InteractionRouter.parseInteractStr("# @m1 @all wide announce"))
                .containsExactly(new OperatorMessage("wide announce"));
    }

    @Test
    void humanAgentNoRecipientsDrivesAvatar() {
        assertThat(InteractionRouter.parseInteractStr("$alice please summarise design.md"))
                .containsExactly(new HumanAgentMessage("please summarise design.md", "alice"));
    }

    @Test
    void humanAgentSingleRecipientEmitsDirect() {
        assertThat(InteractionRouter.parseInteractStr("$alice @dev-1 ping me when done"))
                .containsExactly(new HumanAgentMessage("ping me when done", "alice", "dev-1"));
    }

    @Test
    void humanAgentMultiRecipientFansOut() {
        assertThat(InteractionRouter.parseInteractStr("$alice @m1 @m2 status sync"))
                .containsExactly(
                        new HumanAgentMessage("status sync", "alice", "m1"),
                        new HumanAgentMessage("status sync", "alice", "m2")
                );
    }

    @Test
    void humanAgentAtAllEmitsBroadcastMarker() {
        assertThat(InteractionRouter.parseInteractStr("$alice @all heads up"))
                .containsExactly(new HumanAgentMessage("heads up", "alice", "*"));
    }

    @Test
    void humanAgentNoSpaceBeforeAtSplitsSenderAndRecipient() {
        assertThat(InteractionRouter.parseInteractStr("$alice@dev-1 ping me"))
                .containsExactly(new HumanAgentMessage("ping me", "alice", "dev-1"));
    }

    @Test
    void humanAgentAtInSenderNameIsRejected() {
        assertThat(InteractionRouter.parseInteractStr("$player-6@player-3 汇报当前进展"))
                .containsExactly(new HumanAgentMessage("汇报当前进展", "player-6", "player-3"));
    }

    @Test
    void humanAgentNoSpaceMultiRecipient() {
        assertThat(InteractionRouter.parseInteractStr("$alice@m1 @m2 sync"))
                .containsExactly(
                        new HumanAgentMessage("sync", "alice", "m1"),
                        new HumanAgentMessage("sync", "alice", "m2")
                );
    }

    @Test
    void emptyInputReturnsEmptyList() {
        assertThat(InteractionRouter.parseInteractStr("")).isEmpty();
        assertThat(InteractionRouter.parseInteractStr("   \t\n  ")).isEmpty();
    }

    @Test
    void hashWithoutSpaceIsContent() {
        assertThat(InteractionRouter.parseInteractStr("#hashtag is just text"))
                .containsExactly(new GodViewMessage("#hashtag is just text"));
    }

    @Test
    void dollarWithoutBodyFallsBackToGodView() {
        assertThat(InteractionRouter.parseInteractStr("$alice"))
                .containsExactly(new GodViewMessage("$alice"));
    }

    @Test
    void atWithoutBodyKeepsTokenInBody() {
        assertThat(InteractionRouter.parseInteractStr("@dev-1"))
                .containsExactly(new GodViewMessage("@dev-1"));
    }

    @Test
    void inlineAtInBodyIsNotARecipient() {
        assertThat(InteractionRouter.parseInteractStr("# hello @world this is content"))
                .containsExactly(new GodViewMessage("hello @world this is content"));
    }

    @Test
    void godViewWithOnlyRecipientsHasEmptyBody() {
        assertThat(InteractionRouter.parseInteractStr("# @dev-1 "))
                .containsExactly(new OperatorMessage("", "dev-1"));
    }

    @Test
    void parseMentionPrimitiveStillWorks() {
        assertThat(InteractionRouter.parseMention("@dev-1 hi"))
                .contains(new Mention("dev-1", "hi"));
        assertThat(InteractionRouter.parseMention("hello world")).isEmpty();
    }

    @Test
    void isReservedNameMatchesRuntimeReservedMembers() {
        assertThat(InteractionRouter.isReservedName("user")).isTrue();
        assertThat(InteractionRouter.isReservedName("team_leader")).isTrue();
        assertThat(InteractionRouter.isReservedName("human_agent")).isTrue();
        assertThat(InteractionRouter.isReservedName("dev-1")).isFalse();
    }

    @Test
    void resolveTargetsKeepsKnownRecipients() {
        List<InteractPayload> payloads = InteractionRouter.parseInteractStr("# @m1 @m2 sync");

        List<InteractPayload> resolved = InteractionRouter.resolveTargets(payloads, existsIn("m1", "m2"))
                .toCompletableFuture()
                .join();

        assertThat(resolved).isSameAs(payloads);
    }

    @Test
    void resolveTargetsFoldsUnknownOperatorToGodView() {
        List<InteractPayload> payloads = InteractionRouter.parseInteractStr("# @ghost ship it");

        List<InteractPayload> resolved = InteractionRouter.resolveTargets(payloads, existsIn())
                .toCompletableFuture()
                .join();

        assertThat(resolved).containsExactly(new GodViewMessage("@ghost ship it"));
    }

    @Test
    void resolveTargetsFoldsUnknownHumanAgentToAvatar() {
        List<InteractPayload> payloads = InteractionRouter.parseInteractStr("$alice @ghost hi");

        List<InteractPayload> resolved = InteractionRouter.resolveTargets(payloads, existsIn())
                .toCompletableFuture()
                .join();

        assertThat(resolved).containsExactly(new HumanAgentMessage("@ghost hi", "alice"));
    }

    @Test
    void resolveTargetsPartialMatchKeepsKnownAndFoldsUnknown() {
        List<InteractPayload> payloads = InteractionRouter.parseInteractStr("# @m1 @ghost on it");

        List<InteractPayload> resolved = InteractionRouter.resolveTargets(payloads, existsIn("m1"))
                .toCompletableFuture()
                .join();

        assertThat(resolved).containsExactly(
                new OperatorMessage("on it", "m1"),
                new GodViewMessage("@ghost on it")
        );
    }

    @Test
    void resolveTargetsMultipleUnknownRejoinInOneMessage() {
        List<InteractPayload> payloads = InteractionRouter.parseInteractStr("# @g1 @g2 stand-up in 5");

        List<InteractPayload> resolved = InteractionRouter.resolveTargets(payloads, existsIn())
                .toCompletableFuture()
                .join();

        assertThat(resolved).containsExactly(new GodViewMessage("@g1 @g2 stand-up in 5"));
    }

    @Test
    void resolveTargetsPassesThroughBroadcastAndGodView() {
        MemberExistsCheck predicate = existsIn();

        List<InteractPayload> god = InteractionRouter.resolveTargets(
                InteractionRouter.parseInteractStr("# plain"),
                predicate
        ).toCompletableFuture().join();
        List<InteractPayload> broadcast = InteractionRouter.resolveTargets(
                InteractionRouter.parseInteractStr("# @all heads up"),
                predicate
        ).toCompletableFuture().join();

        assertThat(god).containsExactly(new GodViewMessage("plain"));
        assertThat(broadcast).containsExactly(new OperatorMessage("heads up"));
    }

    @Test
    void deliverDirectSendsKnownTarget() {
        FakeMessageManager messages = new FakeMessageManager("msg-1");

        DeliverResult result = InteractionRouter.deliverDirect(
                "hi",
                "alice",
                "dev-1",
                messages,
                existsIn("dev-1")
        ).toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.success("msg-1"));
        assertThat(messages.records).containsExactly(new MessageRecord("hi", "dev-1", "alice"));
    }

    @Test
    void deliverDirectReturnsUnknownMemberForMissingTarget() {
        FakeMessageManager messages = new FakeMessageManager("msg-1");

        DeliverResult result = InteractionRouter.deliverDirect(
                "hi",
                "alice",
                "ghost",
                messages,
                existsIn("dev-1")
        ).toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.failure("unknown_member:ghost"));
        assertThat(messages.records).isEmpty();
    }

    @Test
    void deliverDirectReturnsSendFailedForNullMessageId() {
        FakeMessageManager messages = new FakeMessageManager(null);

        DeliverResult result = InteractionRouter.deliverDirect(
                "hi",
                "alice",
                "dev-1",
                messages,
                existsIn("dev-1")
        ).toCompletableFuture().join();

        assertThat(result).isEqualTo(DeliverResult.failure("send_failed:dev-1"));
    }

    private static MemberExistsCheck existsIn(String... known) {
        Set<String> names = Set.of(known);
        return name -> CompletableFuture.completedFuture(names.contains(name));
    }

    private record MessageRecord(String content, String toMemberName, String fromMemberName) {
    }

    private static final class FakeMessageManager implements InteractionRouter.MessageManagerView {
        private final String nextMessageId;
        private final List<MessageRecord> records = new ArrayList<>();

        private FakeMessageManager(String nextMessageId) {
            this.nextMessageId = nextMessageId;
        }

        @Override
        public CompletionStage<String> sendMessage(String content, String toMemberName, String fromMemberName) {
            records.add(new MessageRecord(content, toMemberName, fromMemberName));
            return CompletableFuture.completedFuture(nextMessageId);
        }
    }
}
