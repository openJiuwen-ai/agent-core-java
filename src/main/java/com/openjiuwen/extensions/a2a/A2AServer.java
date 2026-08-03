/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.extensions.a2a;

import com.openjiuwen.core.singleagent.schema.AgentCard;
import com.openjiuwen.core.singleagent.schema.AgentResult;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.A2AInvokeHandler;
import com.openjiuwen.extensions.a2a.A2AAgentExecutor.A2AStreamHandler;
import com.openjiuwen.extensions.a2a.A2AAgentCardAdapter.A2aAgentCard;
import com.openjiuwen.extensions.a2a.A2AAgentCardAdapter.AgentCapabilities;
import com.openjiuwen.extensions.a2a.A2AAgentCardAdapter.AgentInterface;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Minimal A2A server wrapper for openjiuwen.
 *
 * <p>Mirrors Python's {@code A2AServer} in
 * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
 */
public class A2AServer {
    public static final String DEFAULT_ADAPTER_ID = "openjiuwen-a2a-agent";
    public static final String DEFAULT_RPC_URL = "/a2a/jsonrpc/";
    public static final String DEFAULT_REST_URL = "/a2a/rest";

    private final String adapterId;
    private final A2aAgentCard a2aAgentCard;
    private final Set<TransportProtocol> transportProtocols;
    private final String rpcUrl;
    private final String restUrl;
    private final InMemoryTaskStore taskStore;
    private final A2AAgentExecutor executor;
    private final DefaultRequestHandler requestHandler;
    private ServerApp app;
    private ServerApp restApp;
    private ServerRuntime uvicornServer;

    public A2AServer(AgentCard agentCard) {
        this(agentCard, DEFAULT_ADAPTER_ID, null, null, null, TransportProtocol.JSONRPC.value(),
                DEFAULT_RPC_URL, DEFAULT_REST_URL);
    }

    public A2AServer(AgentCard agentCard,
                     String adapterId,
                     A2AInvokeHandler invokeHandler,
                     A2AStreamHandler streamHandler,
                     String interfaceUrl,
                     String protocolBinding,
                     String rpcUrl,
                     String restUrl) {
        AgentCard safeAgentCard = Objects.requireNonNull(agentCard, "agentCard");
        this.adapterId = adapterId == null ? DEFAULT_ADAPTER_ID : adapterId;
        String binding = protocolBinding == null ? TransportProtocol.JSONRPC.value() : protocolBinding;
        if (TransportProtocol.GRPC.matches(binding)) {
            throw new IllegalArgumentException("gRPC transport is not supported.");
        }
        String resolvedInterfaceUrl = interfaceUrl == null ? safeAgentCard.getInterfaceUrl() : interfaceUrl;
        String normalizedInterfaceUrl = normalizeJsonrpcInterfaceUrl(resolvedInterfaceUrl);
        this.a2aAgentCard = buildA2aAgentCard(safeAgentCard, normalizedInterfaceUrl, binding);
        this.transportProtocols = resolveTransportProtocols();
        String rawRpcUrl = rpcUrl == null ? DEFAULT_RPC_URL : rpcUrl;
        this.rpcUrl = transportProtocols.contains(TransportProtocol.JSONRPC)
                ? normalizeJsonrpcRoutePath(rawRpcUrl)
                : ensureLeadingSlash(rawRpcUrl);
        this.restUrl = restUrl == null ? DEFAULT_REST_URL : restUrl;
        this.taskStore = new InMemoryTaskStore();
        this.executor = new A2AAgentExecutor(invokeHandler, streamHandler);
        this.requestHandler = new DefaultRequestHandler(executor, taskStore, a2aAgentCard);
    }

    public CompletionStage<Void> start() {
        return start("127.0.0.1", 8000, "warning");
    }

    public CompletionStage<Void> start(String host, int port, String logLevel) {
        return serve(host, port, logLevel);
    }

    public CompletionStage<Void> stop() {
        if (uvicornServer == null) {
            return CompletableFuture.completedFuture(null);
        }
        uvicornServer.setShouldExit(true);
        return CompletableFuture.completedFuture(null);
    }

    public ServerApp buildApp() {
        if (app != null) {
            return app;
        }
        ServerApp resolvedApp = new ServerApp();
        resolvedApp.addRoute(new Route("/.well-known/agent-card.json", "agent-card"));
        if (transportProtocols.contains(TransportProtocol.JSONRPC)) {
            resolvedApp.addRoute(new Route(rpcUrl, "jsonrpc"));
        }
        if (transportProtocols.contains(TransportProtocol.HTTP_JSON)) {
            if (restApp == null) {
                restApp = new ServerApp();
                restApp.addRoute(new Route("/", "rest"));
            }
            resolvedApp.mount(restUrl, restApp);
        }
        this.app = resolvedApp;
        return app;
    }

