/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.rails;

import com.openjiuwen.harness.rails.memory.CodingMemoryRail;
import com.openjiuwen.harness.rails.memory.ExternalMemoryRail;
import com.openjiuwen.harness.rails.memory.MemoryRail;
import com.openjiuwen.harness.rails.interrupt.ConfirmInterruptRail;
import com.openjiuwen.harness.rails.security.BaseSecurityRail;
import com.openjiuwen.harness.rails.security.PermissionInterruptRail;
import com.openjiuwen.harness.rails.security.SafetyPromptRail;
import com.openjiuwen.harness.rails.security.SecurityRail;
import com.openjiuwen.harness.rails.skills.SkillCreateRail;
import com.openjiuwen.harness.rails.skills.SkillUseRail;
import com.openjiuwen.harness.rails.skills.TeamSkillCreateRail;
import com.openjiuwen.harness.rails.skills.TeamSkillRail;
import com.openjiuwen.harness.rails.subagent.SessionRail;
import com.openjiuwen.harness.rails.subagent.SubagentRail;
import com.openjiuwen.harness.rails.subagent.VerificationContractRail;
import com.openjiuwen.harness.rails.subagent.VerificationRail;

import java.util.List;

/**
 * Module facade for common harness rails.
 *
 * <p>Mirrors Python's {@code openjiuwen/harness/rails/__init__.py}.</p>
 */
public final class HarnessRailsPackage {

    private HarnessRailsPackage() {
    }

    public static List<Class<? extends DeepAgentRail>> exportedRails() {
        return List.of(
                AgentModeRail.class,
                HeartbeatRail.class,
                LspRail.class,
                McpRail.class,
                MemoryRail.class,
                CodingMemoryRail.class,
                ExternalMemoryRail.class,
                ConfirmInterruptRail.class,
                BaseSecurityRail.class,
                PermissionInterruptRail.class,
                ProgressiveToolRail.class,
                SafetyPromptRail.class,
                SecurityRail.class,
                SessionRail.class,
                SkillCreateRail.class,
                SkillUseRail.class,
                SubagentRail.class,
                SysOperationRail.class,
                TaskCompletionRail.class,
                TaskPlanningRail.class,
                TeamSkillCreateRail.class,
                TeamSkillRail.class,
                VerificationContractRail.class,
                VerificationRail.class
        );
    }
}
