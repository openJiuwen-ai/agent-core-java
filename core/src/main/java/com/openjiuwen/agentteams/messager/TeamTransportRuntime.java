/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Owns one member's transport and shares an explicitly owned ZeroMQ proxy with in-process teammates.
 * Closing the last member releases the proxy. No process-global team or socket registry is used.
 *
 * @since 2026-11-09
 */
public final class TeamTransportRuntime implements AutoCloseable {
    private static final String LOCAL_ENDPOINT = "tcp://127.0.0.1:*";

    private static final long CLOSE_TIMEOUT_SECONDS = 5L;

    private final String teamName;

    private final Messager messager;

    private final Optional<Network> network;

    private final AtomicBoolean isClosed = new AtomicBoolean();

    private TeamTransportRuntime(String teamName, Messager messager, Optional<Network> network) {
        this.teamName = teamName;
        this.messager = messager;
        this.network = network;
    }

    /**
     * Open a team leader transport. ZeroMQ defaults to an owned loopback proxy with ephemeral ports.
     * Set metadata.pubsub_bind=false to connect to an independently managed proxy instead.
     *
     * @param teamName team identity
     * @param memberName member identity
     * @param config JSON-safe settings; null uses in-process transport
     * @return an owned member runtime
     * @throws IllegalArgumentException if configuration is invalid
     * @throws IllegalStateException if transport startup fails
     */
    public static TeamTransportRuntime open(String teamName, String memberName, MessagerTransportConfig config) {
        return create(teamName, memberName, config, true);
    }

    /**
     * Connect a separately bootstrapped member to configured ZeroMQ endpoints without owning the proxy.
     *
     * @param teamName team identity
     * @param memberName member identity
     * @param config settings containing the shared publication and subscription endpoints
     * @return an owned member runtime
     * @throws IllegalArgumentException if the required endpoints are missing or invalid
     * @throws IllegalStateException if transport startup fails
     */
    public static TeamTransportRuntime connect(String teamName, String memberName, MessagerTransportConfig config) {
        return create(teamName, memberName, config, false);
    }

    private static TeamTransportRuntime create(String team, String member, MessagerTransportConfig config,
            boolean canOwnProxy) {
        requireIdentity(team, "teamName");
        requireIdentity(member, "memberName");
        MessagerTransportConfig settings = config == null ? new MessagerTransportConfig() : config.copy();
        settings.setTeamName(team);
        settings.setNodeId(member);
        if ("inprocess".equals(settings.getBackend())) {
            return new TeamTransportRuntime(team, new InProcessMessager(settings), Optional.empty());
        }
        if (!"pyzmq".equals(settings.getBackend())) {
            throw new IllegalArgumentException("Unsupported team transport backend");
        }
        Network shared = new Network(settings, canOwnProxy);
        try {
            return shared.openMember(member, true);
        } catch (CompletionException | IllegalStateException | IllegalArgumentException failure) {
            shared.closeUnused(failure);
            throw failure;
        }
    }

    /**
     * Allocate a separately owned member transport on the same team network.
     *
     * @param memberName new member identity
     * @return the member runtime
     * @throws IllegalStateException when this runtime has closed or the member already exists
     */
    public synchronized TeamTransportRuntime fork(String memberName) {
        requireIdentity(memberName, "memberName");
        if (isClosed.get()) {
            throw new IllegalStateException("Team transport runtime is closed");
        }
        if (network.isPresent()) {
            return network.get().openMember(memberName, false);
        }
        return new TeamTransportRuntime(teamName, new InProcessMessager(MessagerTransportConfig.builder()
                .teamName(teamName).nodeId(memberName).build()), Optional.empty());
    }

    /**
     * Return the real underlying transport for coordination subscriptions and message delivery.
     *
     * @return the owned transport
     */
    public Messager messager() {
        return messager;
    }

    /**
     * Export JSON-safe bootstrap settings with resolved proxy addresses and known local peers.
     *
     * @param memberName identity of the member being bootstrapped
     * @return independent member settings
     */
    public synchronized MessagerTransportConfig memberConfiguration(String memberName) {
        requireIdentity(memberName, "memberName");
        if (isClosed.get()) {
            throw new IllegalStateException("Team transport runtime is closed");
        }
        if (network.isPresent()) {
            return network.get().memberConfiguration(memberName, false);
        }
        return MessagerTransportConfig.builder().backend("inprocess")
                .teamName(teamName).nodeId(memberName).build();
    }

