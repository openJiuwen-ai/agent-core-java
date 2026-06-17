package com.openjiuwen.extensions.checkpointer.redis;

import com.openjiuwen.core.foundation.llm.schema.AssistantMessage;
import com.openjiuwen.core.foundation.llm.schema.UserMessage;
import com.openjiuwen.core.graph.pregel.PregelConstants;
import com.openjiuwen.core.graph.store.GraphStoreState;
import com.openjiuwen.core.session.config.Config;
import com.openjiuwen.core.session.interaction.InteractiveInput;
import com.openjiuwen.core.session.internal.AgentSession;
import com.openjiuwen.core.session.internal.WorkflowSession;
import com.openjiuwen.core.session.state.InMemoryState;
import com.openjiuwen.core.session.state.WorkflowCommitState;
import com.openjiuwen.extensions.store.kv.RedisStore;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.resps.ScanResult;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@Tag("system-test")
class RedisCheckpointerJsonSystemTest {

    @Test
    void jsonCheckpointerRoundTripsThroughRealRedis() {
        assumeTrue(isDockerAvailable(), "Docker is required for Redis checkpointer JSON system test");

        try (GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
                .withExposedPorts(6379)) {
            redis.start();

            try (JedisPooled jedis = new JedisPooled(redis.getHost(), redis.getMappedPort(6379))) {
                RedisCheckpointer checkpointer = new RedisCheckpointer(
                        new RedisStore(new BinaryRedisClient(jedis)),
                        Map.of("dump_type", "json"));

                saveAgentState(checkpointer);

                String agentTypeKey = "real-redis-session:agent:agent-json:agent_state_blobs_dump_type";
                String agentBlobKey = "real-redis-session:agent:agent-json:agent_state_blobs";
                String agentJson = readUtf8(jedis, agentBlobKey);
                assertEquals("json", jedis.get(agentTypeKey));
                assertTrue(agentJson.contains("\"__jiuwenType\":\"message.user\""));
                assertTrue(agentJson.contains("\"__jiuwenType\":\"message.assistant\""));
                assertTrue(agentJson.contains("real redis user"));
                assertTrue(agentJson.contains("real redis assistant"));
                assertFalse(agentJson.contains("\"__jiuwenType\":\"java.lang.String\""));

                AgentSession recoveredAgent = new AgentSession("real-redis-session", agentConfig(), checkpointer);
                checkpointer.preAgentExecute(recoveredAgent, null);
                List<?> messages = assertInstanceOf(List.class, recoveredAgent.state().getGlobal("messages"));
                UserMessage userMessage = assertInstanceOf(UserMessage.class, messages.get(0));
                AssistantMessage assistantMessage = assertInstanceOf(AssistantMessage.class, messages.get(1));
                assertEquals("real redis user", userMessage.getContentAsString());
                assertEquals("real redis assistant", assistantMessage.getContentAsString());

                GraphStoreState graphState = GraphStoreState.create(
                        "workflow-json",
                        9,
                        Map.of("messages", List.of(new UserMessage("graph message"))),
                        List.of(),
                        Map.of(),
                        Map.of());
                checkpointer.graphStore().save("real-redis-session", "workflow-json", graphState);

                String graphTypeKey = "real-redis-session:workflow-graph:workflow-json:checkpoint_data_type";
                String graphBlobKey = "real-redis-session:workflow-graph:workflow-json:checkpoint_data_value";
                String graphJson = readUtf8(jedis, graphBlobKey);
                assertEquals("json", jedis.get(graphTypeKey));
                assertTrue(graphJson.contains("\"__jiuwenType\":\"graph.storeState\""));
                assertTrue(graphJson.contains("\"__jiuwenType\":\"message.user\""));
                assertTrue(graphJson.contains("graph message"));
                assertFalse(graphJson.contains("\"__jiuwenType\":\"java.lang.String\""));

                GraphStoreState recoveredGraph = checkpointer.graphStore()
                        .get("real-redis-session", "workflow-json")
                        .orElseThrow();
                assertEquals(9, recoveredGraph.getStep());
                List<?> graphMessages = assertInstanceOf(List.class, recoveredGraph.getChannelValues().get("messages"));
                UserMessage graphMessage = assertInstanceOf(UserMessage.class, graphMessages.get(0));
                assertEquals("graph message", graphMessage.getContentAsString());

                WorkflowSession workflow = new WorkflowSession(
                        "workflow-json", null, "real-redis-session", InMemoryState.create(), null);
                checkpointer.preWorkflowExecute(workflow, null);
                WorkflowCommitState workflowState = (WorkflowCommitState) workflow.state();
                workflowState.updateGlobal(Map.of("messages", List.of(new UserMessage("workflow state"))));
                workflowState.updateWorkflow(Map.of("step", 3));
                workflowState.commit();
                workflowState.update(Map.of("afterCommit", List.of(new AssistantMessage("workflow update"))));
                checkpointer.postWorkflowExecute(
                        workflow, Map.of(PregelConstants.TASK_STATUS_INTERRUPT, true), null);

                String workflowStateTypeKey =
                        "real-redis-session:workflow:workflow-json:workflow_state_blobs_dump_type";
                String workflowStateBlobKey =
                        "real-redis-session:workflow:workflow-json:workflow_state_blobs";
                String workflowUpdateTypeKey =
                        "real-redis-session:workflow:workflow-json:workflow_update_blobs_dump_type";
                String workflowUpdateBlobKey =
                        "real-redis-session:workflow:workflow-json:workflow_update_blobs";
                String workflowStateJson = readUtf8(jedis, workflowStateBlobKey);
                String workflowUpdateJson = readUtf8(jedis, workflowUpdateBlobKey);
                assertEquals("json", jedis.get(workflowStateTypeKey));
                assertEquals("json", jedis.get(workflowUpdateTypeKey));
                assertTrue(workflowStateJson.contains("\"__jiuwenType\":\"message.user\""));
                assertTrue(workflowStateJson.contains("workflow state"));
                assertTrue(workflowUpdateJson.contains("\"__jiuwenType\":\"message.assistant\""));
                assertTrue(workflowUpdateJson.contains("workflow update"));

                WorkflowSession recoveredWorkflow = new WorkflowSession(
                        "workflow-json", null, "real-redis-session", InMemoryState.create(), null);
                checkpointer.preWorkflowExecute(recoveredWorkflow, new InteractiveInput());
                WorkflowCommitState recoveredWorkflowState = (WorkflowCommitState) recoveredWorkflow.state();
                List<?> workflowMessages = assertInstanceOf(
                        List.class, recoveredWorkflow.state().getGlobal("messages"));
                UserMessage workflowMessage = assertInstanceOf(UserMessage.class, workflowMessages.get(0));
                assertEquals("workflow state", workflowMessage.getContentAsString());
                assertEquals(3, recoveredWorkflowState.getWorkflow("step"));
                recoveredWorkflowState.commit();
                List<?> workflowUpdates = assertInstanceOf(List.class, recoveredWorkflowState.get("afterCommit"));
                AssistantMessage workflowUpdate = assertInstanceOf(AssistantMessage.class, workflowUpdates.get(0));
                assertEquals("workflow update", workflowUpdate.getContentAsString());
            }
        }
    }

