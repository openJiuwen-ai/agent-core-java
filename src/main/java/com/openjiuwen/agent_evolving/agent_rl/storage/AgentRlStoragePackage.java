/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.storage;

/**
 * Package bridge for shared RL storage exports.
 * <p>
 * Mirrors Python's {@code openjiuwen/agent_evolving/agent_rl/storage/__init__.py}.
 */
public final class AgentRlStoragePackage {

    public static final String PYTHON_MODULE = "openjiuwen/agent_evolving/agent_rl/storage/__init__.py";
    public static final Class<InMemoryTrajectoryStore> IN_MEMORY_TRAJECTORY_STORE = InMemoryTrajectoryStore.class;
    public static final Class<LoRARepository> LORA_REPOSITORY = LoRARepository.class;
    public static final Class<LoRARepository.LoRAVersion> LORA_VERSION = LoRARepository.LoRAVersion.class;
    public static final Class<RedisTrajectoryStore> REDIS_TRAJECTORY_STORE = RedisTrajectoryStore.class;
    public static final Class<TrajectorySampleStore> TRAJECTORY_SAMPLE_STORE = TrajectorySampleStore.class;

    private AgentRlStoragePackage() {
    }
}