    /**
     * Close this member once, then close an owned proxy after its final member has closed.
     *
     * @throws IllegalStateException if one or more owned resources cannot close
     */
    @Override
    public synchronized void close() {
        if (!isClosed.compareAndSet(false, true)) {
            return;
        }
        if (network.isPresent()) {
            network.get().release((PyZmqMessager) messager);
            return;
        }
        List<RuntimeException> failures = new ArrayList<>();
        stop(messager, failures);
        throwCloseFailure(failures);
    }

    private static void stop(Messager transport, List<RuntimeException> failures) {
        try {
            transport.stop().orTimeout(CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS).join();
        } catch (CompletionException | IllegalStateException failure) {
            failures.add(failure);
        }
    }

    private static void throwCloseFailure(List<RuntimeException> failures) {
        if (failures.isEmpty()) {
            return;
        }
        IllegalStateException failure = new IllegalStateException(
                "Cannot close team transport resources", failures.get(0));
        failures.stream().skip(1).forEach(failure::addSuppressed);
        throw failure;
    }

    private static void requireIdentity(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static final class Network {
        private static final Pattern TCP_ENDPOINT = Pattern.compile(
                "tcp://(\\[[0-9a-fA-F:.%_-]+]|[a-zA-Z0-9._-]+|\\*):([0-9]{1,5}|\\*)");

        private final Object lifecycleLock = new Object();

        private final MessagerTransportConfig template;

        private final Optional<PyZmqMessager> broker;

        private final Map<String, PyZmqMessager> members = new LinkedHashMap<>();

        private boolean isClosing;

        private Network(MessagerTransportConfig template, boolean canOwnProxy) {
            this.template = template;
            if (template.getMetadata() == null) {
                template.setMetadata(new LinkedHashMap<>());
            }
            boolean ownsProxy = canOwnProxy && !Boolean.FALSE.equals(template.getMetadata().get("pubsub_bind"));
            prepareEndpoints(template, ownsProxy);
            if (ownsProxy) {
                broker = Optional.of(startBroker(template));
            } else {
                broker = Optional.empty();
            }
        }

        private static void prepareEndpoints(MessagerTransportConfig config, boolean ownsProxy) {
            if (!Double.isFinite(config.getRequestTimeout()) || config.getRequestTimeout() <= 0.0d) {
                throw new IllegalArgumentException("Team transport requestTimeout must be finite and positive");
            }
            if (config.getMetadata() == null) {
                config.setMetadata(new LinkedHashMap<>());
            }
            if (isBlank(config.getPubsubPublishAddr()) && isBlank(config.getPubsubSubscribeAddr()) && ownsProxy) {
                config.setPubsubPublishAddr(LOCAL_ENDPOINT);
                config.setPubsubSubscribeAddr(LOCAL_ENDPOINT);
            }
            validateEndpoint(config.getPubsubPublishAddr(), ownsProxy);
            validateEndpoint(config.getPubsubSubscribeAddr(), ownsProxy);
            if (!isBlank(config.getDirectAddr())) {
                validateEndpoint(config.getDirectAddr(), true);
            }
            validatePeers(config.getKnownPeers());
            validatePeers(config.getBootstrapPeers());
        }

        private static void validatePeers(List<MessagerPeerConfig> peers) {
            if (peers == null) {
                return;
            }
            for (MessagerPeerConfig peer : peers) {
                if (peer == null || isBlank(peer.getAgentId())
                        || peer.getAddrs() == null || peer.getAddrs().isEmpty()) {
                    throw new IllegalArgumentException("ZeroMQ peer requires an identity and endpoints");
                }
                peer.getAddrs().forEach(endpoint -> validateEndpoint(endpoint, false));
            }
        }

        private static PyZmqMessager startBroker(MessagerTransportConfig template) {
            MessagerTransportConfig brokerConfig = MessagerTransportConfig.builder().backend("pyzmq")
                    .teamName(template.getTeamName()).nodeId(template.getNodeId() + "-proxy")
                    .pubsubPublishAddr(template.getPubsubPublishAddr())
                    .pubsubSubscribeAddr(template.getPubsubSubscribeAddr())
                    .requestTimeout(template.getRequestTimeout()).metadata(Map.of("pubsub_bind", true)).build();
            PyZmqMessager transport = new PyZmqMessager(brokerConfig);
            start(transport, template.getRequestTimeout());
            template.setPubsubPublishAddr(connectEndpoint(brokerConfig.getPubsubPublishAddr()));
            template.setPubsubSubscribeAddr(connectEndpoint(brokerConfig.getPubsubSubscribeAddr()));
            return transport;
        }

        private static void start(PyZmqMessager transport, double timeout) {
            try {
                transport.start().orTimeout(Math.max(1L, Math.round(timeout * 1_000.0d)), TimeUnit.MILLISECONDS).join();
            } catch (CompletionException | IllegalStateException failure) {
                List<RuntimeException> cleanup = new ArrayList<>();
                stop(transport, cleanup);
                cleanup.forEach(failure::addSuppressed);
                throw new IllegalStateException("Cannot start team ZeroMQ transport", failure);
            }
        }

        private TeamTransportRuntime openMember(String name, boolean isRoot) {
            synchronized (lifecycleLock) {
                if (isClosing || members.containsKey(name)) {
                    throw new IllegalStateException("Team transport is closing or member is already registered");
                }
                PyZmqMessager transport = new PyZmqMessager(memberConfiguration(name, isRoot));
                start(transport, template.getRequestTimeout());
                members.values().forEach(peer -> {
                    transport.registerPeer(localPeer(peer));
                    peer.registerPeer(localPeer(transport));
                });
                members.put(name, transport);
                return new TeamTransportRuntime(template.getTeamName(), transport, Optional.of(this));
            }
        }

        private MessagerTransportConfig memberConfiguration(String name, boolean isRoot) {
            synchronized (lifecycleLock) {
                MessagerTransportConfig config = template.copy();
                config.setNodeId(name);
                config.setDirectAddr(isRoot && !isBlank(template.getDirectAddr())
                        ? template.getDirectAddr() : memberEndpoint(name));
                Map<String, Object> metadata = new LinkedHashMap<>(config.getMetadata());
                metadata.put("pubsub_bind", false);
                metadata.put("subscription_barrier", true);
                config.setMetadata(metadata);
                List<MessagerPeerConfig> peers = config.getKnownPeers() == null
                        ? new ArrayList<>() : new ArrayList<>(config.getKnownPeers());
                members.values().stream().map(Network::localPeer).forEach(peers::add);
                config.setKnownPeers(peers);
                return config;
            }
        }

        private static MessagerPeerConfig localPeer(PyZmqMessager transport) {
            MessagerPeerConfig peer = transport.getLocalPeer();
            peer.setAddrs(peer.getAddrs().stream().map(Network::connectEndpoint).toList());
            peer.setMetadata(Map.of("readiness_publisher", true));
            return peer;
        }

        private String memberEndpoint(String name) {
            List<MessagerPeerConfig> peers = template.getKnownPeers();
            if (peers != null) {
                for (MessagerPeerConfig peer : peers) {
                    if (name.equals(peer.getAgentId())) {
                        return peer.getAddrs().get(0);
                    }
                }
            }
            return LOCAL_ENDPOINT;
        }

        private static String connectEndpoint(String endpoint) {
            return endpoint.replace("tcp://0.0.0.0:", "tcp://127.0.0.1:")
                    .replace("tcp://[::]:", "tcp://[::1]:");
        }

        private void release(PyZmqMessager member) {
            List<RuntimeException> failures = new ArrayList<>();
            stop(member, failures);
            boolean closesProxy;
            synchronized (lifecycleLock) {
                members.values().removeIf(value -> value == member);
                String memberId = member.getLocalPeer().getAgentId();
                members.values().forEach(peer -> peer.unregisterPeer(memberId));
                closesProxy = members.isEmpty();
                isClosing = closesProxy;
            }
            if (closesProxy) {
                broker.ifPresent(proxy -> stop(proxy, failures));
            }
            throwCloseFailure(failures);
        }

        private void closeUnused(RuntimeException original) {
            List<RuntimeException> failures = new ArrayList<>();
            broker.ifPresent(proxy -> stop(proxy, failures));
            failures.forEach(original::addSuppressed);
        }

        private static void validateEndpoint(String endpoint, boolean canBind) {
            if (isBlank(endpoint)) {
                throw new IllegalArgumentException("ZeroMQ requires publication and subscription endpoints");
            }
            Matcher matcher = TCP_ENDPOINT.matcher(endpoint);
            if (!matcher.matches()) {
                throw new IllegalArgumentException("Invalid ZeroMQ TCP endpoint");
            }
            String host = matcher.group(1);
            if (!canBind && ("*".equals(host) || "0.0.0.0".equals(host) || "[::]".equals(host))) {
                throw new IllegalArgumentException("A connect endpoint must have a routable TCP host");
            }
            String port = matcher.group(2);
            if ("*".equals(port)) {
                if (!canBind) {
                    throw new IllegalArgumentException("A connect endpoint must have a resolved TCP port");
                }
                return;
            }
            int number = Integer.parseInt(port);
            if (number < 1 || number > 65_535) {
                throw new IllegalArgumentException("ZeroMQ TCP port must be in range 1..65535");
            }
        }

        private static boolean isBlank(String value) {
            return value == null || value.isBlank();
        }
    }
}
