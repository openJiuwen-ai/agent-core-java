/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.single_agent.schema.AgentCard;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.A2AInvokeHandler;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.A2AStreamHandler;
import com.openjiuwen.extensions.a2a.A2AServer.ServerApp;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Adapter that boots an openjiuwen A2A server.
 *
 * <p>Mirrors Python's {@code A2AServerAdapter} in
 * {@code openjiuwen/extensions/a2a/a2a_server_adapter.py}.</p>
 */
public class A2AServerAdapter {
    public static final String DEFAULT_RPC_URL = "/a2a/jsonrpc/";
    public static final String DEFAULT_REST_URL = "/a2a/rest";

    private final String adapterId;
    private final String version;
    private final AgentCard agentCard;
    private final String protocolBinding;
    private final A2AServer server;
    private final String serveHost;
    private final Integer servePort;
    private ServerApp app;
    private ServerApp restApp;
    private boolean active;
    private Thread serveThread;

    public A2AServerAdapter(String adapterId, AgentCard agentCard) {
        this(adapterId, "", agentCard, null, null, null, DEFAULT_RPC_URL, DEFAULT_REST_URL);
    }

    public A2AServerAdapter(String adapterId,
                            String version,
                            AgentCard agentCard,
                            A2AInvokeHandler invokeHandler,
                            A2AStreamHandler streamHandler,
                            String interfaceUrl,
                            String rpcUrl,
                            String restUrl) {
        if (agentCard == null) {
            throw new IllegalArgumentException("agent_card is required for A2AServerAdapter");
        }
        this.adapterId = adapterId;
        this.version = version == null ? "" : version;
        this.agentCard = agentCard;
        String resolvedInterfaceUrl = interfaceUrl == null ? agentCard.getInterfaceUrl() : interfaceUrl;
        this.protocolBinding = inferProtocolBinding(resolvedInterfaceUrl);
        this.server = new A2AServer(
                this.agentCard,
                adapterId,
                invokeHandler,
                streamHandler,
                interfaceUrl,
                protocolBinding,
                rpcUrl == null ? DEFAULT_RPC_URL : rpcUrl,
                restUrl == null ? DEFAULT_REST_URL : restUrl
        );
        HostPort hostPort = parseInterfaceUrl(resolvedInterfaceUrl);
        this.serveHost = hostPort.host();
        this.servePort = hostPort.port();
    }

    public void start() {
        if (active) {
            return;
        }
        app = server.buildApp();
        restApp = server.getRestApp();
        active = true;
        if (serveThread == null) {
            serveThread = new Thread(this::runServerInThread, "a2a-server-" + adapterId);
            serveThread.setDaemon(true);
            serveThread.start();
        }
    }

    public CompletionStage<Void> stop() {
        if (!active) {
            return CompletableFuture.completedFuture(null);
        }
        return server.stop().thenRun(() -> {
            if (serveThread != null) {
                try {
                    serveThread.join(5000L);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                }
                serveThread = null;
            }
            active = false;
        });
    }

    void runServerInThread() {
        if (serveHost == null || servePort == null) {
            return;
        }
        server.start(serveHost, servePort, "warning").toCompletableFuture().join();
    }

    public String getAdapterId() {
        return adapterId;
    }

    public String getVersion() {
        return version;
    }

    public AgentCard getAgentCard() {
        return agentCard;
    }

    public String getProtocolBinding() {
        return protocolBinding;
    }

    public A2AServer getServer() {
        return server;
    }

    public ServerApp getApp() {
        return app;
    }

    public ServerApp getRestApp() {
        return restApp;
    }

    public boolean isActive() {
        return active;
    }

    public Thread getServeThread() {
        return serveThread;
    }

    public String getServeHost() {
        return serveHost;
    }

    public Integer getServePort() {
        return servePort;
    }

    public static HostPort parseInterfaceUrl(String interfaceUrl) {
        if (interfaceUrl == null || interfaceUrl.isBlank()) {
            return new HostPort(null, null);
        }
        try {
            URI uri = new URI(interfaceUrl);
            if (uri.getHost() == null || uri.getHost().isBlank()) {
                return new HostPort(null, null);
            }
            int port = uri.getPort() == -1 ? 8000 : uri.getPort();
            return new HostPort(uri.getHost(), port);
        } catch (IllegalArgumentException | URISyntaxException exception) {
            return new HostPort(null, null);
        }
    }

    public static String inferProtocolBinding(String interfaceUrl) {
        if (interfaceUrl == null || interfaceUrl.isBlank()) {
            return A2AServer.TransportProtocol.JSONRPC.value();
        }
        String path = "";
        try {
            URI uri = new URI(interfaceUrl);
            path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
        } catch (URISyntaxException exception) {
            path = "";
        }
        if (path.contains("grpc")) {
            throw new IllegalArgumentException("gRPC transport is not supported.");
        }
        if (path.contains("rest")) {
            return A2AServer.TransportProtocol.HTTP_JSON.value();
        }
        return A2AServer.TransportProtocol.JSONRPC.value();
    }

    /**
     * Parsed interface host and port.
     *
     * <p>Mirrors Python's {@code _parse_interface_url(...)} tuple in
     * {@code openjiuwen/extensions/a2a/a2a_server_adapter.py}.</p>
     */
    public record HostPort(String host, Integer port) {
        public HostPort {
            if (host != null && host.isBlank()) {
                host = null;
            }
            if (host == null) {
                port = null;
            }
        }

        @Override
        public String toString() {
            return Objects.toString(host, "null") + ":" + Objects.toString(port, "null");
        }
    }
}
