/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.mcp;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Counting MCP client test double.
 * Implements {@link McpClient} directly so every connect/disconnect/
 * listTools call and argument can be asserted without Mockito wiring.
 * Outcomes are configurable per operation.
 *
 * @since 0.1.16
 */
public class CountingMcpClient implements McpClient {
    private final AtomicInteger connectCalls = new AtomicInteger();

    private final AtomicInteger disconnectCalls = new AtomicInteger();

    private final AtomicInteger listToolsCalls = new AtomicInteger();

    private final AtomicInteger callToolCalls = new AtomicInteger();

    private final AtomicInteger successfulConnects = new AtomicInteger();

    private final AtomicReference<RuntimeException> connectFailure = new AtomicReference<>();

    private final AtomicBoolean refuseConnect = new AtomicBoolean(false);

    private final List<Float> connectTimeouts = new CopyOnWriteArrayList<>();

    private final List<Float> listToolsTimeouts = new CopyOnWriteArrayList<>();

    private final List<Float> callToolTimeouts = new CopyOnWriteArrayList<>();

    private volatile List<Object> toolsToList = new ArrayList<>();

    private volatile RuntimeException listToolsFailure;

    /**
     * Sets the exception every connect attempt throws.
     *
     * @param failure exception to throw, null to restore success
     */
    public void failConnectWith(RuntimeException failure) {
        connectFailure.set(failure);
    }

    /**
     * Makes every connect attempt return {@code false} (handshake
     * refused). The attempt is recorded but never counted as successful.
     */
    public void refuseConnect() {
        refuseConnect.set(true);
    }

    /**
     * Sets the tool list returned by listTools.
     *
     * @param tools tools to report
     */
    public void listToolsReturning(List<Object> tools) {
        this.toolsToList = new ArrayList<>(tools);
    }

    /**
     * Sets the exception every listTools call throws.
     *
     * @param failure exception to throw, null to restore success
     */
    public void failListToolsWith(RuntimeException failure) {
        this.listToolsFailure = failure;
    }

    @Override
    public boolean connect(int retryTimes, float timeout) throws Exception {
        connectCalls.incrementAndGet();
        connectTimeouts.add(timeout);
        RuntimeException failure = connectFailure.get();
        if (failure instanceof IllegalStateException stateFailure) {
            throw stateFailure;
        }
        if (failure instanceof NullPointerException nullFailure) {
            throw nullFailure;
        }
        if (failure != null) {
            throw new IllegalStateException("injected connect failure", failure);
        }
        if (refuseConnect.get()) {
            return false;
        }
        successfulConnects.incrementAndGet();
        return true;
    }

    @Override
    public boolean disconnect(float timeout) throws Exception {
        disconnectCalls.incrementAndGet();
        return true;
    }

    @Override
    public List<Object> listTools(float timeout) throws Exception {
        listToolsCalls.incrementAndGet();
        listToolsTimeouts.add(timeout);
        RuntimeException failure = listToolsFailure;
        if (failure instanceof IllegalStateException stateFailure) {
            throw stateFailure;
        }
        if (failure instanceof NullPointerException nullFailure) {
            throw nullFailure;
        }
        if (failure != null) {
            throw new IllegalStateException("injected listTools failure", failure);
        }
        return new ArrayList<>(toolsToList);
    }

    @Override
    public Object callTool(String toolName, Map<String, Object> arguments, float timeout) throws Exception {
        callToolCalls.incrementAndGet();
        callToolTimeouts.add(timeout);
        return "ok";
    }

    @Override
    public String getServerPath() {
        return "counting-mcp-client";
    }

    @Override
    public Optional<Object> getToolInfo(String toolName, float timeout) throws Exception {
        return Optional.empty();
    }

    /**
     * Returns how often connect was called (any overload).
     *
     * @return connect call count
     */
    public int connectCalls() {
        return connectCalls.get();
    }

    /**
     * Returns how often disconnect was called (any overload).
     *
     * @return disconnect call count
     */
    public int disconnectCalls() {
        return disconnectCalls.get();
    }

    /**
     * Returns how often listTools was called (any overload).
     *
     * @return listTools call count
     */
    public int listToolsCalls() {
        return listToolsCalls.get();
    }

    /**
     * Returns how often callTool was called.
     *
     * @return callTool call count
     */
    public int callToolCalls() {
        return callToolCalls.get();
    }

    /**
     * Returns how often connect completed successfully (a failed connect
     * never holds a live connection).
     *
     * @return successful connect count
     */
    public int successfulConnects() {
        return successfulConnects.get();
    }

    /**
     * Returns the connect timeouts observed so far, in call order.
     *
     * @return snapshot of connect timeout arguments
     */
    public List<Float> connectTimeouts() {
        return new ArrayList<>(connectTimeouts);
    }

    /**
     * Returns the listTools timeouts observed so far, in call order.
     *
     * @return snapshot of listTools timeout arguments
     */
    public List<Float> listToolsTimeouts() {
        return new ArrayList<>(listToolsTimeouts);
    }

    /**
     * Returns the callTool timeouts observed so far, in call order.
     *
     * @return snapshot of callTool timeout arguments
     */
    public List<Float> callToolTimeouts() {
        return new ArrayList<>(callToolTimeouts);
    }
}
