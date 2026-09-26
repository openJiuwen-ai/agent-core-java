/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Application-scoped executor for Session-level KV-cache actions.
 *
 * <p>Mirrors Python's {@code KVCacheRuntime} in
 * {@code openjiuwen/core/kv_cache/kv_cache_runtime.py}. Two orthogonal state
 * machines drive scheduling: binding-level {@code residency} (where the cache
 * physically is) decides whether a prefetch is needed, and action-scope-level
 * {@code admission} (open/blocked/terminal) decides whether inference must
 * prepare first. Every failure is fail-open: cache management never blocks or
 * fails business inference.</p>
 *
 * <p>Conventions translated from asyncio: each pending action runs on a
 * bounded dedicated executor; the dependency chain of same-key and
 * root-child actions is expressed with {@code CompletableFuture.allOf};
 * idle waiting uses the shared lock's condition variable.</p>
 *
 * @since 0.1.16
 */
public class KVCacheRuntime implements KVCacheTypes.KVCacheRuntimeProtocol {
    private static final System.Logger LOG = System.getLogger(KVCacheRuntime.class.getName());
    private static final int ACTION_CORE_POOL_SIZE = 1;
    private static final int ACTION_MAX_POOL_SIZE = 2;
    private static final long ACTION_KEEP_ALIVE_SECONDS = 60L;
    private static final int ACTION_QUEUE_CAPACITY = 512;

    private final KVCacheConfig config;
    private final Supplier<Object> bindingProvider;
    private final Executor actionExecutor;
    private final ScheduledExecutorService timeoutScheduler;
    private final boolean isOwnsExecutors;

    private final ReentrantLock lock = new ReentrantLock();
    private final Condition stateChanged = lock.newCondition();
    private final Map<ActionKey, BindingState> bindings = new LinkedHashMap<>();
    private final Map<ActionKey, Set<ActionKey>> rootIndex = new LinkedHashMap<>();
    private final Map<ActionKey, ActionState> actionStates = new LinkedHashMap<>();
    private final Map<CompletableFuture<?>, Boolean> tasks = new ConcurrentHashMap<>();
    private boolean isClosed;

    /**
     * Create a runtime with default budgets and a private action executor.
     *
     * @param bindingProvider cold-session fallback model provider, may be {@code null}
     */
    public KVCacheRuntime(Supplier<Object> bindingProvider) {
        this(new KVCacheConfig(), bindingProvider, null, null);
    }

    /**
     * Create a runtime with explicit budgets and a private action executor.
     *
     * @param config scheduling budgets
     * @param bindingProvider fallback model provider, may be {@code null}
     */
    public KVCacheRuntime(KVCacheConfig config, Supplier<Object> bindingProvider) {
        this(config, bindingProvider, null, null);
    }

    /**
     * Create a runtime with explicit budgets and optional injected executors
     * (tests inject deterministic executors here).
     *
     * @param config scheduling budgets
     * @param bindingProvider fallback model provider, may be {@code null}
     * @param actionExecutor executor for background actions, {@code null} creates default
     * @param timeoutScheduler scheduler for action timeouts, {@code null} creates default
     */
    public KVCacheRuntime(KVCacheConfig config, Supplier<Object> bindingProvider,
                          Executor actionExecutor, ScheduledExecutorService timeoutScheduler) {
        this.config = config == null ? new KVCacheConfig() : config;
        this.bindingProvider = bindingProvider;
        this.isOwnsExecutors = actionExecutor == null || timeoutScheduler == null;
        this.actionExecutor = actionExecutor != null
                ? actionExecutor
                : namedDaemonExecutor("kvc-action");
        this.timeoutScheduler = timeoutScheduler != null
                ? timeoutScheduler
                : Executors.newSingleThreadScheduledExecutor(namedDaemonFactory("kvc-timeout"));
    }

    /** State of one action scope. */
    static final class ActionState {
        private int activeInferenceCount;
        private KVCacheTypes.Admission admission = KVCacheTypes.Admission.OPEN;
        private PendingAction pendingAction;
        private CompletableFuture<Boolean> actionTail;
        private boolean isFailOpen;
    }

    /** One queued management action. */
    static final class PendingAction {
        private final KVCacheTypes.ActionKind kind;
        private final CompletableFuture<Boolean> task;
        private final ProviderCallSignal providerCallStarted = new ProviderCallSignal();

        PendingAction(KVCacheTypes.ActionKind kind, CompletableFuture<Boolean> task) {
            this.kind = kind;
            this.task = task;
        }
    }

    /** Provider-call handshake signal for one pending action. */
    static final class ProviderCallSignal {
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition condition = lock.newCondition();
        private boolean isStarted;

        void markStarted() {
            lock.lock();
            try {
                isStarted = true;
                condition.signalAll();
            } finally {
                lock.unlock();
            }
        }

        boolean isStarted() {
            lock.lock();
            try {
                return isStarted;
            } finally {
                lock.unlock();
            }
        }

        boolean awaitStarted(long timeoutMillis) {
            long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            lock.lock();
            try {
                while (!isStarted) {
                    long remaining = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
                    if (remaining <= 0) {
                        return false;
                    }
                    try {
                        condition.await(remaining, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        // Cooperative waiter: treat interruption as a timeout
                        // and let the caller decide, without re-interrupting.
                        return false;
                    }
                }
                return true;
            } finally {
                lock.unlock();
            }
        }
    }

    private static Executor namedDaemonExecutor(String prefix) {
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                ACTION_CORE_POOL_SIZE,
                ACTION_MAX_POOL_SIZE,
                ACTION_KEEP_ALIVE_SECONDS,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(ACTION_QUEUE_CAPACITY),
                namedDaemonFactory(prefix));
        executor.allowCoreThreadTimeOut(true);
        return executor;
    }

