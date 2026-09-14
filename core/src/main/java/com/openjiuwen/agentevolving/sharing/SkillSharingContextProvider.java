/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.sharing;

import java.util.concurrent.CompletionStage;

/**
 * Mirrors Python's {@code SkillSharingContextProvider} in
 * {@code openjiuwen/agent_evolving/sharing/experience_sharer.py}.
 */
@FunctionalInterface
public interface SkillSharingContextProvider {

    CompletionStage<SkillSharingContext> provide(String skillName);
}
