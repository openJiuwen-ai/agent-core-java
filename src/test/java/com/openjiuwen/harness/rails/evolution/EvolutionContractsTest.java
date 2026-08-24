/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails.evolution;

import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.experience.PendingChange;
import com.openjiuwen.agentevolving.trajectory.Trajectory;
import com.openjiuwen.core.session.stream.OutputSchema;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's typed evolution rail contracts in
 * {@code openjiuwen/harness/rails/evolution/contracts.py}.
 */
class EvolutionContractsTest {

    @Test
    void hostEventMetaPayloadOmitsEmptyFieldsAndUsesSnakeCase() {
        EvolutionHostEventMeta meta = EvolutionHostEventMeta.builder(EvolutionEventKind.APPROVAL)
                .railKind("skill")
                .stage("approval")
                .requestId("req-1")
                .status("pending")
                .build();

        assertThat(meta.toPayload()).containsExactly(
                Map.entry("event_kind", "approval"),
                Map.entry("rail_kind", "skill"),
                Map.entry("stage", "approval"),
                Map.entry("request_id", "req-1"),
                Map.entry("status", "pending")
        );
        assertThat(EvolutionEventKind.fromValue("outcome")).isEqualTo(EvolutionEventKind.OUTCOME);
    }

    @Test
    void snapshotRoundTripsLegacyDictShape() {
        Trajectory trajectory = Trajectory.builder().executionId("exec-1").build();
        EvolutionSnapshot snapshot = new EvolutionSnapshot(
                trajectory,
                List.of(Map.of("role", "user", "content", "hi")),
                "skill-a"
        );

        Map<String, Object> legacy = snapshot.toLegacyDict();
        assertThat(legacy).containsKeys("trajectory", "messages", "skill_name");

        EvolutionSnapshot restored = EvolutionSnapshot.fromLegacyDict(legacy);
        assertThat(restored.getTrajectory()).isSameAs(trajectory);
        assertThat(restored.getMessages()).containsExactly(Map.of("role", "user", "content", "hi"));
        assertThat(restored.getSkillName()).isEqualTo("skill-a");
    }

    @Test
    void requestResultsReportChangesLikePythonProperties() {
        EvolutionRequestResult emptyEvolution = EvolutionRequestResult.builder("skill-a").build();
        EvolutionRequestResult withRecord = EvolutionRequestResult.builder("skill-a")
                .records(List.of(EvolutionRecord.builder().id("record-1").build()))
                .message(null)
                .build();
        SimplifyRequestResult emptySimplify = SimplifyRequestResult.builder("skill-a").build();
        SimplifyRequestResult withAction = SimplifyRequestResult.builder("skill-a")
                .actions(List.of(Map.of("kind", "replace")))
                .build();
        SimplifyRequestResult withApproval = SimplifyRequestResult.builder("skill-a")
                .approvalEvent(new OutputSchema("approval", 0, Map.of("request_id", "req-1")))
                .build();

        assertThat(emptyEvolution.hasChanges()).isFalse();
        assertThat(withRecord.hasChanges()).isTrue();
        assertThat(withRecord.getMessage()).isEmpty();
        assertThat(emptySimplify.hasChanges()).isFalse();
        assertThat(withAction.hasChanges()).isTrue();
        assertThat(withApproval.hasChanges()).isTrue();
    }

    @Test
    void approvalProtocolAndPendingStoreKeepTypedBoundaries() {
        RecordingApprovalManager manager = new RecordingApprovalManager();
        PendingChange pending = PendingChange.make("skill-a", List.of(), null, List.of());
        PendingApprovalSnapshotStore store = new PendingApprovalSnapshotStore();

        store.put("req-1", pending);

        assertThat(manager.approveRequest("req-1", List.of("record-1")).toCompletableFuture().join())
                .isEqualTo("approved:req-1:1");
        assertThat(manager.rejectRequest("req-2").toCompletableFuture().join()).isEqualTo("rejected:req-2");
        assertThat(store).containsEntry("req-1", pending);
    }

    private static final class RecordingApprovalManager implements ApprovalManagerProtocol {

        @Override
        public CompletionStage<Object> approveRequest(String requestId, List<String> approvedRecordIds) {
            int count = approvedRecordIds == null ? 0 : approvedRecordIds.size();
            return CompletableFuture.completedFuture("approved:" + requestId + ":" + count);
        }

        @Override
        public CompletionStage<Object> rejectRequest(String requestId) {
            return CompletableFuture.completedFuture("rejected:" + requestId);
        }
    }
}
