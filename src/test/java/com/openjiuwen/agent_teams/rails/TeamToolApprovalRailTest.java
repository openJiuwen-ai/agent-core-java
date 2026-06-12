/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.rails;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.llm.schema.ToolCall;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

/**
 * Tests team tool approval rail interrupt decisions.
 *
 * <p>Mirrors Python's approval flow in
 * {@code openjiuwen/agent_teams/rails/tool_approval_rail.py}.</p>
 */
class TeamToolApprovalRailTest {

    @Test
    void firstCallAutoConfirmedApprovesWithoutSendingMessage() {
        RecordingMessageManager messageManager = new RecordingMessageManager("msg-1");
        TeamToolApprovalRail rail = rail(messageManager);

        TeamToolApprovalRail.InterruptDecision decision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-1", "delete_file", "{\"path\":\"a.txt\"}"),
                null,
                Map.of("delete_file", true)
        ).toCompletableFuture().join();

        assertThat(decision).isInstanceOf(TeamToolApprovalRail.ApproveResult.class);
        assertThat(messageManager.contents).isEmpty();
    }

    @Test
    void firstCallSendsLeaderMessageAndInterrupts() {
        RecordingMessageManager messageManager = new RecordingMessageManager("msg-1");
        TeamToolApprovalRail rail = rail(messageManager);

        TeamToolApprovalRail.InterruptDecision decision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-7", "execute_command", "{\"cmd\":\"pwd\"}"),
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(messageManager.toMembers).containsExactly("leader-1");
        assertThat(messageManager.contents).hasSize(1);
        assertThat(messageManager.contents.getFirst())
                .contains("Teammate tool approval request.")
                .contains("Member: member-1")
                .contains("Tool: execute_command")
                .contains("Tool Call ID: call-7")
                .contains("Arguments: {\"cmd\":\"pwd\"}")
                .contains("Please review and call approve_tool.");
        assertThat(decision).isInstanceOf(TeamToolApprovalRail.InterruptResult.class);

        TeamToolApprovalRail.InterruptResult interrupt = (TeamToolApprovalRail.InterruptResult) decision;
        assertThat(interrupt.request().getMessage()).isEqualTo("Awaiting leader approval for tool: execute_command");
        assertThat(interrupt.request().getAutoConfirmKey()).isEqualTo("execute_command");
        assertThat(interrupt.request().getPayloadSchema()).containsEntry("title", "ConfirmPayload");
    }

    @Test
    void firstCallUsesEmptyJsonArgumentsWhenPythonValueIsFalsy() {
        RecordingMessageManager messageManager = new RecordingMessageManager("msg-1");
        TeamToolApprovalRail rail = rail(messageManager);

        rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-8", "touch", ""),
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(messageManager.contents.getFirst()).contains("Arguments: {}");
    }

    @Test
    void failedLeaderMessageRejectsToolCall() {
        RecordingMessageManager messageManager = new RecordingMessageManager(null);
        TeamToolApprovalRail rail = rail(messageManager);

        TeamToolApprovalRail.InterruptDecision decision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-9", "delete_file", "{}"),
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(decision).isInstanceOf(TeamToolApprovalRail.RejectResult.class);
        TeamToolApprovalRail.RejectResult reject = (TeamToolApprovalRail.RejectResult) decision;
        assertThat(reject.toolResult()).isEqualTo("Failed to send approval request to leader");
    }

    @Test
    void missingToolCallRejectsAsInvalid() {
        TeamToolApprovalRail rail = rail(new RecordingMessageManager("msg-1"));

        TeamToolApprovalRail.InterruptDecision decision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                null,
                null,
                Map.of()
        ).toCompletableFuture().join();

        assertThat(decision).isInstanceOf(TeamToolApprovalRail.RejectResult.class);
        assertThat(((TeamToolApprovalRail.RejectResult) decision).toolResult()).isEqualTo("Invalid tool call");
    }

    @Test
    void resumeApprovesPayloadAndMapInputs() {
        TeamToolApprovalRail rail = rail(new RecordingMessageManager("msg-1"));

        TeamToolApprovalRail.InterruptDecision payloadDecision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-1", "execute_command", "{}"),
                TeamToolApprovalRail.ConfirmPayload.approvedPayload(),
                Map.of()
        ).toCompletableFuture().join();

        TeamToolApprovalRail.InterruptDecision mapDecision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-1", "execute_command", "{}"),
                Map.of("approved", "true", "feedback", "ok"),
                Map.of()
        ).toCompletableFuture().join();

        assertThat(payloadDecision).isInstanceOf(TeamToolApprovalRail.ApproveResult.class);
        assertThat(mapDecision).isInstanceOf(TeamToolApprovalRail.ApproveResult.class);
    }

    @Test
    void resumeRejectsWithFeedbackOrDefaultMessage() {
        TeamToolApprovalRail rail = rail(new RecordingMessageManager("msg-1"));

        TeamToolApprovalRail.InterruptDecision feedbackDecision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-2", "delete_file", "{}"),
                TeamToolApprovalRail.ConfirmPayload.rejectedPayload("too risky"),
                Map.of()
        ).toCompletableFuture().join();

        TeamToolApprovalRail.InterruptDecision defaultDecision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-2", "delete_file", "{}"),
                Map.of("approved", false),
                Map.of()
        ).toCompletableFuture().join();

        assertThat(((TeamToolApprovalRail.RejectResult) feedbackDecision).toolResult()).isEqualTo("too risky");
        assertThat(((TeamToolApprovalRail.RejectResult) defaultDecision).toolResult())
                .isEqualTo("Tool call rejected by leader");
    }

    @Test
    void invalidResumeInputReinterruptsWithConfirmSchema() {
        TeamToolApprovalRail rail = rail(new RecordingMessageManager("msg-1"));

        TeamToolApprovalRail.InterruptDecision badTypeDecision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-3", "delete_file", "{}"),
                List.of("approved"),
                Map.of()
        ).toCompletableFuture().join();

        TeamToolApprovalRail.InterruptDecision badMapDecision = rail.resolveInterrupt(
                new TeamToolApprovalRail.ApprovalCallbackContext(),
                toolCall("call-3", "delete_file", "{}"),
                Map.of("feedback", "missing approved"),
                Map.of()
        ).toCompletableFuture().join();

        assertThat(((TeamToolApprovalRail.InterruptResult) badTypeDecision).request().getMessage())
                .isEqualTo("Invalid approval response format for tool: delete_file");
        assertThat(((TeamToolApprovalRail.InterruptResult) badMapDecision).request().getMessage())
                .isEqualTo("Invalid approval response for tool: delete_file");
    }

    @Test
    void toolRegistrationMirrorsBaseInterruptRailHelpers() {
        TeamToolApprovalRail rail = rail(new RecordingMessageManager("msg-1"));

        rail.addTool("read_file");
        rail.addTools(List.of("delete_file", "execute_command"));
        rail.addPolicy("approve_tool");
        rail.addTool("");

        assertThat(rail.getTools())
                .containsExactlyInAnyOrder("read_file", "delete_file", "execute_command", "approve_tool");
    }

    private static TeamToolApprovalRail rail(RecordingMessageManager messageManager) {
        return new TeamToolApprovalRail(new TeamToolApprovalRail.Config(
                "team-a",
                "member-1",
                messageManager,
                "leader-1",
                Set.of()
        ));
    }

    private static ToolCall toolCall(String id, String name, String arguments) {
        return ToolCall.builder()
                .id(id)
                .name(name)
                .arguments(arguments)
                .build();
    }

    private static final class RecordingMessageManager implements TeamToolApprovalRail.ApprovalMessageManager {
        private final String messageId;
        private final List<String> contents = new ArrayList<>();
        private final List<String> toMembers = new ArrayList<>();

        private RecordingMessageManager(String messageId) {
            this.messageId = messageId;
        }

        @Override
        public CompletableFuture<String> sendMessage(String content, String toMemberName) {
            contents.add(content);
            toMembers.add(toMemberName);
            return CompletableFuture.completedFuture(messageId);
        }
    }
}
