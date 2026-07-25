package com.openjiuwen.agentteams;

import com.openjiuwen.agentteams.agent.CoordinatorLoop;
import com.openjiuwen.agentteams.agent.InnerEventMessage;
import com.openjiuwen.agentteams.agent.InnerEventType;
import com.openjiuwen.agentteams.schema.events.EventMessage;
import com.openjiuwen.agentteams.schema.team.TeamRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class CoordinatorLoopCompatibilityTest {

    @Test
    void startStopShouldToggleRunningFlag() {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER);

        assertThat(loop.isRunning()).isFalse();

        loop.start();
        assertThat(loop.isRunning()).isTrue();

        loop.stop();
        assertThat(loop.isRunning()).isFalse();
    }

    @Test
    void stopShouldBeIdempotent() {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER);

        loop.start();
        loop.stop();
        loop.stop();

        assertThat(loop.isRunning()).isFalse();
    }

    @Test
    void transportEventShouldWakeCallback() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<Object> woke = new CopyOnWriteArrayList<>();
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, event -> {
            woke.add(event);
            latch.countDown();
        });

        loop.start();
        EventMessage event = EventMessage.builder()
                .eventType("message")
                .payload(Map.of("content", "hello"))
                .build();
        loop.enqueue(event);

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        loop.stop();

        assertThat(woke).singleElement().isSameAs(event);
        assertThat(CoordinatorLoop.isTransportEvent(woke.get(0))).isTrue();
    }

    @Test
    void multipleEventsShouldProcessInOrder() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(3);
        List<String> eventTypes = new CopyOnWriteArrayList<>();
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.MEMBER, event -> {
            if (event instanceof EventMessage transportEvent) {
                eventTypes.add(transportEvent.getEventType());
                latch.countDown();
            }
        });

        loop.start();
        loop.enqueue(EventMessage.builder().eventType("message").build());
        loop.enqueue(EventMessage.builder().eventType("task_completed").build());
        loop.enqueue(EventMessage.builder().eventType("broadcast").build());

        assertThat(latch.await(1, TimeUnit.SECONDS)).isTrue();
        loop.stop();

        assertThat(eventTypes).containsExactly("message", "task_completed", "broadcast");
    }

    @Test
    void pollTimerShouldEnqueueMailboxAndTaskEvents() throws InterruptedException {
        AtomicInteger mailboxPolls = new AtomicInteger();
        AtomicInteger taskPolls = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(4);
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, event -> {
            if (CoordinatorLoop.isMailboxPollEvent(event)) {
                mailboxPolls.incrementAndGet();
                latch.countDown();
            } else if (CoordinatorLoop.isTaskPollEvent(event)) {
                taskPolls.incrementAndGet();
                latch.countDown();
            }
        }, 25L, 25L);

        loop.start();

        assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
        loop.stop();

        assertThat(mailboxPolls.get()).isGreaterThanOrEqualTo(2);
        assertThat(taskPolls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void pausePollsShouldStopPollingButKeepMainLoopRunning() throws InterruptedException {
        AtomicInteger pollCount = new AtomicInteger();
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, event -> {
            if (CoordinatorLoop.isMailboxPollEvent(event) || CoordinatorLoop.isTaskPollEvent(event)) {
                pollCount.incrementAndGet();
            }
        }, 20L, 20L);

        loop.start();
        Thread.sleep(120L);
        int beforePause = pollCount.get();

        loop.pausePolls();
        assertThat(loop.isPollsPaused()).isTrue();
        assertThat(loop.isRunning()).isTrue();

        Thread.sleep(120L);
        int afterPause = pollCount.get();
        loop.stop();

        assertThat(beforePause).isGreaterThan(0);
        assertThat(afterPause).isBetween(beforePause, beforePause + 2);
    }

    @Test
    void resumePollsShouldRestartPolling() throws InterruptedException {
        AtomicInteger pollCount = new AtomicInteger();
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, event -> {
            if (CoordinatorLoop.isMailboxPollEvent(event) || CoordinatorLoop.isTaskPollEvent(event)) {
                pollCount.incrementAndGet();
            }
        }, 20L, 20L);

        loop.start();
        loop.pausePolls();
        int pausedCount = pollCount.get();

        Thread.sleep(80L);
        assertThat(pollCount.get()).isEqualTo(pausedCount);

        loop.resumePolls();
        assertThat(loop.isPollsPaused()).isFalse();

        Thread.sleep(120L);
        loop.stop();

        assertThat(pollCount.get()).isGreaterThan(pausedCount);
    }

    @Test
    void pauseAndResumeShouldBeIdempotent() {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null, 20L, 20L);

        loop.start();
        loop.pausePolls();
        loop.pausePolls();
        assertThat(loop.isPollsPaused()).isTrue();

        loop.resumePolls();
        loop.resumePolls();
        assertThat(loop.isPollsPaused()).isFalse();
        loop.stop();
    }

    @Test
    void stopShouldResetPauseFlag() {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null, 20L, 20L);

        loop.start();
        loop.pausePolls();
        assertThat(loop.isPollsPaused()).isTrue();

        loop.stop();

        assertThat(loop.isRunning()).isFalse();
        assertThat(loop.isPollsPaused()).isFalse();
    }

    @Test
    void userInputEventHelperShouldMatchInnerEventType() {
        InnerEventMessage event = InnerEventMessage.builder()
                .eventType(InnerEventType.USER_INPUT)
                .payload(Map.of("content", "hello"))
                .build();

        assertThat(CoordinatorLoop.isUserInputEvent(event)).isTrue();
        assertThat(CoordinatorLoop.isMailboxPollEvent(event)).isFalse();
        assertThat(CoordinatorLoop.isTaskPollEvent(event)).isFalse();
        assertThat(CoordinatorLoop.isShutdownEvent(event)).isFalse();
    }
}
