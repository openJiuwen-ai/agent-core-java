/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.openjiuwen.agentteams.schema.team.TeamRuntimeContext;
import com.openjiuwen.agentteams.spawn.SpawnHandle;
import com.openjiuwen.agentteams.tools.TeamBackend;
import com.openjiuwen.agentteams.tools.TeamMember;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies recovery task sharing, stale callback isolation, and rejected task cleanup for teammates.
 *
 * @since 2026-10-27
 */
class SpawnRecoveryConcurrencyTest {
    private final AtomicReference<Throwable> uncaught = new AtomicReference<>();
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(
            2, 2, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(8), runnable -> {
                Thread thread = new Thread(runnable, "spawn-recovery-test");
                thread.setDaemon(true);
                thread.setUncaughtExceptionHandler((worker, error) -> uncaught.compareAndSet(null, error));
                return thread;
            });
    private final CountDownLatch releaseSpawn = new CountDownLatch(1);
    private final TeamBackend backend = mock(TeamBackend.class);
    private SpawnManager manager;

    @BeforeEach
    void setup() {
        manager = spy(new SpawnManager(mock(TeamAgent.class), backend,
                new RecoveryManager(backend, "leader"), () -> "recovery-test", executor));
        when(backend.getMember("worker")).thenReturn(TeamMember.builder().memberName("worker").build());
        doReturn(TeamRuntimeContext.builder().memberName("worker").build())
                .when(manager).buildContextFromBackend("worker");
        doNothing().when(manager).publishRestartEvent(anyString(), anyInt());
    }

    @AfterEach
    void cleanup() throws InterruptedException {
        releaseSpawn.countDown();
        manager.cancelRecoveryTasks();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        manager.shutdownAllHandles();
        assertThat(uncaught.get()).isNull();
    }

    @Test
    void duplicateRecoverySharesOneTaskAndKeepsReplacement() throws Exception {
        SpawnHandle original = handle();
        manager.registerHandle("worker", original);
        CountDownLatch spawning = new CountDownLatch(1);
        SpawnHandle replacement = arrangeReplacement(spawning);

        completeConcurrentRecovery(spawning);

        assertThat(manager.getSpawnedHandles()).containsEntry("worker", replacement);
        assertThat(manager.getRecoveryTaskCount()).isZero();
        verify(original).forceKill();
        verify(manager, times(1)).spawnTeammate(any(TeamRuntimeContext.class), any());
        verify(manager, times(1)).publishRestartEvent("worker", 1);
    }

    private void completeConcurrentRecovery(CountDownLatch spawning) throws Exception {
        Future<?> first = manager.triggerUnhealthyRecovery("worker");
        assertThat(spawning.await(5, TimeUnit.SECONDS)).isTrue();
        Future<?> duplicate = manager.triggerUnhealthyRecovery("worker");
        try {
            assertThat(duplicate).isSameAs(first);
        } finally {
            releaseSpawn.countDown();
        }
        first.get(5, TimeUnit.SECONDS);
        duplicate.get(5, TimeUnit.SECONDS);
    }

    @Test
    void lateCallbackFromReplacedHandleCannotRemoveNewHandle() throws Exception {
        SpawnHandle original = handle();
        manager.registerHandle("worker", original);
        ArgumentCaptor<Runnable> callback = ArgumentCaptor.forClass(Runnable.class);
        verify(original).setOnUnhealthy(callback.capture());
        SpawnHandle replacement = arrangeReplacement(new CountDownLatch(1));
        releaseSpawn.countDown();
        manager.triggerUnhealthyRecovery("worker").get(5, TimeUnit.SECONDS);

        callback.getValue().run();
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        assertThat(manager.getSpawnedHandles()).containsEntry("worker", replacement);
        verify(replacement, never()).forceKill();
        verify(manager, times(1)).publishRestartEvent("worker", 1);
    }

    @Test
    void rejectedRecoveryDoesNotLeaveTrackedTask() {
        executor.shutdown();

        assertThatThrownBy(() -> manager.triggerUnhealthyRecovery("worker"))
                .isInstanceOf(RejectedExecutionException.class);

        assertThat(manager.getRecoveryTaskCount()).isZero();
        verify(manager, never()).onTeammateUnhealthy(anyString());
    }

    private SpawnHandle arrangeReplacement(CountDownLatch spawning) {
        SpawnHandle replacement = handle();
        doAnswer(call -> {
            spawning.countDown();
            assertThat(releaseSpawn.await(5, TimeUnit.SECONDS)).isTrue();
            manager.registerHandle("worker", replacement);
            return replacement;
        }).when(manager).spawnTeammate(any(TeamRuntimeContext.class), any());
        return replacement;
    }

    private static SpawnHandle handle() {
        SpawnHandle handle = mock(SpawnHandle.class);
        when(handle.isAlive()).thenReturn(true);
        return handle;
    }
}
