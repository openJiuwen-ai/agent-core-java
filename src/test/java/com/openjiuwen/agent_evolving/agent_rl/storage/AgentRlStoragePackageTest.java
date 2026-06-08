/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentRlStoragePackageTest {

    @Test
    void exportsMatchPythonPackageSurface() {
        assertEquals("openjiuwen/agent_evolving/agent_rl/storage/__init__.py", AgentRlStoragePackage.PYTHON_MODULE);
        assertEquals(InMemoryTrajectoryStore.class, AgentRlStoragePackage.IN_MEMORY_TRAJECTORY_STORE);
        assertEquals(LoRARepository.class, AgentRlStoragePackage.LORA_REPOSITORY);
        assertEquals(LoRARepository.LoRAVersion.class, AgentRlStoragePackage.LORA_VERSION);
        assertEquals(RedisTrajectoryStore.class, AgentRlStoragePackage.REDIS_TRAJECTORY_STORE);
        assertEquals(TrajectorySampleStore.class, AgentRlStoragePackage.TRAJECTORY_SAMPLE_STORE);
    }
}
