/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_evolving.agent_rl.online.launcher;

/**
 * Port availability check tuple.
 * <p>
 * Mirrors Python's launcher port-check tuples.
 */
public record PortCheck(String name, String host, int port) {
}
