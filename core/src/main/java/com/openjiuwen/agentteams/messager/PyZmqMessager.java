/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.messager;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.agentteams.messager.TransportHandlerDispatcher.DispatchResult;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.common.logging.Loggers;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZFrame;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import org.zeromq.ZMsg;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * ZeroMQ-compatible team transport backed by JeroMQ.
 *
 * <p>Every ZeroMQ socket is created, used, and closed by one dedicated I/O loop. Direct messages use
 * ROUTER/DEALER with delivery acknowledgement. Topic messages use PUB/SUB and can optionally host the team's
 * XPUB/XSUB proxy when {@code metadata.pubsub_bind} is {@code true}.</p>
 *
 * @since 0.1.7
 */
public class PyZmqMessager implements Messager {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private static final Pattern THREAD_NAME_UNSAFE = Pattern.compile("[^a-zA-Z0-9_-]");

    private static final String REPLY_ROUTE_MARKER = ":reply:";

    private static final String READINESS_EVENT = "__jiuwen_subscription_ready_v1";

    private static final String READINESS_REQUEST = "__jiuwen_subscription_probe_v1";

    private static final long READINESS_PROBE_INTERVAL_NANOS = TimeUnit.MILLISECONDS.toNanos(25L);

    private static final int COMMAND_QUEUE_CAPACITY = 1_024;

    private static final int POLL_TIMEOUT_MILLIS = 10;

    private static final int POLLER_SOCKET_CAPACITY = 4;

    private static final int SOCKET_LINGER_MILLIS = 0;

    private static final long MAX_STOP_DRAIN_MILLIS = 5_000L;

    private final MessagerTransportConfig config;

    private final String localNodeId;

    private final Object lifecycleLock = new Object();

    private final BlockingQueue<IoCommand> commandQueue = new ArrayBlockingQueue<>(COMMAND_QUEUE_CAPACITY);

    private final Map<String, MessagerPeerConfig> peers = new ConcurrentHashMap<>();

    private final Map<String, MessagerHandler> topicHandlers = new LinkedHashMap<>();

    private final Map<String, MessagerHandler> directHandlers = new LinkedHashMap<>();

    private final List<PendingSend> pendingSends = new ArrayList<>();

    private final Map<String, PendingSubscription> pendingSubscriptions = new LinkedHashMap<>();

    private final Map<DispatchResult, AtomicLong> topicDropCounts = new ConcurrentHashMap<>();

    private volatile boolean isRunning;

    private volatile boolean isStopRequested;

    private volatile CompletableFuture<Void> startFuture = CompletableFuture.completedFuture(null);

    private volatile CompletableFuture<Void> stopFuture = CompletableFuture.completedFuture(null);

    private ExecutorService ioExecutor;

    private TransportHandlerDispatcher handlerDispatcher;

    private volatile long stopDeadlineNanos;

    private ZContext context;

    private ZMQ.Poller poller;

    private ZMQ.Socket router;

    private ZMQ.Socket publisher;

    private ZMQ.Socket subscriber;

    private ZMQ.Socket proxyPublisher;

    private ZMQ.Socket proxySubscriber;

    private int routerPollIndex = -1;

    private int subscriberPollIndex = -1;

    private int proxyPublisherPollIndex = -1;

    private int proxySubscriberPollIndex = -1;

    /**
     * Create a JeroMQ transport from JSON-safe settings.
     *
     * @param config transport addresses, node identity, peers, and timeout settings
     * @since 0.1.7
     */
    public PyZmqMessager(MessagerTransportConfig config) {
        this.config = config != null ? config : MessagerTransportConfig.builder().backend("pyzmq").build();
        this.localNodeId = resolveLocalNodeId(this.config.getNodeId());
        this.config.setNodeId(localNodeId);
        registerInitialPeers(this.config.getBootstrapPeers());
        registerInitialPeers(this.config.getKnownPeers());
    }

    /**
     * Return this node's direct endpoint for peer discovery.
     *
     * @return local peer metadata; the address is populated after {@link #start()} when a wildcard port is used
     * @since 0.1.7
     */
    public MessagerPeerConfig getLocalPeer() {
        List<String> addresses = isBlank(config.getDirectAddr()) ? List.of() : List.of(config.getDirectAddr());
        return MessagerPeerConfig.builder().agentId(localNodeId).addrs(addresses).build();
    }

