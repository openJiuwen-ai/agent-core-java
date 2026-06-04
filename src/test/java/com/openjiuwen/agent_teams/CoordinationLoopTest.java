package com.openjiuwen.agent_teams;

import com.openjiuwen.agent_teams.agent.CoordinationEvent;
import com.openjiuwen.agent_teams.agent.CoordinatorLoop;
import com.openjiuwen.agent_teams.schema.TeamEvent;
import com.openjiuwen.agent_teams.schema.TeamRole;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's {@code tests/unit_tests/agent_teams/test_coordination_loop.py}.
 */
class CoordinationLoopTest {

    @Test
    void messageEventWakesLoop() throws Exception {
        List<CoordinationEvent> woke = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, event -> {
            woke.add(event);
            latch.countDown();
        });

        loop.start();
        loop.enqueue(new EventMessage(TeamEvent.MESSAGE, Map.of("content", "hello")));
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        loop.stop();

        assertEquals(1, woke.size());
        assertEquals(TeamEvent.MESSAGE, woke.get(0).getEventType());
    }

    @Test
    void taskEventWakesLoop() throws Exception {
        List<CoordinationEvent> woke = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(1);
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.TEAMMATE, event -> {
            woke.add(event);
            latch.countDown();
        });

        loop.start();
        loop.enqueue(new EventMessage(TeamEvent.TASK_COMPLETED, Map.of("task_id", "t1")));
        assertTrue(latch.await(500, TimeUnit.MILLISECONDS));
        loop.stop();

        assertEquals(1, woke.size());
        assertEquals(TeamEvent.TASK_COMPLETED, woke.get(0).getEventType());
    }

    @Test
    void multipleEventsWakeInOrder() throws Exception {
        List<String> eventTypes = new ArrayList<>();
        CountDownLatch latch = new CountDownLatch(3);
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, event -> {
            eventTypes.add(event.getEventType());
            latch.countDown();
        });

        loop.start();
        loop.enqueue(new EventMessage(TeamEvent.MESSAGE, Map.of()));
        loop.enqueue(new EventMessage(TeamEvent.TASK_COMPLETED, Map.of()));
        loop.enqueue(new EventMessage(TeamEvent.BROADCAST, Map.of()));
        assertTrue(latch.await(1, TimeUnit.SECONDS));
        loop.stop();

        assertEquals(List.of(TeamEvent.MESSAGE, TeamEvent.TASK_COMPLETED, TeamEvent.BROADCAST), eventTypes);
    }

    @Test
    void noCallbackDoesNotCrash() throws Exception {
        CoordinatorLoop loop = new CoordinatorLoop(TeamRole.LEADER, null);

        loop.start();
        loop.enqueue(new EventMessage(TeamEvent.MESSAGE, Map.of()));
        Thread.sleep(75L);
        loop.stop();

        assertTrue(true);
    }
}
