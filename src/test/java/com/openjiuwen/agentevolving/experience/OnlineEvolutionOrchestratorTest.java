/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.experience;

import com.openjiuwen.agentevolving.ApplyResult;
import com.openjiuwen.agentevolving.Protocols;
import com.openjiuwen.agentevolving.UpdateValue;
import com.openjiuwen.agentevolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agentevolving.checkpointing.EvolutionStore;
import com.openjiuwen.agentevolving.signal.EvolutionSignal;
import com.openjiuwen.agentevolving.trajectory.UpdateKey;
import com.openjiuwen.agentevolving.updater.Updater;
import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.operator.skill_call.SkillExperienceOperator;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for online evolution orchestration result semantics.
 *
 * <p>Mirrors Python's {@code OnlineEvolutionOrchestrator} in
 * {@code openjiuwen/agent_evolving/experience/online_orchestrator.py}.</p>
 *
 * <p>Test cases also follow Python's
 * {@code tests/unit_tests/agent_evolving/experience/test_online_orchestrator.py}.</p>
 */
class OnlineEvolutionOrchestratorTest {

    @Test
    void evolveReturnsNoRecordsResultWhenPreviewHasNoRecords() {
        Fixture fixture = fixture(true);

        OnlineEvolutionResult result = fixture.orchestrator.evolve(
                "skill-a",
                List.of(makeSignal("skill-a")),
                true
        ).toCompletableFuture().join();

        assertEquals("no_evolution_no_records", result.getStatus());
        assertEquals("skill-a", result.getSkillName());
        assertNull(result.getRequest());
        verify(fixture.store).readSkillContent("skill-a", true);
        verify(fixture.manager, never()).stageApplyResults(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void evolveReturnsGenerationFailedWhenOptimizerRaisesBaseError() {
        Fixture fixture = fixture(true);
        OnlineEvolutionOrchestrator orchestrator = new FailingPreviewOrchestrator(
                fixture.store,
                fixture.updater,
                fixture.manager
        );

        OnlineEvolutionResult result = orchestrator.evolve(
                "skill-a",
                List.of(makeSignal("skill-a")),
                true
        ).toCompletableFuture().join();

        assertEquals("generation_failed", result.getStatus());
        assertNull(result.getRequest());
        verify(fixture.manager, never()).stageApplyResults(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void evolveReturnsStagedResultWhenRecordsAreGenerated() {
        Fixture fixture = fixture(true);
        ExperienceApprovalRequest request = new ExperienceApprovalRequest();
        request.setRequestId("req-1");
        when(fixture.manager.stageApplyResults(
                eq("skill-a"), any(), eq(true), eq("experience_updater"), any(), any(), any(), any(), any()
        )).thenReturn(request);
        OnlineEvolutionOrchestrator orchestrator = new PreviewOrchestrator(
                fixture.store,
                fixture.updater,
                fixture.manager,
                previewWithRecord()
        );

        OnlineEvolutionResult result = orchestrator.evolve(
                "skill-a",
                List.of(makeSignal("skill-a")),
                true
        ).toCompletableFuture().join();

        assertEquals("staged", result.getStatus());
        assertSame(request, result.getRequest());
        verify(fixture.manager, never()).approveRequest(any());
    }

    @Test
    void evolveReturnsAutoApprovedResultWhenApprovalIsNotRequired() {
        Fixture fixture = fixture(true);
        ExperienceApprovalRequest request = new ExperienceApprovalRequest();
        request.setRequestId("req-1");
        when(fixture.manager.stageApplyResults(
                eq("skill-a"), any(), eq(false), eq("experience_updater"), any(), any(), any(), any(), any()
        )).thenReturn(request);
        when(fixture.manager.approveRequest("req-1")).thenReturn(CompletableFuture.completedFuture(
                new ExperienceApplyResult("skill-a", 1, 0, 0, List.of(), Map.of())
        ));
        OnlineEvolutionOrchestrator orchestrator = new PreviewOrchestrator(
                fixture.store,
                fixture.updater,
                fixture.manager,
                previewWithRecord()
        );

        OnlineEvolutionResult result = orchestrator.evolve(
                "skill-a",
                List.of(makeSignal("skill-a")),
                false
        ).toCompletableFuture().join();

        assertEquals("auto_approved", result.getStatus());
        assertSame(request, result.getRequest());
        verify(fixture.manager).approveRequest("req-1");
    }

    @Test
    void evolveReturnsPersistenceFailedWhenAutoApprovalApplyFails() {
        Fixture fixture = fixture(true);
        ExperienceApprovalRequest request = new ExperienceApprovalRequest();
        request.setRequestId("req-1");
        when(fixture.manager.stageApplyResults(
                eq("skill-a"), any(), eq(false), eq("experience_updater"), any(), any(), any(), any(), any()
        )).thenReturn(request);
        when(fixture.manager.approveRequest("req-1")).thenReturn(CompletableFuture.completedFuture(
                new ExperienceApplyResult("skill-a", 0, 0, 0, List.of("disk full"), Map.of())
        ));
        OnlineEvolutionOrchestrator orchestrator = new PreviewOrchestrator(
                fixture.store,
                fixture.updater,
                fixture.manager,
                previewWithRecord()
        );

        OnlineEvolutionResult result = orchestrator.evolve(
                "skill-a",
                List.of(makeSignal("skill-a")),
                false
        ).toCompletableFuture().join();

        assertEquals("persistence_failed", result.getStatus());
        assertSame(request, result.getRequest());
        assertEquals("disk full", result.getMessage());
    }

    @Test
    void evolveReturnsSkippedNoInputWithoutSkillOrSignals() {
        Fixture fixture = fixture(true);

        OnlineEvolutionResult result = fixture.orchestrator.evolve("", List.of(), true)
                .toCompletableFuture()
                .join();

        assertEquals("skipped_no_input", result.getStatus());
        assertNull(result.getRequest());
        verify(fixture.updater, never()).process(any(), any(), any());
    }

    @Test
    void evolveReturnsSkippedSkillNotFound() {
        Fixture fixture = fixture(false);

        OnlineEvolutionResult result = fixture.orchestrator.evolve(
                "missing-skill",
                List.of(makeSignal("missing-skill")),
                true
        ).toCompletableFuture().join();

        assertEquals("skipped_skill_not_found", result.getStatus());
        assertEquals("missing-skill", result.getSkillName());
        assertNull(result.getRequest());
        verify(fixture.store).skillExists("missing-skill");
        verify(fixture.updater, never()).process(any(), any(), any());
    }

    @Test
    void evolveReturnsSkippedSkillDefinitionNotFound() {
        Fixture fixture = fixture(true);
        when(fixture.store.skillDefinitionExists("skill-a")).thenReturn(false);

        OnlineEvolutionResult result = fixture.orchestrator.evolve(
                "skill-a",
                List.of(makeSignal("skill-a")),
                true
        ).toCompletableFuture().join();

        assertEquals("skipped_skill_definition_not_found", result.getStatus());
        assertEquals("skill-a", result.getSkillName());
        assertNull(result.getRequest());
        verify(fixture.store).skillExists("skill-a");
        verify(fixture.store).skillDefinitionExists("skill-a");
        verify(fixture.store, never()).readSkillContent(any(), anyBoolean());
        verify(fixture.updater, never()).process(any(), any(), any());
        verify(fixture.manager, never()).stageApplyResults(
                any(), any(), anyBoolean(), any(), any(), any(), any(), any(), any()
        );
    }

    private static Fixture fixture(boolean skillExists) {
        EvolutionStore store = mock(EvolutionStore.class);
        Updater updater = mock(Updater.class);
        OnlineEvolutionOrchestrator.ExperienceManagerPort manager =
                mock(OnlineEvolutionOrchestrator.ExperienceManagerPort.class);

        when(store.skillExists(any())).thenReturn(skillExists);
        when(store.skillDefinitionExists(any())).thenReturn(skillExists);
        when(store.readSkillContent(any(), anyBoolean())).thenReturn(CompletableFuture.completedFuture("# skill"));
        when(store.getPendingRecords(any(), any())).thenReturn(CompletableFuture.completedFuture(List.<EvolutionRecord>of()));
        when(updater.bind(any(), any(), any())).thenReturn(1);
        when(updater.process(any(), any(), any())).thenReturn(CompletableFuture.completedFuture(Map.of()));
        when(manager.buildLocalApplyPreview(any(), any())).thenReturn(new LocalApplyPreview(
                "skill-a",
                List.of(),
                List.of()
        ));
        when(manager.approveRequest(any())).thenReturn(CompletableFuture.completedFuture(
                new ExperienceApplyResult("skill-a", 1, 0, 0, List.of(), Map.of())
        ));

        OnlineEvolutionOrchestrator orchestrator = new OnlineEvolutionOrchestrator(
                store,
                updater,
                manager,
                new LinkedHashMap<>()
        );
        return new Fixture(store, updater, manager, orchestrator);
    }

    private static EvolutionSignal makeSignal(String skillName) {
        return EvolutionSignal.builder()
                .signalType("tool_failure")
                .section("Troubleshooting")
                .excerpt("command failed")
                .skillName(skillName)
                .context(Map.of("source", "passive_conversation"))
                .build();
    }

    private static LocalApplyPreview previewWithRecord() {
        ApplyResult result = ApplyResult.builder()
                .operatorId("skill_experience_skill-a")
                .target(Protocols.EXPERIENCES_TARGET)
                .applied(true)
                .mode(Protocols.APPEND_MODE)
                .effect(Protocols.PENDING_CHANGE_EFFECT)
                .value(List.of("record"))
                .records(List.of("record"))
                .build();
        return new LocalApplyPreview("skill-a", List.of(mock(EvolutionRecord.class)), List.of(result));
    }

    private record Fixture(
            EvolutionStore store,
            Updater updater,
            OnlineEvolutionOrchestrator.ExperienceManagerPort manager,
            OnlineEvolutionOrchestrator orchestrator
    ) {
    }

    private static final class PreviewOrchestrator extends OnlineEvolutionOrchestrator {
        private final LocalApplyPreview preview;

        private PreviewOrchestrator(
                EvolutionStore store,
                Updater updater,
                ExperienceManagerPort manager,
                LocalApplyPreview preview
        ) {
            super(store, updater, manager, Map.<String, SkillExperienceOperator>of());
            this.preview = preview;
        }

        @Override
        protected CompletionStage<LocalApplyPreview> generateLocalApplyPreview(EvolutionContext onlineContext) {
            return CompletableFuture.completedFuture(preview);
        }
    }

    private static final class FailingPreviewOrchestrator extends OnlineEvolutionOrchestrator {

        private FailingPreviewOrchestrator(
                EvolutionStore store,
                Updater updater,
                ExperienceManagerPort manager
        ) {
            super(store, updater, manager, Map.<String, SkillExperienceOperator>of());
        }

        @Override
        protected CompletionStage<LocalApplyPreview> generateLocalApplyPreview(EvolutionContext onlineContext) {
            return CompletableFuture.failedFuture(new BaseError(
                    StatusCode.COMPONENT_LLM_INVOKE_CALL_FAILED,
                    "network failed",
                    null,
                    null
            ));
        }
    }
}
