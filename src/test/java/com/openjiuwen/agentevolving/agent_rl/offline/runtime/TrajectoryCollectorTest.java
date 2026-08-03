package com.openjiuwen.agent_evolving.agent_rl.offline.runtime;

import com.openjiuwen.agent_evolving.agent_rl.RLRail;
import com.openjiuwen.agent_evolving.trajectory.Trajectory;
import com.openjiuwen.core.session.AgentSessionApi;
import com.openjiuwen.core.session.stream.StreamMode;
import com.openjiuwen.core.singleagent.BaseAgent;
import com.openjiuwen.core.singleagent.schema.AgentCard;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code TrajectoryCollector} in
 * {@code openjiuwen/agent_evolving/agent_rl/offline/runtime/collector.py}.
 */

class TrajectoryCollectorTest {

    @Test
    void rejectsAgentsWithoutRailRegistration() {
        TrajectoryCollector collector = new TrajectoryCollector();

        assertThatThrownBy(() -> collector.collectBlocking(new Object(), Map.of(), "", "offline", null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("register_rail");
    }

    @Test
    void returnsLastTrajectoryAndOverwritesRuntimeMetadata() {
        RecordingAgent agent = new RecordingAgent(false);
        TrajectoryCollector collector = new TrajectoryCollector();

        Trajectory trajectory = collector.collect(
                        agent,
                        Map.of("conversation_id", "conv-1", "query", "hello"),
                        "",
                        "offline",
                        null)
                .toCompletableFuture()
                .join();

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getExecutionId()).isEqualTo("traj-2");
        assertThat(trajectory.getSource()).isEqualTo("offline");
        assertThat(trajectory.getSessionId()).isEqualTo("conv-1");
        assertThat(trajectory.getCaseId()).isEqualTo("conv-1");
        assertThat(agent.registeredRail).isSameAs(agent.unregisteredRail);
        assertThat(agent.invokedSession).isNotNull();
    }

    @Test
    void returnsPartialTrajectoryWhenInvokeFailsAndStillUnregistersRail() {
        RecordingAgent agent = new RecordingAgent(true);
        TrajectoryCollector collector = new TrajectoryCollector();

        Trajectory trajectory = collector.collect(
                        agent,
                        Map.of("conversation_id", "conv-2"),
                        "explicit-session",
                        "offline-eval",
                        "case-9")
                .toCompletableFuture()
                .join();

        assertThat(trajectory).isNotNull();
        assertThat(trajectory.getExecutionId()).isEqualTo("traj-2");
        assertThat(trajectory.getSource()).isEqualTo("offline-eval");
        assertThat(trajectory.getSessionId()).isEqualTo("explicit-session");
        assertThat(trajectory.getCaseId()).isEqualTo("case-9");
        assertThat(agent.registeredRail).isSameAs(agent.unregisteredRail);
    }

    private static final class RecordingAgent extends BaseAgent {
        private final boolean fail;
        private RLRail registeredRail;
        private Object unregisteredRail;
        private AgentSessionApi invokedSession;

        private RecordingAgent(boolean fail) {
            super(new AgentCard("agent-1", "agent-1", "Agent"));
            this.fail = fail;
        }

        @Override
        public BaseAgent configure(Object config) {
            return this;
        }

        public CompletionStage<BaseAgent> registerRail(Object rail) {
            registeredRail = (RLRail) rail;
            return CompletableFuture.completedFuture(this);
        }

        public CompletionStage<BaseAgent> unregisterRail(Object rail) {
            unregisteredRail = rail;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletionStage<Object> invoke(Object inputs, AgentSessionApi session) {
            invokedSession = session;
            registeredRail.getTrajectoryStore().save(trajectory("traj-1"), null);
            registeredRail.getTrajectoryStore().save(trajectory("traj-2"), null);
            if (fail) {
                CompletableFuture<Object> failed = new CompletableFuture<>();
                failed.completeExceptionally(new IllegalStateException("boom"));
                return failed;
            }
            return CompletableFuture.completedFuture("ok");
        }

        @Override
        public Iterator<Object> stream(Object inputs, AgentSessionApi session, List<StreamMode> streamModes) {
            return List.of().iterator();
        }

        private static Trajectory trajectory(String executionId) {
            return Trajectory.builder()
                    .executionId(executionId)
                    .source("raw")
                    .sessionId("raw-session")
                    .caseId("raw-case")
                    .build();
        }
    }
}
