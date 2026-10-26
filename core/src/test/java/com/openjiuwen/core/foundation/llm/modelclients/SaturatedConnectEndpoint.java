/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.modelclients;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

/**
 * Fills a loopback listener's connection backlog without accepting connections.
 * Unlike an arbitrary unreachable IP, the endpoint has a route and stays owned
 * throughout the test. All sockets are closed even if setup or assertions fail.
 */
final class SaturatedConnectEndpoint implements AutoCloseable {
    private static final int MAX_FILL_CONNECTIONS = 32;

    private final ServerSocket listener;
    private final List<Socket> connections = new ArrayList<>();

    private SaturatedConnectEndpoint() throws IOException {
        listener = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"));
    }

    static SaturatedConnectEndpoint open() throws IOException {
        SaturatedConnectEndpoint endpoint = new SaturatedConnectEndpoint();
        try {
            endpoint.fillBacklog();
            return endpoint;
        } catch (IOException exception) {
            try {
                endpoint.close();
            } catch (IOException cleanupFailure) {
                exception.addSuppressed(cleanupFailure);
            }
            throw exception;
        }
    }

    String apiBase() {
        return "http://127.0.0.1:" + listener.getLocalPort();
    }

    private void fillBacklog() throws IOException {
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", listener.getLocalPort());
        for (int index = 0; index < MAX_FILL_CONNECTIONS; index++) {
            Socket connection = new Socket();
            connections.add(connection);
            try {
                connection.connect(address, 200);
            } catch (SocketTimeoutException expected) {
                connection.close();
                return;
            }
        }
        throw new IOException("Could not saturate the local TCP backlog within the fixture limit");
    }

    @Override
    public void close() throws IOException {
        IOException failure = null;
        for (Socket connection : connections) {
            try {
                connection.close();
            } catch (IOException exception) {
                failure = collectFailure(failure, exception);
            }
        }
        try {
            listener.close();
        } catch (IOException exception) {
            failure = collectFailure(failure, exception);
        }
        if (failure != null) {
            throw failure;
        }
    }

    private static IOException collectFailure(IOException previous, IOException next) {
        if (previous == null) {
            return next;
        }
        previous.addSuppressed(next);
        return previous;
    }
}