    private static void saveAgentState(RedisCheckpointer checkpointer) {
        AgentSession session = new AgentSession("real-redis-session", agentConfig(), checkpointer);
        session.state().updateGlobal(Map.of("messages", List.of(
                new UserMessage("real redis user"),
                new AssistantMessage("real redis assistant"))));

        checkpointer.postAgentExecute(session);
    }

    private static Config agentConfig() {
        Config config = new Config();
        config.setAgentConfig(new Config.MetadataLike("agent-json", "agent", "invoke"));
        return config;
    }

    private static String readUtf8(JedisPooled jedis, String key) {
        byte[] value = jedis.get(key.getBytes(StandardCharsets.UTF_8));
        return value == null ? null : new String(value, StandardCharsets.UTF_8);
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }

    static final class BinaryRedisClient {
        private final JedisPooled jedis;

        private BinaryRedisClient(JedisPooled jedis) {
            this.jedis = jedis;
        }

        public void set(String key, Object value) {
            setValue(key, value);
        }

        public boolean set(String key, Object value, boolean nx, Integer expiry) {
            if (nx && exists(key) > 0L) {
                return false;
            }
            setValue(key, value);
            if (expiry != null && expiry > 0) {
                expire(key, expiry);
            }
            return true;
        }

        public Object get(String key) {
            byte[] value = jedis.get(keyBytes(key));
            if (value == null) {
                return null;
            }
            String text = new String(value, StandardCharsets.UTF_8);
            if ("json".equals(text) || "java".equals(text)) {
                return text;
            }
            return value;
        }

        public long exists(String key) {
            return Boolean.TRUE.equals(jedis.exists(key)) ? 1L : 0L;
        }

        public long delete(String... keys) {
            return jedis.del(keys);
        }

        public List<Object> mget(String... keys) {
            List<Object> values = new ArrayList<>(keys.length);
            for (String key : keys) {
                values.add(get(key));
            }
            return values;
        }

        public List<String> scanIter(String pattern) {
            List<String> keys = new ArrayList<>();
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(pattern).count(100);
            do {
                ScanResult<String> result = jedis.scan(cursor, params);
                keys.addAll(result.getResult());
                cursor = result.getCursor();
            } while (!ScanParams.SCAN_POINTER_START.equals(cursor));
            keys.sort(String::compareTo);
            return keys;
        }

        public boolean expire(String key, int ttlSeconds) {
            return jedis.expire(key, ttlSeconds) > 0L;
        }

        public BinaryRedisPipeline pipeline() {
            return new BinaryRedisPipeline(this);
        }

        private void setValue(String key, Object value) {
            if (value instanceof byte[] bytes) {
                jedis.set(keyBytes(key), bytes);
            } else {
                jedis.set(key, String.valueOf(value));
            }
        }

        private byte[] keyBytes(String key) {
            return key.getBytes(StandardCharsets.UTF_8);
        }
    }

    static final class BinaryRedisPipeline {
        private final BinaryRedisClient client;
        private final List<Runnable> operations = new ArrayList<>();

        private BinaryRedisPipeline(BinaryRedisClient client) {
            this.client = client;
        }

        public BinaryRedisPipeline expire(String key, int ttlSeconds) {
            operations.add(() -> client.expire(key, ttlSeconds));
            return this;
        }

        public List<Object> execute() {
            operations.forEach(Runnable::run);
            operations.clear();
            return List.of();
        }
    }
}
