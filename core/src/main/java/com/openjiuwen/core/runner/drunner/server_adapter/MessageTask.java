/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;

import java.util.concurrent.Future;

/**
 * Running MQ request task.
 *
 * <p>Mirrors Python's {@code MessageTask} in
 * {@code openjiuwen/core/runner/drunner/server_adapter/mq_server_adapter.py}.</p>
 *
 * @param message original request message
 * @param task running Java task handle
 */
public record MessageTask(DmqRequestMessage message, Future<?> task) {
}