    public String getAdapterId() {
        return adapterId;
    }

    public A2aAgentCard getA2aAgentCard() {
        return a2aAgentCard;
    }

    public Set<TransportProtocol> getTransportProtocols() {
        return Set.copyOf(transportProtocols);
    }

    public String getRpcUrl() {
        return rpcUrl;
    }

    public String getRestUrl() {
        return restUrl;
    }

    public InMemoryTaskStore getTaskStore() {
        return taskStore;
    }

    public A2AAgentExecutor getExecutor() {
        return executor;
    }

    public DefaultRequestHandler getRequestHandler() {
        return requestHandler;
    }

    public ServerApp getApp() {
        return app;
    }

    public ServerApp getRestApp() {
        return restApp;
    }

    public ServerRuntime getUvicornServer() {
        return uvicornServer;
    }

    void setUvicornServer(ServerRuntime uvicornServer) {
        this.uvicornServer = uvicornServer;
    }

    Set<TransportProtocol> resolveTransportProtocols() {
        Set<TransportProtocol> transports = new LinkedHashSet<>();
        for (AgentInterface agentInterface : a2aAgentCard.getSupportedInterfaces()) {
            String binding = agentInterface.getProtocolBinding();
            if (binding == null || binding.isBlank()) {
                continue;
            }
            TransportProtocol transport = TransportProtocol.fromValue(binding);
            if (transport == null) {
                continue;
            }
            if (transport == TransportProtocol.GRPC) {
                throw new IllegalArgumentException("gRPC transport is not supported.");
            }
            transports.add(transport);
        }
        if (transports.isEmpty()) {
            transports.add(TransportProtocol.JSONRPC);
        }
        return transports;
    }

    private CompletionStage<Void> serve(String host, int port, String logLevel) {
        ServerApp resolvedApp = buildApp();
        this.uvicornServer = new ServerRuntime(resolvedApp, host, port, logLevel);
        return uvicornServer.serve();
    }

    static String normalizeJsonrpcRoutePath(String rpcUrl) {
        String normalized = rpcUrl == null ? "" : rpcUrl;
        if (!normalized.startsWith("/")) {
            normalized = "/" + normalized;
        }
        while (normalized.endsWith("/") && normalized.length() > 1) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if ("/".equals(normalized)) {
            return "/";
        }
        return normalized + "/";
    }

    static String normalizeJsonrpcInterfaceUrl(String interfaceUrl) {
        if (interfaceUrl == null || interfaceUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(interfaceUrl);
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (!path.toLowerCase(Locale.ROOT).contains("jsonrpc")) {
                return interfaceUrl;
            }
            String normalizedPath = path;
            while (normalizedPath.endsWith("/") && normalizedPath.length() > 1) {
                normalizedPath = normalizedPath.substring(0, normalizedPath.length() - 1);
            }
            normalizedPath = normalizedPath + "/";
            return new URI(uri.getScheme(), uri.getRawAuthority(), normalizedPath,
                    uri.getRawQuery(), uri.getRawFragment()).toString();
        } catch (URISyntaxException exception) {
            return interfaceUrl;
        }
    }

    static A2aAgentCard buildA2aAgentCard(AgentCard agentCard,
                                          String interfaceUrl,
                                          String protocolBinding) {
        A2aAgentCard a2aCard = A2AAgentCardAdapter.toA2aAgentCard(
                agentCard,
                interfaceUrl,
                protocolBinding,
                "1.0",
                null,
                null);
        if (a2aCard != null) {
            return a2aCard;
        }
        return new A2aAgentCard(
                isBlank(agentCard.getName()) ? agentCard.getId() : agentCard.getName(),
                agentCard.getDescription() == null ? "" : agentCard.getDescription(),
                new AgentCapabilities(true, false),
                A2AAgentCardAdapter.DEFAULT_INPUT_MODES,
                A2AAgentCardAdapter.DEFAULT_OUTPUT_MODES);
    }

