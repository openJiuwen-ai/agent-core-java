/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Holds the first connection before its greeting and forwards subsequent connections to a real peer.
 *
 * @since 2026-11-10
 */
final class StalledHandshakeProxy implements AutoCloseable {
    private final ServerSocket listener;

    private final URI target;

    private final List<Socket> sockets = new CopyOnWriteArrayList<>();

    private final AtomicInteger connections = new AtomicInteger();

    private final AtomicReference<IOException> failure = new AtomicReference<>();

    private final ExecutorService executor = OpenJiuwenExecutors.newBoundedModulePool("handshake-proxy", 5, 32, true);

    private volatile boolean isClosed;

    StalledHandshakeProxy(String endpoint) throws IOException {
        target = URI.create(endpoint);
        listener = new ServerSocket(0, 8, InetAddress.getLoopbackAddress());
        executor.execute(this::accept);
    }

    String endpoint() {
        return "tcp://127.0.0.1:" + listener.getLocalPort();
    }

    int connections() {
        return connections.get();
    }

    @Override
    public void close() throws IOException, InterruptedException {
        isClosed = true;
        try {
            listener.close();
            for (Socket socket : sockets) {
                socket.close();
            }
        } finally {
            executor.shutdownNow();
        }
        if (!executor.awaitTermination(3L, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Handshake proxy did not stop");
        }
        if (failure.get() != null) {
            throw new IOException("Handshake proxy failed", failure.get());
        }
    }

    private void accept() {
        try {
            while (!isClosed) {
                Socket incoming = listener.accept();
                sockets.add(incoming);
                if (connections.incrementAndGet() > 1) {
                    Socket outgoing = new Socket(target.getHost(), target.getPort());
                    sockets.add(outgoing);
                    executor.execute(() -> forward(incoming, outgoing));
                    executor.execute(() -> forward(outgoing, incoming));
                }
            }
        } catch (IOException exception) {
            recordFailure(exception);
        }
    }

    private void forward(Socket source, Socket destination) {
        try {
            source.getInputStream().transferTo(destination.getOutputStream());
            destination.shutdownOutput();
        } catch (IOException exception) {
            recordFailure(exception);
        }
    }

    private void recordFailure(IOException exception) {
        if (!isClosed) {
            failure.compareAndSet(null, exception);
        }
    }
}
