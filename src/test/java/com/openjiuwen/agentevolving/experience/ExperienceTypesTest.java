/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.experience;

import com.openjiuwen.agent_evolving.ApplyResult;
import com.openjiuwen.agent_evolving.Protocols;
import com.openjiuwen.agent_evolving.checkpointing.EvolutionRecord;
import com.openjiuwen.agent_evolving.signal.EvolutionSignal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExperienceTypesTest {

    @Test
    void pendingChangeFactoriesPreserveLifecycleDefaults() {
        EvolutionRecord record = new EvolutionRecord();
        PendingChange pending = PendingChange.make("demo_skill", List.of(record), "trajectory", List.of(Map.of("role", "user")));
        PendingChange shared = PendingChange.makeForSharedRecords("demo_skill", List.of(record), null, null);

        assertThat(pending.getOperatorId()).isEqualTo("skill_experience_demo_skill");
        assertThat(pending.getChangeType()).isEqualTo(Protocols.SKILL_EXPERIENCE_ENTRY);
        assertThat(pending.getPayload()).hasSize(1);
        assertThat(pending.getChangeId()).startsWith("skill_evolve_");
        assertThat(shared.isSharedRecords()).isTrue();
    }

    @Test
    void approvalAndOutcomeHelpersMatchPythonFlow() {
        ExperienceProposal proposal = new ExperienceProposal("demo_skill", List.of(), true, null, null, null, null);
        PendingChange pending = PendingChange.make("demo_skill", List.of(new EvolutionRecord()), null, null);
        ExperienceApprovalRequest request = new ExperienceApprovalRequest(
                "demo_skill",
                proposal,
                pending,
                "req-1",
                List.of(new ApplyResult("operator", "target", true))
        );
        OnlineEvolutionResult staged = new OnlineEvolutionResult("demo_skill", OnlineEvolutionStatus.STAGED, request, "");
        OnlineEvolutionResult failed = new OnlineEvolutionResult("demo_skill", OnlineEvolutionStatus.GENERATION_FAILED, request, "");

        assertThat(request.toHostResult().getPendingCount()).isEqualTo(1);
        assertThat(OnlineEvolutionResult.requestForOnlineEvolutionResult(staged)).isSameAs(request);
        assertThat(OnlineEvolutionResult.requestForOnlineEvolutionResult(failed)).isNull();
    }

    @Test
    void applyResultMapsToHostFacingStatus() {
        ExperienceApplyResult rejected = new ExperienceApplyResult("demo_skill", 0, 2, 0, List.of(), Map.of());
        ExperienceApplyResult partial = new ExperienceApplyResult("demo_skill", 1, 0, 1, List.of("warn"), Map.of("k", "v"));

        assertThat(rejected.isOk()).isTrue();
        assertThat(rejected.toHostResult("req-2", Protocols.SKILL_EXPERIENCE_ENTRY).getStatus()).isEqualTo("rejected");
        assertThat(partial.isOk()).isFalse();
        assertThat(partial.toHostResult("req-3", Protocols.SKILL_EXPERIENCE_ENTRY).getStatus()).isEqualTo("partial");
    }

    @Test
    void onlineEvolutionContextAliasesEvolutionContext() {
        EvolutionContext context = new EvolutionContext(
                "demo_skill",
                List.of(EvolutionSignal.builder().signalType("user_intent").build()),
                "content",
                List.of(Map.of("role", "user")),
                List.of(),
                List.of(),
                "question",
                null,
                List.of(),
                Map.of("scope", "demo")
        );
        OnlineEvolutionContext alias = new OnlineEvolutionContext(context);

        assertThat(alias.getSkillName()).isEqualTo("demo_skill");
        assertThat(alias.getSignals()).hasSize(1);
        assertThat(alias.getMetadata()).containsEntry("scope", "demo");
    }
}