    private static String ensureLeadingSlash(String value) {
        String normalized = value == null ? "" : value;
        return normalized.startsWith("/") ? normalized : "/" + normalized;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Transport protocol values used by the A2A server wrapper.
     *
     * <p>Mirrors Python's {@code TransportProtocol} checks in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    public enum TransportProtocol {
        JSONRPC("JSONRPC"),
        HTTP_JSON("HTTP+JSON"),
        GRPC("GRPC");

        private final String value;

        TransportProtocol(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }

        boolean matches(String candidate) {
            return value.equalsIgnoreCase(candidate)
                    || name().equalsIgnoreCase(candidate)
                    || name().replace('_', '+').equalsIgnoreCase(candidate);
        }

        static TransportProtocol fromValue(String value) {
            for (TransportProtocol protocol : values()) {
                if (protocol.matches(value)) {
                    return protocol;
                }
            }
            return null;
        }
    }

    /**
     * Lightweight application route container.
     *
     * <p>Mirrors Python's {@code FastAPI} route container used in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    public static final class ServerApp {
        private final List<Route> routes = new ArrayList<>();
        private final List<Mount> mounts = new ArrayList<>();

        public void addRoute(Route route) {
            routes.add(route);
        }

        public void mount(String path, ServerApp mountedApp) {
            mounts.add(new Mount(path, mountedApp));
        }

        public List<Route> getRoutes() {
            return List.copyOf(routes);
        }

        public List<Mount> getMounts() {
            return List.copyOf(mounts);
        }
    }

    /**
     * Lightweight route descriptor.
     *
     * <p>Mirrors Python's FastAPI route entries in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    public static final class Route {
        private final String path;
        private final String name;

        public Route(String path, String name) {
            this.path = path;
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public String getName() {
            return name;
        }
    }

    /**
     * Lightweight mounted-app descriptor.
     *
     * <p>Mirrors Python's {@code app.mount(...)} behavior in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    public static final class Mount {
        private final String path;
        private final ServerApp app;

        public Mount(String path, ServerApp app) {
            this.path = path;
            this.app = app;
        }

        public String getPath() {
            return path;
        }

        public ServerApp getApp() {
            return app;
        }
    }

    /**
     * Minimal server runtime marker with a shutdown flag.
     *
     * <p>Mirrors Python's {@code uvicorn.Server} role in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    public static final class ServerRuntime {
        private final ServerApp app;
        private final String host;
        private final int port;
        private final String logLevel;
        private boolean shouldExit;

        public ServerRuntime(ServerApp app, String host, int port, String logLevel) {
            this.app = app;
            this.host = host;
            this.port = port;
            this.logLevel = logLevel;
        }

        public CompletionStage<Void> serve() {
            return CompletableFuture.completedFuture(null);
        }

        public ServerApp getApp() {
            return app;
        }

        public String getHost() {
            return host;
        }

        public int getPort() {
            return port;
        }

        public String getLogLevel() {
            return logLevel;
        }

        public boolean isShouldExit() {
            return shouldExit;
        }

        public void setShouldExit(boolean shouldExit) {
            this.shouldExit = shouldExit;
        }
    }

    /**
     * Minimal in-memory task store marker.
     *
     * <p>Mirrors Python's {@code InMemoryTaskStore} in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    public static final class InMemoryTaskStore {
    }

    /**
     * Minimal request-handler descriptor.
     *
     * <p>Mirrors Python's {@code DefaultRequestHandler} in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    public static final class DefaultRequestHandler {
        private final A2AAgentExecutor agentExecutor;
        private final InMemoryTaskStore taskStore;
        private final A2aAgentCard agentCard;

        public DefaultRequestHandler(A2AAgentExecutor agentExecutor,
                                     InMemoryTaskStore taskStore,
                                     A2aAgentCard agentCard) {
            this.agentExecutor = agentExecutor;
            this.taskStore = taskStore;
            this.agentCard = agentCard;
        }

        public A2AAgentExecutor getAgentExecutor() {
            return agentExecutor;
        }

        public InMemoryTaskStore getTaskStore() {
            return taskStore;
        }

        public A2aAgentCard getAgentCard() {
            return agentCard;
        }
    }

    /**
     * Invoke handler alias for A2A server construction.
     *
     * <p>Mirrors Python's {@code A2AInvokeHandler} in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    @FunctionalInterface
    public interface A2AServerInvokeHandler extends A2AInvokeHandler {
        @Override
        CompletionStage<AgentResult> invoke(java.util.Map<String, Object> requestPayload);
    }

    /**
     * Stream handler alias for A2A server construction.
     *
     * <p>Mirrors Python's {@code A2AStreamHandler} in
     * {@code openjiuwen/extensions/a2a/a2a_server.py}.</p>
     */
    @FunctionalInterface
    public interface A2AServerStreamHandler extends A2AStreamHandler {
        @Override
        Iterable<AgentResult> stream(java.util.Map<String, Object> requestPayload);
    }
}