    private static ThreadFactory namedDaemonFactory(String prefix) {
        return new NamedDaemonThreadFactory(prefix);
    }

    private static final class NamedDaemonThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger counter = new AtomicInteger();

        private NamedDaemonThreadFactory(String prefix) {
            this.prefix = prefix;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-" + counter.incrementAndGet());
            thread.setDaemon(true);
            thread.setUncaughtExceptionHandler(KVCacheRuntime::logUncaughtException);
            return thread;
        }
    }

    private static void logUncaughtException(Thread thread, Throwable throwable) {
        LOG.log(System.Logger.Level.WARNING, "Uncaught exception in {0}: {1}",
                thread.getName(), throwable.toString());
    }

    /**
     * Whether this runtime has been closed and no longer admits work.
     *
     * @return true when {@link #close()} has completed
     */
    public boolean isClosed() {
        lock.lock();
        try {
            return isClosed;
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<KVCacheTypes.InferenceLease> beginInference(
            KVCacheIdentity identity, Object model, String modelName) {
        return registerBinding(identity, model, modelName, false)
                .thenCompose(binding -> {
                    if (binding == null) {
                        return CompletableFuture.<KVCacheTypes.InferenceLease>completedFuture(null);
                    }
                    return beginInferenceAfterBinding(identity, binding);
                });
    }

    private CompletableFuture<KVCacheTypes.InferenceLease> beginInferenceAfterBinding(
            KVCacheIdentity identity, BindingState binding) {
        ActionKey childKey = ActionKey.bindingKey(identity.cacheId(), binding.controlDomain());
        ActionKey rootKey = ActionKey.rootKey(identity.parentCacheId(), binding.controlDomain());
        // tryAcquireOnce already contains the Python two-pass semantics: when
        // a scope is BLOCKED it prepares the keys first, then either acquires
        // or fails open. A single call therefore never double-counts.
        return tryAcquireOnce(childKey, rootKey);
    }

    private CompletableFuture<KVCacheTypes.InferenceLease> tryAcquireOnce(ActionKey childKey, ActionKey rootKey) {
        List<ActionKey> blocked = new ArrayList<>();
        lock.lock();
        try {
            if (isClosed || isTerminal(childKey, rootKey)) {
                return CompletableFuture.completedFuture(null);
            }
            collectBlocked(blocked, childKey, rootKey);
            if (blocked.isEmpty()) {
                return leaseNow(childKey, rootKey);
            }
        } finally {
            lock.unlock();
        }
        return prepareBlockedKeys(blocked)
                .thenCompose(prepared -> {
                    if (!prepared) {
                        return failOpen(childKey, rootKey);
                    }
                    return recheckAfterPrepare(childKey, rootKey);
                });
    }

    private CompletableFuture<KVCacheTypes.InferenceLease> recheckAfterPrepare(
            ActionKey childKey, ActionKey rootKey) {
        lock.lock();
        try {
            if (isClosed || isTerminal(childKey, rootKey)) {
                return CompletableFuture.completedFuture(null);
            }
            List<ActionKey> stillBlocked = new ArrayList<>();
            collectBlocked(stillBlocked, childKey, rootKey);
            if (stillBlocked.isEmpty()) {
                return leaseNow(childKey, rootKey);
            }
        } finally {
            lock.unlock();
        }
        return failOpen(childKey, rootKey);
    }

    private boolean isTerminal(ActionKey childKey, ActionKey rootKey) {
        return state(childKey).admission == KVCacheTypes.Admission.TERMINAL
                || state(rootKey).admission == KVCacheTypes.Admission.TERMINAL;
    }

    private void collectBlocked(List<ActionKey> blocked, ActionKey childKey, ActionKey rootKey) {
        if (state(rootKey).admission == KVCacheTypes.Admission.BLOCKED) {
            blocked.add(rootKey);
        }
        if (state(childKey).admission == KVCacheTypes.Admission.BLOCKED) {
            blocked.add(childKey);
        }
    }

    private CompletableFuture<KVCacheTypes.InferenceLease> leaseNow(ActionKey childKey, ActionKey rootKey) {
        state(childKey).activeInferenceCount++;
        state(rootKey).activeInferenceCount++;
        return CompletableFuture.completedFuture(new KVCacheTypes.InferenceLease(childKey, rootKey));
    }

    private CompletableFuture<Boolean> prepareBlockedKeys(List<ActionKey> blocked) {
        CompletableFuture<Boolean> current = CompletableFuture.completedFuture(true);
        for (ActionKey key : blocked) {
            current = current.thenCompose(prepared -> prepareKey(key).thenCombine(
                    CompletableFuture.completedFuture(prepared),
                    Boolean::logicalAnd));
        }
        return current;
    }

    private CompletableFuture<KVCacheTypes.InferenceLease> failOpen(ActionKey childKey, ActionKey rootKey) {
        lock.lock();
        try {
            for (ActionKey key : List.of(rootKey, childKey)) {
                ActionState state = state(key);
                if (state.admission != KVCacheTypes.Admission.TERMINAL) {
                    state.admission = KVCacheTypes.Admission.OPEN;
                    state.isFailOpen = true;
                }
            }
            stateChanged.signalAll();
            if (isClosed) {
                return CompletableFuture.completedFuture(null);
            }
            return leaseNow(childKey, rootKey);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Void> endInference(KVCacheTypes.InferenceLease lease, boolean isSucceeded) {
        if (lease == null || lease.isReleased()) {
            return CompletableFuture.completedFuture(null);
        }
        lease.markReleased();
        lock.lock();
        try {
            for (ActionKey key : List.of(lease.childKey(), lease.rootKey())) {
                ActionState state = actionStates.get(key);
                if (state == null) {
                    continue;
                }
                state.activeInferenceCount = Math.max(0, state.activeInferenceCount - 1);
            }
            BindingState binding = bindings.get(
                    ActionKey.bindingKey(lease.childKey().cacheId(), lease.childKey().controlDomain()));
            if (binding != null) {
                binding.setResidency(isSucceeded
                        ? BindingState.Residency.RESIDENT
                        : BindingState.Residency.UNKNOWN);
            }
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Boolean> prepare(KVCacheIdentity identity) {
        return identityActionKeys(identity)
                .thenCompose(keys -> {
                    if (keys.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    return prepareActionKeys(keys)
                            .thenCompose(ordered -> {
                                CompletableFuture<Boolean> current = CompletableFuture.completedFuture(false);
                                for (ActionKey key : ordered) {
                                    ActionKey captured = key;
                                    current = current.thenCompose(anyPrepared ->
                                            prepareKey(captured).thenApply(result -> result || anyPrepared));
                                }
                                return current;
                            });
                });
    }

    @Override
    public CompletableFuture<Boolean> suspend(KVCacheIdentity identity) {
        return identityActionKeys(identity)
                .thenCompose(keys -> {
                    if (keys.isEmpty()) {
                        return CompletableFuture.completedFuture(false);
                    }
                    CompletableFuture<Boolean> current = CompletableFuture.completedFuture(false);
                    for (ActionKey key : keys) {
                        ActionKey captured = key;
                        current = current.thenCompose(scheduled -> suspendKey(captured)
                                .thenApply(keyScheduled -> keyScheduled || scheduled));
                    }
                    return current;
                });
    }

    private CompletableFuture<Boolean> suspendKey(ActionKey key) {
        lock.lock();
        try {
            ActionState state = state(key);
            if (state.admission == KVCacheTypes.Admission.TERMINAL) {
                return CompletableFuture.completedFuture(false);
            }
            if (state.isFailOpen) {
                state.admission = KVCacheTypes.Admission.OPEN;
                stateChanged.signalAll();
                return CompletableFuture.completedFuture(false);
            }
            List<BindingState> boundBindings = bindingsForActionLocked(key);
            boolean isAlreadyOffloaded = !boundBindings.isEmpty() && boundBindings.stream()
                    .allMatch(binding -> binding.residency() == BindingState.Residency.OFFLOADED);
            if (state.admission == KVCacheTypes.Admission.BLOCKED
                    && state.pendingAction == null
                    && isAlreadyOffloaded) {
                return CompletableFuture.completedFuture(false);
            }
            state.admission = KVCacheTypes.Admission.BLOCKED;
            PendingAction pending = enqueueLocked(key, KVCacheTypes.ActionKind.OFFLOAD);
            return CompletableFuture.completedFuture(pending != null);
        } finally {
            lock.unlock();
        }
    }

    @Override
    public CompletableFuture<Boolean> release(KVCacheIdentity identity) {
        return identityActionKeys(identity)
                .thenCompose(keys -> {
                    List<CompletableFuture<Boolean>> actions = new ArrayList<>();
                    lock.lock();
                    try {
                        for (ActionKey key : keys) {
                            ActionState state = state(key);
                            state.admission = KVCacheTypes.Admission.TERMINAL;
                            PendingAction pending = enqueueLocked(key, KVCacheTypes.ActionKind.EVICT);
                            if (pending != null) {
                                actions.add(pending.task);
                            }
                        }
                    } finally {
                        lock.unlock();
                    }
                    return waitForAll(actions, (long) ((config.actionTimeout() + config.evictTimeout()) * 1000))
                            .thenCompose(succeeded -> forget(identity).thenApply(ignored -> succeeded));
                });
    }

    private CompletableFuture<Boolean> waitForAll(List<CompletableFuture<Boolean>> actions, long timeoutMillis) {
        if (actions.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(actions.toArray(new CompletableFuture<?>[0]));
        return orTimeout(all, timeoutMillis)
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        LOG.log(System.Logger.Level.WARNING,
                                "[KVCacheRuntime] Session evict timed out or failed: {0}",
                                throwable.toString());
                        return false;
                    }
                    return actions.stream().anyMatch(action -> Boolean.TRUE.equals(action.join()));
                });
    }

    /**
     * Close the runtime: cancel in-flight actions, evict every distinct root
     * once per (parent, domain), and clear local state.
     *
     * @return completion of the shutdown eviction
     */
    public CompletableFuture<Void> close() {
        List<BindingState> snapshots;
        lock.lock();
        try {
            if (isClosed) {
                return CompletableFuture.completedFuture(null);
            }
            isClosed = true;
            stateChanged.signalAll();
            snapshots = new ArrayList<>(bindings.values());
            bindings.clear();
            rootIndex.clear();
            actionStates.clear();
        } finally {
            lock.unlock();
        }
        List<CompletableFuture<Boolean>> evictions = new ArrayList<>();
        Map<String, BindingState> roots = new LinkedHashMap<>();
        for (BindingState binding : snapshots) {
            String rootId = binding.identity().parentCacheId();
            roots.putIfAbsent(rootId + "\u0000" + binding.controlDomain(), binding);
        }
        for (BindingState binding : roots.values()) {
            String rootId = binding.identity().parentCacheId();
            evictions.add(callModel(binding, KVCacheTypes.ActionKind.EVICT,
                    new ActionInvocation(rootId, rootId, null, null)));
        }
        return waitForAll(evictions, (long) (config.closeTimeout() * 1000))
                .handle((ignored, throwable) -> null);
    }

    CompletableFuture<BindingState> registerBinding(
            KVCacheIdentity identity, Object model, String modelName, boolean isFallback) {
        if (model == null || !isValidIdentity(identity)) {
            return CompletableFuture.completedFuture(null);
        }
        KVCacheControlDomain domain = controlDomain(model, modelName);
        BindingState created = new BindingState(identity, model, domain, isFallback);
        ActionKey bindingKey = ActionKey.bindingKey(identity.cacheId(), domain);
        ActionKey rootKey = ActionKey.rootKey(identity.parentCacheId(), domain);
        lock.lock();
        try {
            if (isClosed) {
                return CompletableFuture.completedFuture(null);
            }
            replaceFallbackBinding(identity, domain, bindingKey, isFallback);
            BindingState previous = bindings.get(bindingKey);
            rerootPreviousBinding(previous, bindingKey, rootKey, domain);
            created.setRevision(previous == null ? 0 : previous.revision() + 1);
            if (previous != null) {
                created.setResidency(previous.residency());
            }
            bindings.put(bindingKey, created);
            rootIndex.computeIfAbsent(rootKey, key -> new LinkedHashSet<>()).add(bindingKey);
            state(bindingKey);
            state(rootKey);
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
        return CompletableFuture.<BindingState>completedFuture(created);
    }

    private void replaceFallbackBinding(KVCacheIdentity identity, KVCacheControlDomain domain,
            ActionKey bindingKey, boolean isFallback) {
        ActionKey fallbackKey = ActionKey.bindingKey(identity.parentCacheId(), domain);
        BindingState fallbackState = bindings.get(fallbackKey);
        boolean hasReplaceableFallback = fallbackState != null && fallbackState.isFallback();
        if (!isFallback && !fallbackKey.equals(bindingKey) && hasReplaceableFallback) {
            removeBindingLocked(fallbackKey, true);
        }
    }

    private void rerootPreviousBinding(BindingState previous, ActionKey bindingKey, ActionKey rootKey,
            KVCacheControlDomain domain) {
        if (previous == null) {
            return;
        }
        ActionKey previousRoot = ActionKey.rootKey(previous.identity().parentCacheId(), domain);
        if (previousRoot.equals(rootKey)) {
            return;
        }
        Set<ActionKey> indexed = rootIndex.get(previousRoot);
        if (indexed != null) {
            indexed.remove(bindingKey);
            if (indexed.isEmpty()) {
                rootIndex.remove(previousRoot);
                actionStates.remove(previousRoot);
            }
        }
    }

    private CompletableFuture<List<ActionKey>> identityActionKeys(KVCacheIdentity identity) {
        if (!isValidIdentity(identity)) {
            return CompletableFuture.completedFuture(List.of());
        }
        return registeredActionKeys(identity)
                .thenCompose(keys -> {
                    if (!keys.isEmpty() || bindingProvider == null) {
                        return CompletableFuture.completedFuture(keys);
                    }
                    return CompletableFuture.supplyAsync(bindingProvider, actionExecutor)
                            .handle((model, throwable) -> {
                                if (throwable != null) {
                                    LOG.log(System.Logger.Level.WARNING,
                                            "[KVCacheRuntime] fallback binding unavailable: {0}",
                                            throwable.toString());
                                    return null;
                                }
                                return model;
                            })
                            .thenCompose(model -> model == null
                                    ? CompletableFuture.completedFuture(List.<ActionKey>of())
                                    : registerBinding(identity, model, null, true)
                                    .thenApply(ignored -> List.<ActionKey>of()))
                            .thenCompose(ignored -> registeredActionKeys(identity));
                });
    }

    private CompletableFuture<List<ActionKey>> registeredActionKeys(KVCacheIdentity identity) {
        lock.lock();
        try {
            if (isClosed) {
                return CompletableFuture.completedFuture(List.of());
            }
            List<ActionKey> keys = new ArrayList<>();
            if (identity.cacheId().equals(identity.parentCacheId())) {
                for (ActionKey rootKey : rootIndex.keySet()) {
                    if (rootKey.cacheId().equals(identity.parentCacheId())) {
                        keys.add(rootKey);
                    }
                }
            } else {
                for (ActionKey bindingKey : bindings.keySet()) {
                    if (bindingKey.cacheId().equals(identity.cacheId())) {
                        keys.add(bindingKey);
                    }
                }
            }
            return CompletableFuture.completedFuture(keys);
        } finally {
            lock.unlock();
        }
    }

    private CompletableFuture<List<ActionKey>> prepareActionKeys(List<ActionKey> keys) {
        lock.lock();
        try {
            List<ActionKey> roots = new ArrayList<>();
            for (ActionKey key : keys) {
                if (key.scope() != ActionKey.ActionScope.BINDING) {
                    continue;
                }
                BindingState binding = bindings.get(ActionKey.bindingKey(key.cacheId(), key.controlDomain()));
                if (binding == null) {
                    continue;
                }
                ActionKey rootKey = ActionKey.rootKey(binding.identity().parentCacheId(), key.controlDomain());
                ActionState rootState = actionStates.get(rootKey);
                if (rootState != null
                        && rootState.admission == KVCacheTypes.Admission.BLOCKED
                        && !roots.contains(rootKey)) {
                    roots.add(rootKey);
                }
            }
            List<ActionKey> ordered = new ArrayList<>(roots);
            ordered.addAll(keys);
            return CompletableFuture.completedFuture(ordered);
        } finally {
            lock.unlock();
        }
    }

    CompletableFuture<Boolean> prepareKey(ActionKey key) {
        CompletableFuture<OffloadGate> gate = inspectForPrefetch(key);
        return gate.thenCompose(inspect -> {
            if (inspect.startedOffload() != null) {
                return finishOrCancel(inspect.startedOffload().task)
                        .thenCompose(ignored -> enqueuePrefetchAfterOffload(key));
            }
            return enqueuePrefetchAfterOffload(key);
        });
    }

    private record OffloadGate(PendingAction startedOffload) {
    }

    private CompletableFuture<OffloadGate> inspectForPrefetch(ActionKey key) {
        lock.lock();
        try {
            if (isClosed) {
                return CompletableFuture.completedFuture(new OffloadGate(null));
            }
            ActionState state = state(key);
            if (state.admission == KVCacheTypes.Admission.TERMINAL) {
                return CompletableFuture.completedFuture(new OffloadGate(null));
            }
            PendingAction pending = state.pendingAction;
            if (pending != null && pending.kind == KVCacheTypes.ActionKind.OFFLOAD
                    && !pending.providerCallStarted.isStarted()) {
                pending.task.cancel(true);
                state.admission = KVCacheTypes.Admission.OPEN;
                return CompletableFuture.completedFuture(new OffloadGate(null));
            }
            if (pending != null && pending.kind == KVCacheTypes.ActionKind.OFFLOAD) {
                return CompletableFuture.completedFuture(new OffloadGate(pending));
            }
            return CompletableFuture.completedFuture(new OffloadGate(null));
        } finally {
            lock.unlock();
        }
    }

    private CompletableFuture<Boolean> enqueuePrefetchAfterOffload(ActionKey key) {
        lock.lock();
        try {
            if (isClosed) {
                return CompletableFuture.completedFuture(false);
            }
            ActionState state = state(key);
            if (state.admission == KVCacheTypes.Admission.TERMINAL) {
                return CompletableFuture.completedFuture(false);
            }
            PendingAction pending = state.pendingAction;
            List<BindingState> boundBindings = bindingsForActionLocked(key);
            boolean isPrefetchNeeded = (pending != null && pending.kind == KVCacheTypes.ActionKind.OFFLOAD)
                    || relatedScopeIsBlockedLocked(key)
                    || boundBindings.stream().anyMatch(
                    binding -> binding.residency() != BindingState.Residency.RESIDENT);
            if (!isPrefetchNeeded) {
                state.admission = KVCacheTypes.Admission.OPEN;
                stateChanged.signalAll();
                return CompletableFuture.completedFuture(true);
            }
            PendingAction action = enqueueLocked(key, KVCacheTypes.ActionKind.PREFETCH);
            if (action == null) {
                state.admission = KVCacheTypes.Admission.OPEN;
                stateChanged.signalAll();
                return CompletableFuture.completedFuture(false);
            }
            return waitUntilProviderCallStarted(action);
        } finally {
            lock.unlock();
        }
    }

    private CompletableFuture<Boolean> waitUntilProviderCallStarted(PendingAction action) {
        long timeoutMillis = (long) (config.actionTimeout() * 1000);
        return CompletableFuture.supplyAsync(() -> {
            try {
                boolean isStarted = action.providerCallStarted.awaitStarted(timeoutMillis);
                if (isStarted) {
                    return true;
                }
                if (action.task.isDone() && !action.task.isCompletedExceptionally()) {
                    return Boolean.TRUE.equals(action.task.join());
                }
                return false;
            } catch (java.util.concurrent.CancellationException exception) {
                return false;
            } catch (CompletionException exception) {
                LOG.log(System.Logger.Level.WARNING,
                        "prefetch admission failed: {0}", exception.toString());
                return false;
            }
        }, actionExecutor);
    }

    private CompletableFuture<Boolean> finishOrCancel(CompletableFuture<Boolean> task) {
        return orTimeout(task, (long) (config.actionTimeout() * 1000))
                .handle((ignored, throwable) -> {
                    if (throwable != null) {
                        task.cancel(true);
                    }
                    return true;
                });
    }

    private PendingAction enqueueLocked(ActionKey key, KVCacheTypes.ActionKind kind) {
        ActionState state = state(key);
        PendingAction current = state.pendingAction;
        if (current != null && !current.task.isDone() && current.kind == kind) {
            return current;
        }
        java.util.concurrent.atomic.AtomicReference<PendingAction> self =
                new java.util.concurrent.atomic.AtomicReference<>();
        List<CompletableFuture<Boolean>> dependencies = actionDependenciesLocked(key);
        CompletableFuture<Boolean> task = runAction(key, kind, dependencies, self);
        PendingAction pending = new PendingAction(kind, task);
        self.set(pending);
        state.pendingAction = pending;
        state.actionTail = task;
        tasks.put(task, Boolean.TRUE);
        task.whenComplete((ignored, throwable) -> tasks.remove(task));
        return pending;
    }

    private CompletableFuture<Boolean> runAction(
            ActionKey key, KVCacheTypes.ActionKind kind, List<CompletableFuture<Boolean>> dependencies,
            java.util.concurrent.atomic.AtomicReference<PendingAction> self) {
        CompletableFuture<Boolean> chain = completedDependencyChain(dependencies);
        return chain
                .thenCompose(ignored -> awaitIdleThenTimeout(key, kind))
                .thenCompose(ignored -> invokeProviderCall(key, kind))
                .thenCompose(succeeded -> recordResult(key, kind, key, succeeded)
                        .thenApply(ignored -> succeeded))
                .exceptionally(throwable -> {
                    LOG.log(System.Logger.Level.WARNING, "action failed: action={0} cacheId={1} error={2}",
                            kind.value(), key.cacheId(), rootMessage(throwable));
                    recordResult(key, kind, null, false);
                    return false;
                })
                .whenComplete((ignored, throwable) -> completePendingAction(key, kind, self.get()));
    }

    private CompletableFuture<Boolean> completedDependencyChain(List<CompletableFuture<Boolean>> dependencies) {
        if (dependencies.isEmpty()) {
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.allOf(dependencies.toArray(new CompletableFuture<?>[0]))
                .handle((ignored, throwable) -> true);
    }

    private CompletableFuture<Boolean> awaitIdleThenTimeout(ActionKey key, KVCacheTypes.ActionKind kind) {
        if (kind != KVCacheTypes.ActionKind.OFFLOAD && kind != KVCacheTypes.ActionKind.EVICT) {
            return CompletableFuture.completedFuture(true);
        }
        return waitForIdle(key)
                .thenCompose(idle -> orTimeout(
                        CompletableFuture.completedFuture(idle), actionTimeoutMillis(kind)));
    }

    private CompletableFuture<Boolean> invokeProviderCall(ActionKey key, KVCacheTypes.ActionKind kind) {
        List<BindingState> snapshots;
        lock.lock();
        try {
            snapshots = new ArrayList<>(bindingsForActionLocked(key));
        } finally {
            lock.unlock();
        }
        if (snapshots.isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }
        BindingState binding = snapshots.get(0);
        String cacheId = key.cacheId();
        String parentCacheId = key.scope() == ActionKey.ActionScope.ROOT
                ? key.cacheId()
                : binding.identity().parentCacheId();
        ProviderCallSignal signal = currentPendingActionSignal(key, kind).orElse(null);
        return orTimeout(
                callModel(binding, kind,
                        new ActionInvocation(cacheId, parentCacheId, signal, key)),
                actionTimeoutMillis(kind));
    }

    private long actionTimeoutMillis(KVCacheTypes.ActionKind kind) {
        return (long) ((kind == KVCacheTypes.ActionKind.EVICT
                ? config.evictTimeout()
                : config.actionTimeout()) * 1000);
    }

    /** One provider-side action invocation captured from a scope key. */
    private record ActionInvocation(String cacheId, String parentCacheId,
            ProviderCallSignal signal, ActionKey actionKey) {
    }

    private Optional<ProviderCallSignal> currentPendingActionSignal(ActionKey key, KVCacheTypes.ActionKind kind) {
        lock.lock();
        try {
            ActionState state = actionStates.get(key);
            PendingAction pending = state == null ? null : state.pendingAction;
            if (pending != null && pending.kind == kind && !pending.task.isDone()) {
                return Optional.of(pending.providerCallStarted);
            }
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    private void completePendingAction(ActionKey key, KVCacheTypes.ActionKind kind, PendingAction self) {
        lock.lock();
        try {
            ActionState state = actionStates.get(key);
            if (state != null && state.pendingAction != null && state.pendingAction == self) {
                state.pendingAction = null;
                if (kind == KVCacheTypes.ActionKind.PREFETCH
                        && state.admission == KVCacheTypes.Admission.BLOCKED) {
                    openPrefetchedScopeLocked(key);
                }
                stateChanged.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    private CompletableFuture<Void> recordResult(
            ActionKey key, KVCacheTypes.ActionKind kind, ActionKey snapshotKey, boolean isSucceeded) {
        BindingState.Residency target;
        if (isSucceeded && kind == KVCacheTypes.ActionKind.OFFLOAD) {
            target = BindingState.Residency.OFFLOADED;
        } else if (isSucceeded && kind == KVCacheTypes.ActionKind.PREFETCH) {
            target = BindingState.Residency.RESIDENT;
        } else {
            target = BindingState.Residency.UNKNOWN;
        }
        lock.lock();
        try {
            List<BindingState> snapshots = snapshotKey == null
                    ? List.of()
                    : List.copyOf(bindingsForActionLocked(snapshotKey));
            for (BindingState binding : snapshots) {
                ActionKey bindingKey = ActionKey.bindingKey(binding.identity().cacheId(), binding.controlDomain());
                BindingState current = bindings.get(bindingKey);
                if (current != null) {
                    current.setResidency(target);
                }
            }
            ActionState state = actionStates.get(key);
            if (state != null && !isSucceeded) {
                state.isFailOpen = true;
            }
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
        return CompletableFuture.completedFuture(null);
    }

    private CompletableFuture<Boolean> callModel(
            BindingState binding, KVCacheTypes.ActionKind kind, ActionInvocation invocation) {
        lock.lock();
        try {
            if (invocation.signal() != null
                    && kind == KVCacheTypes.ActionKind.PREFETCH && invocation.actionKey() != null) {
                openPrefetchedScopeLocked(invocation.actionKey());
            }
        } finally {
            lock.unlock();
        }
        if (invocation.signal() != null) {
            invocation.signal().markStarted();
        }
        return CompletableFuture.supplyAsync(() -> {
            Object model = binding.model();
            Object outcome = invokeKvcMethod(model, kind.value(),
                    invocation.cacheId(), invocation.parentCacheId(), binding);
            return Boolean.TRUE.equals(outcome);
        }, actionExecutor);
    }

    private Object invokeKvcMethod(Object model, String kindValue, String cacheId, String parentCacheId,
                                   BindingState binding) {
        Optional<java.lang.reflect.Method> method = findKvcMethod(model, kindValue + "Kvc");
        if (method.isEmpty()) {
            return Boolean.FALSE;
        }
        try {
            Class<?>[] parameterTypes = method.get().getParameterTypes();
            Object[] args = buildKvcArgs(parameterTypes, cacheId, parentCacheId,
                    binding.controlDomain().modelNameOrEmpty());
            return method.get().invoke(model, args);
        } catch (java.lang.reflect.InvocationTargetException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            LOG.log(System.Logger.Level.WARNING, "kvc action {0} failed: {1}", kindValue, cause.toString());
            return Boolean.FALSE;
        } catch (IllegalAccessException | IllegalArgumentException exception) {
            LOG.log(System.Logger.Level.WARNING, "kvc action {0} failed: {1}", kindValue, exception.toString());
            return Boolean.FALSE;
        }
    }

    private Optional<java.lang.reflect.Method> findKvcMethod(Object model, String camelName) {
        if (model == null) {
            return Optional.empty();
        }
        Class<?> type = model.getClass();
        while (type != null) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (method.getName().equals(camelName) && method.getParameterCount() > 0) {
                    method.setAccessible(true);
                    return Optional.of(method);
                }
            }
            type = type.getSuperclass();
        }
        return Optional.empty();
    }

    private Object[] buildKvcArgs(Class<?>[] parameterTypes, String cacheId, String parentCacheId, String modelName) {
        List<Object> args = new ArrayList<>(parameterTypes.length);
        for (int index = 0; index < parameterTypes.length; index++) {
            args.add("String".equals(parameterTypes[index].getSimpleName()) ? cacheId : null);
        }
        if (parameterTypes.length >= 2) {
            args.set(0, cacheId);
            args.set(1, parentCacheId);
        }
        if (parameterTypes.length >= 3) {
            args.set(2, "session");
        }
        if (parameterTypes.length >= 4) {
            args.set(3, modelName.isEmpty() ? null : modelName);
        }
        return args.toArray();
    }

    private CompletableFuture<Boolean> waitForIdle(ActionKey key) {
        return CompletableFuture.supplyAsync(() -> {
            lock.lock();
            try {
                while (!isClosed) {
                    ActionState state = actionStates.get(key);
                    if (state == null || state.activeInferenceCount == 0) {
                        return true;
                    }
                    try {
                        stateChanged.await(200, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException exception) {
                        // Cooperative waiter: stop polling on interruption.
                        return true;
                    }
                }
                return true;
            } finally {
                lock.unlock();
            }
        }, actionExecutor);
    }

    private CompletableFuture<Void> forget(KVCacheIdentity identity) {
        lock.lock();
        try {
            List<ActionKey> bindingKeys = new ArrayList<>();
            if (identity.cacheId().equals(identity.parentCacheId())) {
                for (Map.Entry<ActionKey, Set<ActionKey>> entry : rootIndex.entrySet()) {
                    if (entry.getKey().cacheId().equals(identity.parentCacheId())) {
                        bindingKeys.addAll(entry.getValue());
                    }
                }
            } else {
                for (ActionKey key : bindings.keySet()) {
                    if (key.cacheId().equals(identity.cacheId())) {
                        bindingKeys.add(key);
                    }
                }
            }
            for (ActionKey bindingKey : bindingKeys) {
                removeBindingLocked(bindingKey, false);
            }
            stateChanged.signalAll();
        } finally {
            lock.unlock();
        }
        return CompletableFuture.completedFuture(null);
    }

    private void removeBindingLocked(ActionKey bindingKey, boolean isPreserveRootState) {
        BindingState binding = bindings.remove(bindingKey);
        if (binding == null) {
            return;
        }
        ActionKey rootKey = ActionKey.rootKey(binding.identity().parentCacheId(), bindingKey.controlDomain());
        Set<ActionKey> indexed = rootIndex.get(rootKey);
        if (indexed != null) {
            indexed.remove(bindingKey);
            if (indexed.isEmpty()) {
                rootIndex.remove(rootKey);
                if (!isPreserveRootState) {
                    actionStates.remove(rootKey);
                }
            }
        }
        actionStates.remove(bindingKey);
    }

    private List<CompletableFuture<Boolean>> actionDependenciesLocked(ActionKey key) {
        List<CompletableFuture<Boolean>> dependencies = new ArrayList<>();
        addTail(dependencies, actionStates.get(key));
        if (key.scope() == ActionKey.ActionScope.ROOT) {
            ActionKey rootKey = ActionKey.rootKey(key.cacheId(), key.controlDomain());
            for (ActionKey bindingKey : rootIndex.getOrDefault(rootKey, Set.of())) {
                addTail(dependencies, actionStates.get(bindingKey));
            }
        } else {
            BindingState binding = bindings.get(ActionKey.bindingKey(key.cacheId(), key.controlDomain()));
            if (binding != null) {
                ActionKey rootKey = ActionKey.rootKey(binding.identity().parentCacheId(), key.controlDomain());
                addTail(dependencies, actionStates.get(rootKey));
            }
        }
        return dependencies;
    }

    private void addTail(List<CompletableFuture<Boolean>> dependencies, ActionState state) {
        if (state != null && state.actionTail != null && !dependencies.contains(state.actionTail)) {
            dependencies.add(state.actionTail);
        }
    }

    private boolean relatedScopeIsBlockedLocked(ActionKey key) {
        if (key.scope() == ActionKey.ActionScope.ROOT) {
            ActionKey rootKey = ActionKey.rootKey(key.cacheId(), key.controlDomain());
            for (ActionKey bindingKey : rootIndex.getOrDefault(rootKey, Set.of())) {
                ActionState state = actionStates.get(bindingKey);
                if (state != null && state.admission == KVCacheTypes.Admission.BLOCKED) {
                    return true;
                }
            }
            return false;
        }
        BindingState binding = bindings.get(ActionKey.bindingKey(key.cacheId(), key.controlDomain()));
        if (binding == null) {
            return false;
        }
        ActionKey rootKey = ActionKey.rootKey(binding.identity().parentCacheId(), key.controlDomain());
        ActionState rootState = actionStates.get(rootKey);
        return rootState != null && rootState.admission == KVCacheTypes.Admission.BLOCKED;
    }

    private void openPrefetchedScopeLocked(ActionKey key) {
        List<ActionKey> keys = new ArrayList<>();
        keys.add(key);
        if (key.scope() == ActionKey.ActionScope.ROOT) {
            ActionKey rootKey = ActionKey.rootKey(key.cacheId(), key.controlDomain());
            for (ActionKey bindingKey : rootIndex.getOrDefault(rootKey, Set.of())) {
                keys.add(bindingKey);
            }
        }
        for (ActionKey actionKey : keys) {
            ActionState state = actionStates.get(actionKey);
            if (state != null && state.admission != KVCacheTypes.Admission.TERMINAL) {
                state.admission = KVCacheTypes.Admission.OPEN;
            }
        }
        stateChanged.signalAll();
    }

    private List<BindingState> bindingsForActionLocked(ActionKey key) {
        if (key.scope() == ActionKey.ActionScope.BINDING) {
            BindingState state = bindings.get(ActionKey.bindingKey(key.cacheId(), key.controlDomain()));
            return state == null ? List.of() : List.of(state);
        }
        ActionKey rootKey = ActionKey.rootKey(key.cacheId(), key.controlDomain());
        List<BindingState> result = new ArrayList<>();
        for (ActionKey bindingKey : rootIndex.getOrDefault(rootKey, Set.of())) {
            BindingState binding = bindings.get(bindingKey);
            if (binding != null) {
                result.add(binding);
            }
        }
        return result;
    }

    private ActionState state(ActionKey key) {
        return actionStates.computeIfAbsent(key, ignored -> new ActionState());
    }

    private static boolean isValidIdentity(KVCacheIdentity identity) {
        return identity != null
                && !identity.cacheId().strip().isEmpty()
                && !identity.parentCacheId().strip().isEmpty();
    }

    private static KVCacheControlDomain controlDomain(Object model, String modelName) {
        Optional<Object> clientConfig = property(model, "modelClientConfig", "getModelClientConfig");
        Optional<Object> requestConfig = property(model, "modelConfig", "getModelConfig");
        String provider = "";
        String apiBase = "";
        String namespace = "";
        if (clientConfig.isPresent()) {
            Object config = clientConfig.get();
            provider = providerOf(config);
            apiBase = apiBaseOf(config);
            namespace = namespaceOf(config);
        }
        String resolvedModel = resolveModelName(modelName, requestConfig);
        return new KVCacheControlDomain(provider.strip(), apiBase.strip(), resolvedModel, namespace.strip());
    }

    private static String providerOf(Object clientConfig) {
        Object providerValue = property(clientConfig, "clientProvider", "getClientProvider").orElse("");
        return providerValue instanceof com.openjiuwen.core.foundation.llm.schema.ProviderType type
                ? type.getValue()
                : String.valueOf(providerValue);
    }

    private static String apiBaseOf(Object clientConfig) {
        return String.valueOf(property(clientConfig, "apiBase", "getApiBase").orElse(""));
    }

    private static String namespaceOf(Object clientConfig) {
        Optional<Object> kvCache = property(clientConfig, "extensions", "getExtensions")
                .flatMap(extensions -> property(extensions, "kvCache", "getKvCache"));
        return kvCache
                .map(cache -> String.valueOf(
                        property(cache, "cacheNamespace", "getCacheNamespace").orElse("")))
                .orElse("");
    }

    private static String resolveModelName(String modelName, Optional<Object> requestConfig) {
        if (modelName != null && !modelName.isEmpty()) {
            return modelName;
        }
        return requestConfig
                .map(config -> String.valueOf(property(config, "modelName", "getModelName").orElse("")))
                .orElse("");
    }

    private static Optional<Object> property(Object target, String fieldName, String getterName) {
        if (target == null) {
            return Optional.empty();
        }
        try {
            java.lang.reflect.Field field = target.getClass().getField(fieldName);
            return Optional.ofNullable(field.get(target));
        } catch (NoSuchFieldException | IllegalAccessException ignored) {
            // fall through to getter lookup
        }
        try {
            return Optional.ofNullable(target.getClass().getMethod(getterName).invoke(target));
        } catch (NoSuchMethodException | IllegalAccessException
                | java.lang.reflect.InvocationTargetException ignored) {
            return Optional.empty();
        }
    }

    private static String rootMessage(Throwable throwable) {
        Throwable current = throwable;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current == null ? "unknown" : current.toString();
    }

    private static <T> CompletableFuture<T> orTimeout(CompletableFuture<T> future, long timeoutMillis) {
        long effectiveTimeout = timeoutMillis <= 0 ? 1 : timeoutMillis;
        future.orTimeout(effectiveTimeout, TimeUnit.MILLISECONDS);
        return future;
    }

    /**
     * Expose registered binding count for diagnostics and tests.
     *
     * @return number of live bindings
     */
    int bindingCount() {
        lock.lock();
        try {
            return bindings.size();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Expose whether the given binding residency matches expectation.
     *
     * @param cacheId binding cache id
     * @param residency expected residency
     * @return true when the binding exists with that residency
     */
    boolean isResidency(String cacheId, BindingState.Residency residency) {
        lock.lock();
        try {
            for (Map.Entry<ActionKey, BindingState> entry : bindings.entrySet()) {
                if (entry.getKey().cacheId().equals(cacheId)) {
                    return entry.getValue().residency() == residency;
                }
            }
            return false;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Surface the admission state for tests.
     *
     * @param key action key
     * @return admission state
     */
    KVCacheTypes.Admission admissionOf(ActionKey key) {
        lock.lock();
        try {
            ActionState state = actionStates.get(key);
            return state == null ? KVCacheTypes.Admission.OPEN : state.admission;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Test hook: whether any failure has been recorded on a scope.
     *
     * @param key action key
     * @return true when fail-open was recorded
     */
    boolean isFailOpen(ActionKey key) {
        lock.lock();
        try {
            ActionState state = actionStates.get(key);
            return state != null && state.isFailOpen;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Test hook: the number of pending background tasks.
     *
     * @return pending task count
     */
    int pendingTaskCount() {
        return tasks.size();
    }

    static Collection<Object> emptySafe(Collection<Object> values) {
        return values == null ? List.of() : values;
    }
}
