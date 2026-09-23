/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.runner.callback;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.common.logging.Loggers;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Concurrency tests for the callback registry.
 *
 * <p>Before the fix, {@code unregister} iterated the per-event
 * synchronizedList without holding its monitor and removed the entry in a
 * separate lock acquisition, while {@code sortCallbacks} sorted the same
 * list bare; concurrent registration then threw
 * {@code ConcurrentModificationException} from either site. These tests pin
 * the atomic unregister contract (iterate + remove under the event-list
 * mutex) and the trigger-family immunity under concurrent register and
 * unregister pressure, at the operation shape the production rail binding
 * path uses ({@code register(event, callback, priority, callbackName)} and
 * {@code unregister(event, callback)}).</p>
 */
@DisplayName("CallbackFramework concurrency")
@Timeout(300)
class CallbackFrameworkConcurrencyTest {
    private static final int THREADS = 16;

    private static final int ROUNDS = 2000;

    private static final int SHARED_EVENTS = 4;

    private static final int GROUP_SIZE = 8;

    private static final Consumer<ChainContext> NOOP_ROLLBACK = context -> {
    };

    private CallbackFramework framework;

    @BeforeEach
    void setup() {
        framework = new CallbackFramework(false, false);
    }

    private static ExecutorService newPool(int threads, String poolName) {
        AtomicInteger seq = new AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS,
            new LinkedBlockingQueue<>(threads * 2), r -> {
                Thread thread = new Thread(r, poolName + "-" + seq.incrementAndGet());
                thread.setUncaughtExceptionHandler((failedThread, error) ->
                    Loggers.COMMON.error("Uncaught exception in {}: {}",
                        failedThread.getName(), error.getMessage()));
                return thread;
            });
    }

    private static final class CountingCallback implements Function<Map<String, Object>, Object> {
        private final AtomicInteger invocations = new AtomicInteger();

        private final Set<Integer> roundsSeen = ConcurrentHashMap.newKeySet();

        private final AtomicInteger duplicateRounds = new AtomicInteger();

        @Override
        public Object apply(Map<String, Object> kwargs) {
            invocations.incrementAndGet();
            Object round = kwargs.get("round");
            if (round instanceof Integer r && !roundsSeen.add(r)) {
                duplicateRounds.incrementAndGet();
            }
            return null;
        }
    }

    /**
     * 16 threads x 2000 rounds of register / unregister / sort
     * interleaved on shared events must complete with zero exceptions and a
     * consistent registry: only the pre-registered stable callbacks remain,
     * in priority-descending order, with no filter or chain residue.
     *
     * @throws Exception if any storm worker fails or exceeds its wait bound
     */
    @Test
    void registerUnregisterSort_underStorm_noCmeAndStructuresConsistent() throws Exception {
        String[] events = new String[SHARED_EVENTS];
        for (int i = 0; i < SHARED_EVENTS; i++) {
            events[i] = "storm-event-" + i;
            framework.register(events[i], kwargs -> null, 40, "stable-A");
            framework.register(events[i], kwargs -> null, 30, "stable-B");
            framework.register(events[i], kwargs -> null, 20, "stable-C");
            framework.register(events[i], kwargs -> null, 10, "stable-D");
        }

        ExecutorService pool = newPool(THREADS, "cb-storm");
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> {
                    start.await();
                    String event = events[threadId % SHARED_EVENTS];
                    Function<Map<String, Object>, Object> own = kwargs -> null;
                    List<EventFilter> passFilter = List.of(new EventFilter());
                    for (int round = 0; round < ROUNDS; round++) {
                        int priority = (threadId * 31 + round) % 97;
                        if (round % 3 == 0) {
                            framework.register(event, own, priority, false, "storm", Set.of("storm"), passFilter, null,
                                null, 0, 0.0, null, "storm-cb");
                        } else {
                            framework.register(event, own, priority, "storm-cb");
                        }
                        framework.unregister(event, own);
                        framework.unregister(event, own);
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) {
                future.get(60L, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        for (String event : events) {
            List<Map<String, Object>> listed = framework.listCallbacks(event);
            assertThat(listed).extracting(info -> info.get("name"))
                .containsExactly("stable-A", "stable-B", "stable-C", "stable-D");
        }
        assertThat(framework.getCallbackFilters()).isEmpty();
        assertThat(framework.getChains()).isEmpty();
    }

    /**
     * a single unregister must be atomic across the three
     * structures (event list, callback filters, chain members) while other
     * threads mutate the same event and trigger it continuously: no CME, no
     * duplicated invocation per trigger round, and zero residue once the
     * final unregister pass completes.
     *
     * @throws Exception if any concurrent worker fails or exceeds its wait bound
     */
    @Test
    void singleUnregister_underTriggerAndMutation_atomicAcrossStructures() throws Exception {
        String event = "atomicity-event";
        CountingCallback[] stables = registerStableCallbacks(event, 44, 11);
        CountingCallback[] group = new CountingCallback[GROUP_SIZE];
        for (int i = 0; i < group.length; i++) {
            group[i] = new CountingCallback();
        }
        Runnable registerGroup = groupRegistrar(event, group);
        registerGroup.run();
        int triggerRounds = runAtomicityStorm(event, group, registerGroup);
        assertAtomicityOutcome(event, stables, group, triggerRounds);
    }

    /**
     * continuous trigger of pre-existing callbacks while a
     * 16-thread register/unregister storm runs on the same event must keep
     * the existing callbacks' invocation counts exact: no lost and no
     * duplicated invocations, and no exceptions anywhere.
     *
     * @throws Exception if any concurrent worker fails or exceeds its wait bound
     */
    @Test
    void trigger_underRegisterUnregisterStorm_noLossNoDuplication() throws Exception {
        String event = "trigger-immunity-event";
        CountingCallback[] stables = registerStableCallbacks(event, 40, 10);
        int triggerRounds = runTriggerImmunityStorm(event);
        assertThat(triggerRounds).isPositive();
        for (CountingCallback stable : stables) {
            assertThat(stable.invocations.get()).isEqualTo(triggerRounds);
            assertThat(stable.roundsSeen.size()).isEqualTo(triggerRounds);
            assertThat(stable.duplicateRounds.get()).isZero();
        }
    }

    private CountingCallback[] registerStableCallbacks(String event, int topPriority, int step) {
        CountingCallback[] stables = new CountingCallback[4];
        for (int i = 0; i < stables.length; i++) {
            stables[i] = new CountingCallback();
            framework.register(event, stables[i], topPriority - i * step, "stable-" + i);
        }
        return stables;
    }

    private Runnable groupRegistrar(String event, CountingCallback[] group) {
        return () -> {
            for (int i = 0; i < group.length; i++) {
                if (i < group.length / 2) {
                    framework.register(event, group[i], 90 - i, false, "group", Set.of("group"),
                        List.of(new EventFilter()), null, null, 0, 0.0, null, "group-filtered-" + i);
                } else {
                    framework.register(event, group[i], 90 - i, false, "group", Set.of("group"), null,
                        NOOP_ROLLBACK, null, 0, 0.0, null, "group-chained-" + i);
                }
            }
        };
    }

    private int runAtomicityStorm(String event, CountingCallback[] group, Runnable registerGroup) throws Exception {
        ExecutorService pool = newPool(3, "cb-atomic");
        AtomicBoolean stop = new AtomicBoolean();
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> unregisterer = pool.submit(() -> unregisterRounds(start, event, group, registerGroup));
            Future<?> mutator = pool.submit(() -> mutatorRounds(start, event));
            Future<Integer> triggerer = pool.submit(() -> triggerLoop(start, event, stop));
            start.countDown();
            try {
                unregisterer.get(60L, TimeUnit.SECONDS);
                mutator.get(60L, TimeUnit.SECONDS);
            } finally {
                stop.set(true);
            }
            return triggerer.get(60L, TimeUnit.SECONDS);
        } finally {
            stop.set(true);
            pool.shutdownNow();
        }
    }

    private int unregisterRounds(CountDownLatch start, String event, CountingCallback[] group,
            Runnable registerGroup) throws Exception {
        start.await();
        for (int round = 0; round < ROUNDS; round++) {
            for (CountingCallback callback : group) {
                framework.unregister(event, callback);
            }
            if (round < ROUNDS - 1) {
                registerGroup.run();
            }
        }
        return ROUNDS;
    }

    private int mutatorRounds(CountDownLatch start, String event) throws Exception {
        start.await();
        Function<Map<String, Object>, Object> own = kwargs -> null;
        for (int i = 0; i < ROUNDS; i++) {
            framework.register(event, own, (i * 7) % 89, "mutator");
            framework.unregister(event, own);
            framework.unregister(event, own);
        }
        return ROUNDS;
    }

    private int triggerLoop(CountDownLatch start, String event, AtomicBoolean stop) throws Exception {
        start.await();
        int round = 0;
        while (!stop.get()) {
            round++;
            framework.trigger(event, Map.of("round", round));
        }
        return round;
    }

    private void assertAtomicityOutcome(String event, CountingCallback[] stables, CountingCallback[] group,
            int triggerRounds) {
        assertThat(triggerRounds).isPositive();
        for (CountingCallback stable : stables) {
            assertThat(stable.invocations.get()).isEqualTo(triggerRounds);
            assertThat(stable.duplicateRounds.get()).isZero();
        }
        for (CountingCallback callback : group) {
            assertThat(callback.invocations.get()).isLessThanOrEqualTo(triggerRounds);
            assertThat(callback.duplicateRounds.get()).isZero();
        }
        assertThat(framework.listCallbacks(event)).extracting(info -> info.get("name"))
            .containsExactly("stable-0", "stable-1", "stable-2", "stable-3");
        assertThat(framework.getCallbackFilters()).isEmpty();
        CallbackChain chain = framework.getChains().get(event);
        assertThat(chain).isNotNull();
        assertThat(chain.getCallbacks()).isEmpty();
    }

    private int runTriggerImmunityStorm(String event) throws Exception {
        ExecutorService pool = newPool(THREADS + 1, "cb-immunity");
        AtomicBoolean stop = new AtomicBoolean();
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int threadId = t;
                futures.add(pool.submit(() -> stormRounds(start, event, threadId)));
            }
            Future<Integer> triggerer = pool.submit(() -> triggerLoop(start, event, stop));
            start.countDown();
            try {
                for (Future<?> future : futures) {
                    future.get(60L, TimeUnit.SECONDS);
                }
            } finally {
                stop.set(true);
            }
            return triggerer.get(60L, TimeUnit.SECONDS);
        } finally {
            stop.set(true);
            pool.shutdownNow();
        }
    }

    private int stormRounds(CountDownLatch start, String event, int threadId) throws Exception {
        start.await();
        Function<Map<String, Object>, Object> own = kwargs -> null;
        for (int round = 0; round < ROUNDS; round++) {
            framework.register(event, own, (threadId * 31 + round) % 97, "storm-cb");
            framework.unregister(event, own);
            framework.unregister(event, own);
        }
        return ROUNDS;
    }
}
