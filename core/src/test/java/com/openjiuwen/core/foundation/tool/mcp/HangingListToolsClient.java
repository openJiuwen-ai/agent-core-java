/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MCP client stub whose listTools blocks until released or until the
 * caller-supplied discovery timeout elapses, then fails with a simulated
 * discovery timeout. Used to pin
 * the bounded wait of destroy behind a hanging tool discovery: with a
 * resolved discovery timeout the stub always self-unblocks, and tests that
 * release the gate early must do so in a finally block so no worker thread
 * leaks.
 *
 * @since 0.1.16
 */
public class HangingListToolsClient extends CountingMcpClient {
    private final CountDownLatch releaseGate;

    private final AtomicInteger listToolsEntries = new AtomicInteger();

    /**
     * Creates the hanging stub.
     *
     * @param releaseGate latch the test counts down to unblock listTools early
     */
    public HangingListToolsClient(CountDownLatch releaseGate) {
        this.releaseGate = releaseGate;
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        listToolsEntries.incrementAndGet();
        boolean isReleased = releaseGate.await(discoveryWaitMillis(timeout), TimeUnit.MILLISECONDS);
        if (!isReleased) {
            throw new IOException("simulated discovery timeout after " + timeout + "s");
        }
        return super.listTools(timeout);
    }

    /**
     * Number of listTools invocations observed so far.
     *
     * @return the observed invocation count
     */
    public int listToolsEntries() {
        return listToolsEntries.get();
    }

    private static long discoveryWaitMillis(float timeout) {
        return Math.max(1L, BigDecimal.valueOf(timeout).multiply(BigDecimal.valueOf(1000L)).longValue());
    }
}