    /**
     * Return lifetime counts of topic messages rejected by this instance after receipt.
     * PUB/SUB has no delivery acknowledgement; these counts do not include upstream network loss.
     *
     * @return immutable counts keyed by CLOSED, CAPACITY_EXHAUSTED, or SUBMISSION_REJECTED
     * @since 2026-11-06
     */
    public Map<String, Long> getTopicDropCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        topicDropCounts.forEach((reason, count) -> counts.put(reason.name(), count.get()));
        return Map.copyOf(counts);
    }

    /**
     * Register or replace a direct-message route.
     *
     * @param peer peer identity and at least one routable endpoint
     * @throws IllegalArgumentException when the peer identity or endpoint is missing
     * @since 0.1.7
     */
    public void registerPeer(MessagerPeerConfig peer) {
        validatePeer(peer);
        peers.put(peer.getAgentId(), copyPeer(peer));
    }

    void unregisterPeer(String memberId) {
        peers.remove(memberId);
    }

    /**
     * Start the transport I/O loop and bind or connect configured endpoints.
     *
     * @return future completed when all sockets are ready
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> start() {
        synchronized (lifecycleLock) {
            if (isRunning && !isStopRequested) {
                return startFuture;
            }
            if (isRunning) {
                return stopFuture.thenCompose(ignored -> start());
            }
            prepareIoLoop();
            return startFuture;
        }
    }

    /**
     * Stop the I/O loop and deterministically close every socket and context.
     * Already accepted handlers drain for a bounded interval before cancellation.
     * Subsequent operations require an explicit {@link #start()}.
     *
     * @return future completed after transport resources are closed
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> stop() {
        synchronized (lifecycleLock) {
            if (!isStopRequested) {
                stopDeadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(
                        Math.min(MAX_STOP_DRAIN_MILLIS, requestTimeoutMillis()));
            }
            isStopRequested = true;
            if (!isRunning) {
                return CompletableFuture.completedFuture(null);
            }
            return stopFuture;
        }
    }

    /**
     * Publish one event on a team topic.
     *
     * @param topicId topic identifier
     * @param message event to publish
     * @return future completed after the message is accepted by the PUB socket
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> publish(String topicId, EventMessage message) {
        if (isBlank(topicId)) {
            return failedFuture(new IllegalArgumentException("topicId is required"));
        }
        EventMessage effectiveMessage = stampSender(Objects.requireNonNull(message, "message is required"));
        byte[] payload;
        try {
            payload = serializeMessage(effectiveMessage, null);
        } catch (JsonProcessingException jsonException) {
            return failedFuture(new IllegalArgumentException("Event message must be JSON serializable", jsonException));
        }
        return enqueueImmediate(() -> publishOnIoThread(topicId, payload));
    }

    /**
     * Subscribe one local handler to a team topic.
     *
     * @param topicId topic identifier
     * @param handler asynchronous event handler
     * @return future completed after the SUB filter is installed; with metadata.subscription_barrier=true,
     *     completion additionally requires a private probe to traverse the actual PUB/SUB path
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> subscribe(String topicId, MessagerHandler handler) {
        if (isBlank(topicId)) {
            return failedFuture(new IllegalArgumentException("topicId is required"));
        }
        Objects.requireNonNull(handler, "handler is required");
        if (config.getMetadata() != null && Boolean.TRUE.equals(config.getMetadata().get("subscription_barrier"))) {
            return enqueue(result -> subscribeWithReadiness(topicId, handler, result));
        }
        return enqueueImmediate(() -> subscribeOnIoThread(topicId, handler));
    }

    /**
     * Remove the local handler and SUB filter for a topic.
     *
     * @param topicId topic identifier
     * @return future completed after local unsubscription
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> unsubscribe(String topicId) {
        if (isBlank(topicId)) {
            return CompletableFuture.completedFuture(null);
        }
        return enqueueImmediate(() -> unsubscribeOnIoThread(topicId));
    }

    /**
     * Send one event to a configured peer and wait for its transport acknowledgement.
     *
     * @param agentId recipient identity
     * @param message event to send
     * @return future completed after the peer ROUTER acknowledges the message
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> send(String agentId, EventMessage message) {
        if (isBlank(agentId)) {
            return failedFuture(new IllegalArgumentException("agentId is required"));
        }
        EventMessage effectiveMessage = stampSender(Objects.requireNonNull(message, "message is required"));
        byte[] payload;
        try {
            payload = serializeMessage(effectiveMessage, agentId);
        } catch (JsonProcessingException jsonException) {
            return failedFuture(new IllegalArgumentException("Event message must be JSON serializable", jsonException));
        }
        return enqueue(commandFuture -> sendOnIoThread(agentId, payload, commandFuture));
    }

    /**
     * Send a request event and await a response addressed to its temporary reply route.
     *
     * @param agentId recipient identity
     * @param payload request payload
     * @param timeout response timeout; defaults to the configured request timeout
     * @return response payload future
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Map<String, Object>> sendAndWait(String agentId, Map<String, Object> payload,
            Duration timeout) {
        String requestId = UUID.randomUUID().toString();
        String replyTo = localNodeId + REPLY_ROUTE_MARKER + requestId;
        CompletableFuture<Map<String, Object>> response = new CompletableFuture<>();
        CompletableFuture<Void> registered = registerDirectHandler(
                replyTo, message -> completeResponseAfterUnregister(response, replyTo, message));
        Map<String, Object> requestPayload = copyPayload(payload);
        requestPayload.put("reply_to", replyTo);
        requestPayload.put("request_id", requestId);
        EventMessage request = EventMessage.builder().eventType("request").payload(requestPayload).build();
        registered.thenCompose(ignored -> send(agentId, request)).whenComplete((ignored, throwable) -> {
            if (throwable != null) {
                completeFailureAfterUnregister(response, replyTo, throwable);
            }
        });
        applyResponseTimeout(response, replyTo, timeout);
        return response;
    }

    /**
     * Register the local node's direct-message handler.
     *
     * @param handler asynchronous event handler
     * @return future completed after handler registration
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> registerDirectMessageHandler(MessagerHandler handler) {
        Objects.requireNonNull(handler, "handler is required");
        return registerDirectHandler(localNodeId, handler);
    }

    /**
     * Remove the local node's direct-message handler.
     *
     * @return future completed after handler removal
     * @since 0.1.7
     */
    @Override
    public CompletableFuture<Void> unregisterDirectMessageHandler() {
        return unregisterDirectHandler(localNodeId);
    }

    private void prepareIoLoop() {
        isRunning = true;
        isStopRequested = false;
        startFuture = new CompletableFuture<>();
        stopFuture = new CompletableFuture<>();
        String threadSuffix = THREAD_NAME_UNSAFE.matcher(localNodeId).replaceAll("-");
        handlerDispatcher = new TransportHandlerDispatcher(threadSuffix);
        ioExecutor = OpenJiuwenExecutors.newSingleThreadExecutor(
                "agent-team-pyzmq-" + threadSuffix, true);
        ioExecutor.execute(this::runIoLoop);
    }

    private void runIoLoop() {
        try {
            initializeSockets();
            startFuture.complete(null);
            while (!isStopRequested || shouldDrain()) {
                drainCommands();
                pollSockets();
                receivePendingAcknowledgements();
                expirePendingSends();
                driveSubscriptionReadiness();
            }
        } catch (ZMQException | IllegalArgumentException | IllegalStateException transportException) {
            startFuture.completeExceptionally(transportException);
            Loggers.AGENT.warn(
                    "PyZmq transport loop stopped unexpectedly for node {}", localNodeId, transportException);
        } finally {
            try {
                closeIoResources();
            } catch (ZMQException | IllegalStateException closeException) {
                Loggers.AGENT.warn(
                        "PyZmq transport resources did not close cleanly for node {}", localNodeId, closeException);
            } finally {
                finishIoLoop();
            }
        }
    }

    private boolean shouldDrain() {
        return System.nanoTime() < stopDeadlineNanos
                && (handlerDispatcher.hasPending() || !pendingSends.isEmpty() || !commandQueue.isEmpty());
    }

    private void initializeSockets() {
        context = new ZContext();
        context.setLinger(SOCKET_LINGER_MILLIS);
        poller = context.createPoller(POLLER_SOCKET_CAPACITY);
        initializeRouter();
        initializePubSub();
    }

    private void initializeRouter() {
        if (isBlank(config.getDirectAddr())) {
            return;
        }
        router = createSocket(SocketType.ROUTER);
        config.setDirectAddr(bindAndResolve(router, config.getDirectAddr(), "direct"));
        routerPollIndex = poller.register(router, ZMQ.Poller.POLLIN);
    }

    private String bindAndResolve(ZMQ.Socket socket, String endpoint, String endpointType) {
        if (endpoint.endsWith(":*")) {
            String baseEndpoint = endpoint.substring(0, endpoint.length() - 2);
            int port = socket.bindToRandomPort(baseEndpoint);
            if (port <= 0) {
                throw new IllegalStateException("Failed to bind random ZeroMQ " + endpointType + " endpoint");
            }
            return baseEndpoint + ":" + port;
        }
        if (!socket.bind(endpoint)) {
            throw new IllegalStateException("Failed to bind ZeroMQ " + endpointType + " endpoint");
        }
        String boundEndpoint = socket.getLastEndpoint();
        return isBlank(boundEndpoint) ? endpoint : boundEndpoint;
    }

    private void initializePubSub() {
        boolean hasPublishAddress = !isBlank(config.getPubsubPublishAddr());
        boolean hasSubscribeAddress = !isBlank(config.getPubsubSubscribeAddr());
        if (!hasPublishAddress && !hasSubscribeAddress) {
            return;
        }
        if (!hasPublishAddress || !hasSubscribeAddress) {
            throw new IllegalArgumentException("Both pubsub ZeroMQ endpoints are required");
        }
        if (isPubSubBroker()) {
            initializePubSubProxy();
        }
        publisher = createSocket(SocketType.PUB);
        subscriber = createSocket(SocketType.SUB);
        connectOrThrow(publisher, config.getPubsubPublishAddr());
        connectOrThrow(subscriber, config.getPubsubSubscribeAddr());
        subscriberPollIndex = poller.register(subscriber, ZMQ.Poller.POLLIN);
    }

    private void initializePubSubProxy() {
        proxyPublisher = createSocket(SocketType.XPUB);
        proxySubscriber = createSocket(SocketType.XSUB);
        config.setPubsubSubscribeAddr(bindAndResolve(proxyPublisher, config.getPubsubSubscribeAddr(), "subscription"));
        config.setPubsubPublishAddr(bindAndResolve(proxySubscriber, config.getPubsubPublishAddr(), "publication"));
        proxyPublisherPollIndex = poller.register(proxyPublisher, ZMQ.Poller.POLLIN);
        proxySubscriberPollIndex = poller.register(proxySubscriber, ZMQ.Poller.POLLIN);
    }

    private ZMQ.Socket createSocket(SocketType type) {
        ZMQ.Socket socket = context.createSocket(type);
        socket.setLinger(SOCKET_LINGER_MILLIS);
        return socket;
    }

    private void connectOrThrow(ZMQ.Socket socket, String endpoint) {
        if (!socket.connect(endpoint)) {
            throw new IllegalStateException("Failed to connect ZeroMQ endpoint");
        }
    }

    private void drainCommands() {
        for (int count = 0; count < COMMAND_QUEUE_CAPACITY; count++) {
            IoCommand command = commandQueue.poll();
            if (command == null) {
                break;
            }
            executeCommand(command);
        }
    }

    private void executeCommand(IoCommand command) {
        if (command.future().isDone()) {
            return;
        }
        try {
            command.action().execute(command.future());
        } catch (ZMQException | IllegalArgumentException | IllegalStateException transportException) {
            command.future().completeExceptionally(transportException);
        }
    }

    private void pollSockets() {
        if (poller.poll(POLL_TIMEOUT_MILLIS) <= 0) {
            return;
        }
        if (isReadable(routerPollIndex)) {
            receiveDirectMessage();
        }
        if (isReadable(subscriberPollIndex)) {
            receiveTopicMessage();
        }
        if (isReadable(proxySubscriberPollIndex)) {
            forwardMultipart(proxySubscriber, proxyPublisher);
        }
        if (isReadable(proxyPublisherPollIndex)) {
            forwardMultipart(proxyPublisher, proxySubscriber);
        }
    }

    private boolean isReadable(int pollIndex) {
        return pollIndex >= 0 && poller.pollin(pollIndex);
    }

    private void receiveDirectMessage() {
        ZMsg frames = ZMsg.recvMsg(router, ZMQ.DONTWAIT);
        if (frames == null || frames.size() < 2) {
            destroyFrames(frames);
            return;
        }
        try {
            byte[] identity = frames.getFirst().getData();
            byte[] payload = frames.getLast().getData();
            boolean isAccepted = !isStopRequested && handleDirectPayload(payload);
            router.sendMore(identity);
            router.send(isAccepted ? "ok" : "error:handler_unavailable");
        } finally {
            frames.destroy();
        }
    }

    private boolean handleDirectPayload(byte[] payload) {
        try {
            WireEvent wireEvent = deserializeMessage(payload);
            if (READINESS_REQUEST.equals(wireEvent.message().getEventType())) {
                return localNodeId.equals(wireEvent.recipientId())
                        && peers.containsKey(wireEvent.message().getSenderId())
                        && respondToSubscriptionProbe(wireEvent.message());
            }
            MessagerHandler handler = directHandlers.get(wireEvent.recipientId());
            return handler == null
                    || handlerDispatcher.dispatch(handler, wireEvent.message()) == DispatchResult.ACCEPTED;
        } catch (IOException ioException) {
            Loggers.AGENT.warn(
                    "PyZmq discarded an invalid direct message for node {}", localNodeId, ioException);
            return false;
        }
    }

    private void receiveTopicMessage() {
        ZMsg frames = ZMsg.recvMsg(subscriber, ZMQ.DONTWAIT);
        if (frames == null || frames.size() < 2) {
            destroyFrames(frames);
            return;
        }
        try {
            String topic = frames.getFirst().getString(StandardCharsets.UTF_8);
            byte[] payload = frames.getLast().getData();
            handleTopicPayload(topic, payload);
        } finally {
            frames.destroy();
        }
    }

    private void handleTopicPayload(String topic, byte[] payload) {
        MessagerHandler handler = topicHandlers.get(topic);
        if (handler == null) {
            return;
        }
        if (isStopRequested) {
            recordTopicDrop(DispatchResult.CLOSED);
            return;
        }
        try {
            EventMessage message = deserializeMessage(payload).message();
            if (READINESS_EVENT.equals(message.getEventType())) {
                completeSubscriptionReadiness(topic, message);
                return;
            }
            DispatchResult result = handlerDispatcher.dispatch(handler, message);
            if (result != DispatchResult.ACCEPTED) {
                recordTopicDrop(result);
            }
        } catch (IOException ioException) {
            Loggers.AGENT.warn("PyZmq discarded an invalid topic message on {}", topic, ioException);
        }
    }

    private void recordTopicDrop(DispatchResult reason) {
        long count = topicDropCounts.computeIfAbsent(reason, ignored -> new AtomicLong()).incrementAndGet();
        Loggers.AGENT.warn("PyZmq topic message dropped for node {}, reason={}, dropped_count={}",
                localNodeId, reason, count);
    }

    private void forwardMultipart(ZMQ.Socket source, ZMQ.Socket target) {
        ZMsg frames = ZMsg.recvMsg(source, ZMQ.DONTWAIT);
        if (frames == null) {
            return;
        }
        try {
            if (!frames.send(target, false)) {
                throw new IllegalStateException("Failed to forward ZeroMQ multipart message");
            }
        } finally {
            frames.destroy();
        }
    }

    private void receivePendingAcknowledgements() {
        Iterator<PendingSend> iterator = pendingSends.iterator();
        while (iterator.hasNext()) {
            PendingSend pending = iterator.next();
            byte[] acknowledgement = pending.socket().recv(ZMQ.DONTWAIT);
            if (acknowledgement == null) {
                continue;
            }
            closeSocket(pending.socket());
            if (new String(acknowledgement, StandardCharsets.UTF_8).startsWith("error:")) {
                pending.future().completeExceptionally(new IllegalStateException(
                        "ZeroMQ peer rejected delivery: " + pending.diagnostic()));
            } else {
                pending.future().complete(null);
            }
            iterator.remove();
        }
    }

    private void expirePendingSends() {
        long now = System.nanoTime();
        Iterator<PendingSend> iterator = pendingSends.iterator();
        while (iterator.hasNext()) {
            PendingSend pending = iterator.next();
            if (now < pending.deadlineNanos() && !pending.future().isDone()) {
                continue;
            }
            closeSocket(pending.socket());
            pending.future().completeExceptionally(
                    new TimeoutException("Timed out waiting for ZeroMQ transport acknowledgement: "
                            + pending.diagnostic()));
            iterator.remove();
        }
    }

    private void publishOnIoThread(String topicId, byte[] payload) {
        if (publisher == null) {
            throw new IllegalStateException("Pub/sub ZeroMQ endpoints are not configured");
        }
        if (!publisher.sendMore(topicId) || !publisher.send(payload)) {
            throw new IllegalStateException("Failed to publish ZeroMQ event");
        }
        Loggers.AGENT.debug("PyZmq published event on topic {} from node {}", topicId, localNodeId);
    }

    private void subscribeOnIoThread(String topicId, MessagerHandler handler) {
        if (subscriber == null) {
            throw new IllegalStateException("Pub/sub ZeroMQ endpoints are not configured");
        }
        boolean isFirstHandler = !topicHandlers.containsKey(topicId);
        topicHandlers.put(topicId, handler);
        if (isFirstHandler && !subscriber.subscribe(topicId)) {
            topicHandlers.remove(topicId);
            throw new IllegalStateException("Failed to subscribe to ZeroMQ topic");
        }
        Loggers.AGENT.debug("PyZmq subscribed node {} to topic {}", localNodeId, topicId);
    }

    private void unsubscribeOnIoThread(String topicId) {
        PendingSubscription pending = pendingSubscriptions.remove(topicId);
        if (pending != null) {
            pending.requests.values().forEach(request -> request.cancel(false));
            pending.future.completeExceptionally(new IllegalStateException("ZeroMQ subscription was removed"));
        }
        if (topicHandlers.remove(topicId) != null && subscriber != null) {
            subscriber.unsubscribe(topicId);
        }
    }

    private void sendOnIoThread(String agentId, byte[] payload, CompletableFuture<Void> result) {
        MessagerPeerConfig peer = resolvePeer(agentId);
        ZMQ.Socket dealer = createSocket(SocketType.DEALER);
        String messageId = UUID.randomUUID().toString();
        try {
            String identity = localNodeId + ":" + messageId;
            dealer.setIdentity(identity.getBytes(StandardCharsets.UTF_8));
            connectOrThrow(dealer, peer.getAddrs().get(0));
            if (!dealer.send(payload)) {
                throw new IllegalStateException("Failed to send ZeroMQ direct event");
            }
        } catch (ZMQException | IllegalArgumentException | IllegalStateException transportException) {
            throw closeFailedDealer(dealer, transportException);
        }
        long timeoutMillis = requestTimeoutMillis();
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
        pendingSends.add(new PendingSend(dealer, result, deadline, "phase=ack, message=" + messageId
                + ", from=" + safeNodeId(localNodeId) + ", to=" + safeNodeId(agentId)));
        Loggers.AGENT.debug("PyZmq sent direct event from {} to {}", localNodeId, agentId);
    }

    private IllegalStateException closeFailedDealer(ZMQ.Socket dealer, RuntimeException transportException) {
        IllegalStateException sendFailure = new IllegalStateException(
                "Failed to prepare ZeroMQ direct event", transportException);
        try {
            closeSocket(dealer);
        } catch (ZMQException | IllegalStateException closeException) {
            sendFailure.addSuppressed(closeException);
        }
        return sendFailure;
    }

    private CompletableFuture<Void> registerDirectHandler(String routeId, MessagerHandler handler) {
        Objects.requireNonNull(handler, "handler is required");
        return enqueueImmediate(() -> {
            if (router == null) {
                throw new IllegalStateException("A direct ZeroMQ endpoint is required for handler registration");
            }
            directHandlers.put(routeId, handler);
        });
    }

    private CompletableFuture<Void> unregisterDirectHandler(String routeId) {
        CompletableFuture<Void> result = new CompletableFuture<>();
        synchronized (lifecycleLock) {
            if (!isRunning || isStopRequested) {
                return CompletableFuture.completedFuture(null);
            }
            IoAction action = commandFuture -> {
                directHandlers.remove(routeId);
                commandFuture.complete(null);
            };
            if (!commandQueue.offer(new IoCommand(action, result))) {
                result.completeExceptionally(new IllegalStateException("PyZmq transport command queue is full"));
            }
        }
        return result;
    }

    private CompletableFuture<Void> completeResponseAfterUnregister(
            CompletableFuture<Map<String, Object>> response, String replyTo, EventMessage message) {
        Map<String, Object> responsePayload = copyPayload(message.getPayload());
        return unregisterDirectHandler(replyTo).whenComplete((ignored, cleanupFailure) -> {
            if (cleanupFailure != null) {
                response.completeExceptionally(cleanupFailure);
                return;
            }
            response.complete(responsePayload);
        });
    }

    private void completeFailureAfterUnregister(
            CompletableFuture<Map<String, Object>> response, String replyTo, Throwable requestFailure) {
        CompletableFuture<Void> cleanup = unregisterDirectHandler(replyTo);
        cleanup.whenComplete((ignored, cleanupFailure) -> {
            if (cleanupFailure != null && cleanupFailure != requestFailure) {
                requestFailure.addSuppressed(cleanupFailure);
            }
            response.completeExceptionally(requestFailure);
        });
    }

    private CompletableFuture<Void> enqueueImmediate(Runnable action) {
        return enqueue(commandFuture -> {
            action.run();
            commandFuture.complete(null);
        });
    }

    private CompletableFuture<Void> enqueue(IoAction action) {
        synchronized (lifecycleLock) {
            if (isStopRequested) {
                return failedFuture(new IllegalStateException("PyZmq transport stopped; call start() explicitly"));
            }
            CompletableFuture<Void> result = new CompletableFuture<>();
            start().whenComplete((ignored, throwable) -> enqueueAfterStart(action, result, throwable));
            return result;
        }
    }

    private void enqueueAfterStart(IoAction action, CompletableFuture<Void> result, Throwable startFailure) {
        synchronized (lifecycleLock) {
            if (startFailure != null) {
                result.completeExceptionally(startFailure);
                return;
            }
            if (!isRunning || isStopRequested) {
                result.completeExceptionally(new IllegalStateException("PyZmq transport is stopping"));
                return;
            }
            if (!commandQueue.offer(new IoCommand(action, result))) {
                result.completeExceptionally(new IllegalStateException("PyZmq transport command queue is full"));
            }
        }
    }

    private void applyResponseTimeout(CompletableFuture<Map<String, Object>> response, String replyTo,
            Duration timeout) {
        long timeoutMillis = timeout != null ? Math.max(1L, timeout.toMillis()) : requestTimeoutMillis();
        response.orTimeout(timeoutMillis, TimeUnit.MILLISECONDS).whenComplete((ignored, throwable) -> {
            if (throwable instanceof TimeoutException) {
                completeFailureAfterUnregister(response, replyTo, throwable);
            }
        });
    }

    private long requestTimeoutMillis() {
        return Math.max(1L, Math.round(config.getRequestTimeout() * 1_000.0d));
    }

    private MessagerPeerConfig resolvePeer(String routeId) {
        MessagerPeerConfig peer = peers.get(routeId);
        if (peer == null) {
            int markerIndex = routeId.indexOf(REPLY_ROUTE_MARKER);
            if (markerIndex > 0) {
                peer = peers.get(routeId.substring(0, markerIndex));
            }
        }
        if (peer == null || peer.getAddrs() == null || peer.getAddrs().isEmpty()) {
            throw new IllegalArgumentException("Unknown ZeroMQ route for recipient: " + routeId);
        }
        return peer;
    }

    private void registerInitialPeers(List<MessagerPeerConfig> initialPeers) {
        if (initialPeers == null) {
            return;
        }
        for (MessagerPeerConfig peer : initialPeers) {
            registerPeer(peer);
        }
    }

    private void closeIoResources() {
        List<RuntimeException> closeFailures = new ArrayList<>();
        failOutstandingOperations(closeFailures);
        closeSocketSafely(router, closeFailures);
        closeSocketSafely(publisher, closeFailures);
        closeSocketSafely(subscriber, closeFailures);
        closeSocketSafely(proxyPublisher, closeFailures);
        closeSocketSafely(proxySubscriber, closeFailures);
        closePollerSafely(closeFailures);
        closeContextSafely(closeFailures);
        clearIoFields();
        handlerDispatcher.close(stopDeadlineNanos);
        throwCloseFailure(closeFailures);
    }

    private void failOutstandingOperations(List<RuntimeException> closeFailures) {
        IllegalStateException stopped = new IllegalStateException("PyZmq transport stopped before completion");
        IoCommand command;
        while ((command = commandQueue.poll()) != null) {
            command.future().completeExceptionally(stopped);
        }
        for (PendingSend pending : pendingSends) {
            closeSocketSafely(pending.socket(), closeFailures);
            pending.future().completeExceptionally(stopped);
        }
        pendingSends.clear();
        pendingSubscriptions.values().forEach(pending -> pending.future.completeExceptionally(stopped));
        pendingSubscriptions.clear();
    }

    private void closeSocketSafely(ZMQ.Socket socket, List<RuntimeException> closeFailures) {
        try {
            closeSocket(socket);
        } catch (ZMQException | IllegalStateException exception) {
            closeFailures.add(exception);
        }
    }

    private void closePollerSafely(List<RuntimeException> closeFailures) {
        if (poller == null) {
            return;
        }
        try {
            poller.close();
        } catch (ZMQException | IllegalStateException exception) {
            closeFailures.add(exception);
        }
    }

    private void closeContextSafely(List<RuntimeException> closeFailures) {
        if (context == null) {
            return;
        }
        try {
            context.close();
        } catch (ZMQException | IllegalStateException exception) {
            closeFailures.add(exception);
        }
    }

    private void throwCloseFailure(List<RuntimeException> closeFailures) {
        if (closeFailures.isEmpty()) {
            return;
        }
        IllegalStateException closeFailure = new IllegalStateException(
                "Failed to close one or more PyZmq transport resources", closeFailures.get(0));
        for (int index = 1; index < closeFailures.size(); index++) {
            closeFailure.addSuppressed(closeFailures.get(index));
        }
        throw closeFailure;
    }

    private void clearIoFields() {
        router = null;
        publisher = null;
        subscriber = null;
        proxyPublisher = null;
        proxySubscriber = null;
        poller = null;
        context = null;
        routerPollIndex = -1;
        subscriberPollIndex = -1;
        proxyPublisherPollIndex = -1;
        proxySubscriberPollIndex = -1;
        topicHandlers.clear();
        directHandlers.clear();
    }

    private void finishIoLoop() {
        synchronized (lifecycleLock) {
            ExecutorService completedExecutor = ioExecutor;
            ioExecutor = null;
            isRunning = false;
            isStopRequested = true;
            if (!startFuture.isDone()) {
                startFuture.completeExceptionally(new IllegalStateException("PyZmq transport failed to start"));
            }
            if (completedExecutor != null) {
                completedExecutor.shutdown();
            }
            stopFuture.complete(null);
        }
    }

    private void closeSocket(ZMQ.Socket socket) {
        if (socket != null) {
            socket.setLinger(SOCKET_LINGER_MILLIS);
            socket.close();
        }
    }

    private EventMessage stampSender(EventMessage message) {
        if (!isBlank(message.getSenderId())) {
            return message;
        }
        return message.toBuilder().senderId(localNodeId).build();
    }

    private boolean isPubSubBroker() {
        return config.getMetadata() != null && Boolean.TRUE.equals(config.getMetadata().get("pubsub_bind"));
    }

    private void subscribeWithReadiness(String topicId, MessagerHandler handler, CompletableFuture<Void> result) {
        subscribeOnIoThread(topicId, handler);
        PendingSubscription previous = pendingSubscriptions.remove(topicId);
        if (previous != null) {
            previous.requests.values().forEach(request -> request.cancel(false));
            previous.future.completeExceptionally(new IllegalStateException("ZeroMQ subscription was replaced"));
        }
        String nonce = UUID.randomUUID().toString();
        EventMessage probe = EventMessage.builder().eventType(READINESS_EVENT).senderId(localNodeId)
                .payload(Map.of("nonce", nonce, "subscriber", localNodeId)).build();
        try {
            byte[] payload = serializeMessage(probe, null);
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(requestTimeoutMillis());
            Set<String> publishers = new LinkedHashSet<>();
            publishers.add(localNodeId);
            peers.values().stream().filter(peer -> peer.getMetadata() != null
                    && Boolean.TRUE.equals(peer.getMetadata().get("readiness_publisher")))
                    .map(MessagerPeerConfig::getAgentId).forEach(publishers::add);
            pendingSubscriptions.put(topicId, new PendingSubscription(nonce, payload, deadline, result, publishers));
        } catch (JsonProcessingException failure) {
            result.completeExceptionally(new IllegalStateException("Cannot encode ZeroMQ readiness probe", failure));
        }
    }

    private void driveSubscriptionReadiness() {
        long now = System.nanoTime();
        Iterator<Map.Entry<String, PendingSubscription>> iterator = pendingSubscriptions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PendingSubscription> entry = iterator.next();
            PendingSubscription pending = entry.getValue();
            pending.publishers.removeIf(peer -> !localNodeId.equals(peer) && !peers.containsKey(peer));
            if (pending.publishers.isEmpty()) {
                pending.future.complete(null);
            }
            if (pending.future.isDone()) {
                pending.requests.values().forEach(request -> request.cancel(false));
                if (pending.future.isCompletedExceptionally()) {
                    topicHandlers.remove(entry.getKey());
                    subscriber.unsubscribe(entry.getKey());
                }
                iterator.remove();
                continue;
            }
            if (now >= pending.deadlineNanos) {
                pending.future.completeExceptionally(new TimeoutException("ZeroMQ subscription did not become ready"));
                pending.requests.values().forEach(request -> request.cancel(false));
                iterator.remove();
                topicHandlers.remove(entry.getKey());
                subscriber.unsubscribe(entry.getKey());
                continue;
            }
            if (now >= pending.nextProbeNanos && !isStopRequested) {
                sendSubscriptionProbes(entry.getKey(), pending);
                pending.nextProbeNanos = now + READINESS_PROBE_INTERVAL_NANOS;
            }
        }
    }

    private void sendSubscriptionProbes(String topic, PendingSubscription pending) {
        if (pending.publishers.contains(localNodeId)) {
            publishOnIoThread(topic, pending.payload);
        }
        for (String peer : List.copyOf(pending.publishers)) {
            if (!localNodeId.equals(peer)) {
                requestSubscriptionProbe(peer, topic, pending);
            }
        }
    }

    private void completeSubscriptionReadiness(String topic, EventMessage message) {
        PendingSubscription pending = pendingSubscriptions.get(topic);
        if (pending != null && localNodeId.equals(message.getPayload().get("subscriber"))
                && pending.nonce.equals(message.getPayload().get("nonce"))) {
            pending.publishers.remove(message.getSenderId());
            if (pending.publishers.isEmpty()) {
                pendingSubscriptions.remove(topic);
                pending.requests.values().forEach(request -> request.cancel(false));
                pending.future.complete(null);
            }
        }
    }

    private void requestSubscriptionProbe(String peer, String topic, PendingSubscription pending) {
        CompletableFuture<Void> previous = pending.requests.get(peer);
        if (previous != null && !previous.isDone()) {
            return;
        }
        CompletableFuture<Void> acknowledgement = new CompletableFuture<>();
        pending.requests.put(peer, acknowledgement);
        acknowledgement.whenComplete((ignored, failure) -> {
            if (failure != null && !acknowledgement.isCancelled()) {
                pending.future.completeExceptionally(failure);
            }
        });
        EventMessage request = EventMessage.builder().eventType(READINESS_REQUEST).senderId(localNodeId)
                .payload(Map.of("nonce", pending.nonce, "subscriber", localNodeId, "topic", topic)).build();
        try {
            sendOnIoThread(peer, serializeMessage(request, peer), acknowledgement);
        } catch (JsonProcessingException | IllegalArgumentException | IllegalStateException | ZMQException failure) {
            acknowledgement.completeExceptionally(failure);
        }
    }

    private boolean respondToSubscriptionProbe(EventMessage request) throws JsonProcessingException {
        Map<String, Object> payload = request.getPayload();
        if (publisher == null || payload == null) {
            return false;
        }
        Object nonce = payload.get("nonce");
        Object subscriberId = payload.get("subscriber");
        Object topic = payload.get("topic");
        if (!(nonce instanceof String) || !(subscriberId instanceof String) || !(topic instanceof String topicId)) {
            return false;
        }
        EventMessage probe = EventMessage.builder().eventType(READINESS_EVENT).senderId(localNodeId)
                .payload(Map.of("nonce", nonce, "subscriber", subscriberId)).build();
        publishOnIoThread(topicId, serializeMessage(probe, null));
        return true;
    }

    private static final class PendingSubscription {
        private final String nonce;
        private final byte[] payload;
        private final long deadlineNanos;
        private final CompletableFuture<Void> future;
        private final Set<String> publishers;
        private final Map<String, CompletableFuture<Void>> requests = new LinkedHashMap<>();
        private long nextProbeNanos;

        private PendingSubscription(String nonce, byte[] payload, long deadlineNanos,
                CompletableFuture<Void> future, Set<String> publishers) {
            this.nonce = nonce;
            this.payload = payload;
            this.deadlineNanos = deadlineNanos;
            this.future = future;
            this.publishers = publishers;
        }
    }

    private static byte[] serializeMessage(EventMessage message, String recipientId)
            throws JsonProcessingException {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("event_type", message.getEventType());
        wire.put("payload", copyPayload(message.getPayload()));
        wire.put("sender_id", message.getSenderId());
        if (recipientId != null) {
            wire.put("_recipient_id", recipientId);
        }
        return OBJECT_MAPPER.writeValueAsBytes(wire);
    }

    private static WireEvent deserializeMessage(byte[] payload) throws IOException {
        Map<String, Object> wire = OBJECT_MAPPER.readValue(payload, MAP_TYPE);
        String eventType = stringValue(wire.getOrDefault("event_type", wire.get("eventType")));
        String senderId = stringValue(wire.getOrDefault("sender_id", wire.get("senderId")));
        String recipientId = stringValue(wire.get("_recipient_id"));
        Map<String, Object> eventPayload = new LinkedHashMap<>();
        if (wire.get("payload") instanceof Map<?, ?> rawPayload) {
            eventPayload = stringifyMap(rawPayload);
        }
        EventMessage message = EventMessage.builder()
                .eventType(eventType)
                .payload(eventPayload)
                .senderId(senderId)
                .build();
        return new WireEvent(recipientId, message);
    }

    private static Map<String, Object> stringifyMap(Map<?, ?> rawPayload) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawPayload.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static Map<String, Object> copyPayload(Map<String, Object> payload) {
        return payload != null ? new LinkedHashMap<>(payload) : new LinkedHashMap<>();
    }

    private static void validatePeer(MessagerPeerConfig peer) {
        if (peer == null || isBlank(peer.getAgentId())) {
            throw new IllegalArgumentException("peer agentId is required");
        }
        if (peer.getAddrs() == null || peer.getAddrs().isEmpty() || isBlank(peer.getAddrs().get(0))) {
            throw new IllegalArgumentException("peer endpoint is required");
        }
    }

    private static MessagerPeerConfig copyPeer(MessagerPeerConfig peer) {
        return MessagerPeerConfig.builder()
                .agentId(peer.getAgentId())
                .peerId(peer.getPeerId())
                .addrs(new ArrayList<>(peer.getAddrs()))
                .metadata(peer.getMetadata() != null
                        ? new LinkedHashMap<>(peer.getMetadata())
                        : new LinkedHashMap<>())
                .build();
    }

    private static void destroyFrames(ZMsg frames) {
        if (frames != null) {
            frames.destroy();
        }
    }

    private static String resolveLocalNodeId(String configuredNodeId) {
        return isBlank(configuredNodeId) ? UUID.randomUUID().toString() : configuredNodeId;
    }

    private static String safeNodeId(String nodeId) {
        String bounded = nodeId.substring(0, Math.min(nodeId.length(), 64));
        return THREAD_NAME_UNSAFE.matcher(bounded).replaceAll("-");
    }

    private static String stringValue(Object value) {
        return value != null ? String.valueOf(value) : "";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static <T> CompletableFuture<T> failedFuture(Throwable throwable) {
        return CompletableFuture.failedFuture(throwable);
    }

    @FunctionalInterface
    private interface IoAction {
        /**
         * Executes an action on the transport I/O thread.
         *
         * @param future future completed with the action result
         */
        void execute(CompletableFuture<Void> future);
    }

    private record IoCommand(IoAction action, CompletableFuture<Void> future) {
    }

    private record PendingSend(ZMQ.Socket socket, CompletableFuture<Void> future, long deadlineNanos,
            String diagnostic) {
    }

    private record WireEvent(String recipientId, EventMessage message) {
    }
}
