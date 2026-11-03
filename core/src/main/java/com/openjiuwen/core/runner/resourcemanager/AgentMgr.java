/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.resourcemanager;

/**
 * 0.1.12-compatible agent manager alias.
 *
 * <p>Mirrors Python's {@code AgentMgr} in
 * {@code openjiuwen/core/runner/resources_manager/agent_manager.py}.</p>
 *
 * @param <T> agent type retained for source compatibility
 */
public class AgentMgr<T> extends AgentManager {

    public String kind() {
        return "agent";
    }
}
