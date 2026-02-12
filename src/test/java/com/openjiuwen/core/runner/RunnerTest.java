// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.remoteclient.RemoteAgent;
import com.openjiuwen.core.runner.resourcesmanager.ResourceMgr;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for Runner.
 *
 * <p>注意：Python的test_runner.py中的test_run_workflow和test_run_tool是集成测试，
 * 依赖Workflow/AgentSession/Tool等未完全转换的模块。
 * 这里只测试Runner的核心功能：生命周期、配置、Agent执行路径（RemoteAgent部分）。
 *
 * 对应Python: tests/unit_tests/core/runner/test_runner.py
 */
@ExtendWith(MockitoExtension.class)
class RunnerTest {

    @BeforeEach
    void setUp() {
        Runner.reset();
    }

    @AfterEach
    void tearDown() {
        Runner.reset();
    }

    // ==========================================
    // 配置管理测试
    // ==========================================

    @Nested
    @DisplayName("配置管理测试")
    class ConfigTests {

        @Test
        @DisplayName("setConfig设置配置")
        void testSetConfig() {
            RunnerConfig config = new RunnerConfig();
            Runner.setConfig(config);
            assertSame(config, Runner.getConfig());
        }

        @Test
        @DisplayName("getResourceMgr返回非null")
        void testGetResourceMgr() {
            assertNotNull(Runner.getResourceMgr());
            assertInstanceOf(ResourceMgr.class, Runner.getResourceMgr());
        }

        @Test
        @DisplayName("getPubsub返回非null")
        void testGetPubsub() {
            assertNotNull(Runner.getPubsub());
            assertInstanceOf(LocalMessageQueue.class, Runner.getPubsub());
        }

        @Test
        @DisplayName("初始状态distPubsub为null")
        void testInitDistPubsubIsNull() {
            assertNull(Runner.getDistPubsub());
        }

        @Test
        @DisplayName("初始状态systemReplySub为null")
        void testInitSystemReplySubIsNull() {
            assertNull(Runner.getSystemReplySub());
        }
    }

    // ==========================================
    // 生命周期测试
    // ==========================================

    @Nested
    @DisplayName("生命周期测试")
    class LifecycleTests {

        @Test
        @DisplayName("非分布式模式start/stop成功")
        void testStartStopNonDistributed() {
            RunnerConfig config = new RunnerConfig();
            config.setDistributedMode(false);
            Runner.setConfig(config);

            assertTrue(Runner.start());
            assertTrue(Runner.stop());
        }

        @Test
        @DisplayName("分布式模式start初始化MQ和ReplyTopicSubscription")
        void testStartDistributedMode() {
            RunnerConfig config = new RunnerConfig();
            config.setDistributedMode(true);
            DistributedConfig distConfig = new DistributedConfig();
            MessageQueueConfig mqConfig = new MessageQueueConfig("FAKE", null);
            distConfig.setMessageQueueConfig(mqConfig);
            config.setDistributedConfig(distConfig);
            Runner.setConfig(config);

            assertTrue(Runner.start());

            assertNotNull(Runner.getDistPubsub());
            assertNotNull(Runner.getSystemReplySub());

            // Clean up
            assertTrue(Runner.stop());
            assertNull(Runner.getDistPubsub());
            assertNull(Runner.getSystemReplySub());
        }

        @Test
        @DisplayName("stop释放资源管理器")
        void testStopReleasesResources() {
            RunnerConfig config = new RunnerConfig();
            config.setDistributedMode(false);
            Runner.setConfig(config);
            Runner.start();
            assertTrue(Runner.stop());
        }

        @Test
        @DisplayName("reset重置所有状态")
        void testReset() {
            RunnerConfig config = new RunnerConfig();
            Runner.setConfig(config);

            Runner.reset();

            // After reset, getConfig returns DEFAULT_RUNNER_CONFIG (not null)
            // because RunnerConfig.getRunnerConfig() initializes to default if null
            assertNotNull(Runner.getResourceMgr());
            assertNull(Runner.getDistPubsub());
            assertNull(Runner.getSystemReplySub());
        }
    }

    // ==========================================
    // Agent 执行测试
    // ==========================================

    @Nested
    @DisplayName("runAgent测试")
    class RunAgentTests {

        @Test
        @DisplayName("runAgent - Agent不存在时抛出异常")
        void testRunAgentNotFound() {
            // AgentMgr.getAgent returns null for non-existent agent, which triggers AGENT_NOT_FOUND
            Exception ex = assertThrows(Exception.class,
                    () -> Runner.runAgent("nonexistent-agent", new HashMap<>(Map.of("query", "test"))));
            // Should be JiuWenBaseException with AGENT_NOT_FOUND, or wrapped as RuntimeException
            assertTrue(ex instanceof JiuWenBaseException || ex.getMessage() != null);
        }

        @Test
        @DisplayName("runAgent - RemoteAgent调用invoke并返回结果")
        void testRunAgentWithRemoteAgent() throws Exception {
            // 创建mock RemoteAgent
            RemoteAgent mockRemote = mock(RemoteAgent.class);
            when(mockRemote.invoke(any(), any())).thenReturn(Map.of("result", "success"));

            // 注册到ResourceMgr - 使用addAgent(cardId, cardName, Object agent, List<String> tags)
            Runner.getResourceMgr().addAgent("remote-agent", "Remote Agent", (Object) mockRemote, null);

            Object result = Runner.runAgent("remote-agent",
                    new HashMap<>(Map.of("query", "hello")));

            assertEquals(Map.of("result", "success"), result);
            verify(mockRemote).invoke(any(), any());
        }

        @Test
        @DisplayName("runAgent - RemoteAgent注入conversation_id")
        void testRunAgentInjectsConversationId() throws Exception {
            RemoteAgent mockRemote = mock(RemoteAgent.class);
            when(mockRemote.invoke(any(), any())).thenReturn(Map.of());

            Runner.getResourceMgr().addAgent("remote-agent-2", "Remote Agent 2", (Object) mockRemote, null);

            HashMap<String, Object> inputs = new HashMap<>();
            inputs.put("query", "hello");
            Runner.runAgent("remote-agent-2", inputs);

            // 验证inputs中被注入了conversation_id
            assertTrue(inputs.containsKey("conversation_id"));
            assertEquals("default_session", inputs.get("conversation_id"));
        }

        @Test
        @DisplayName("runAgent - 已有conversation_id时不覆盖")
        void testRunAgentDoesNotOverrideConversationId() throws Exception {
            RemoteAgent mockRemote = mock(RemoteAgent.class);
            when(mockRemote.invoke(any(), any())).thenReturn(Map.of());

            Runner.getResourceMgr().addAgent("remote-agent-3", "Remote Agent 3", (Object) mockRemote, null);

            HashMap<String, Object> inputs = new HashMap<>();
            inputs.put("query", "hello");
            inputs.put("conversation_id", "my-session-123");
            Runner.runAgent("remote-agent-3", inputs);

            assertEquals("my-session-123", inputs.get("conversation_id"));
        }
    }

    // ==========================================
    // Agent 流式执行测试
    // ==========================================

    @Nested
    @DisplayName("runAgentStreaming测试")
    class RunAgentStreamingTests {

        @Test
        @DisplayName("runAgentStreaming - Agent不存在时抛出异常")
        void testRunAgentStreamingNotFound() {
            assertThrows(Exception.class,
                    () -> Runner.runAgentStreaming("nonexistent", new HashMap<>(Map.of("query", "test"))));
        }

        @Test
        @DisplayName("runAgentStreaming - RemoteAgent调用stream并返回迭代器")
        void testRunAgentStreamingWithRemoteAgent() throws Exception {
            RemoteAgent mockRemote = mock(RemoteAgent.class);
            when(mockRemote.stream(any(), any())).thenReturn(
                    List.of(Map.of("chunk", 1), Map.of("chunk", 2)));

            Runner.getResourceMgr().addAgent("stream-agent", "Stream Agent", (Object) mockRemote, null);

            Iterator<Object> result = Runner.runAgentStreaming("stream-agent",
                    new HashMap<>(Map.of("prompt", "generate")));

            assertNotNull(result);
            assertTrue(result.hasNext());
            assertEquals(Map.of("chunk", 1), result.next());
            assertEquals(Map.of("chunk", 2), result.next());
            assertFalse(result.hasNext());
        }

        @Test
        @DisplayName("runAgentStreaming - 注入conversation_id")
        void testRunAgentStreamingInjectsConversationId() throws Exception {
            RemoteAgent mockRemote = mock(RemoteAgent.class);
            when(mockRemote.stream(any(), any())).thenReturn(List.of());

            Runner.getResourceMgr().addAgent("stream-agent-2", "Stream Agent 2", (Object) mockRemote, null);

            HashMap<String, Object> inputs = new HashMap<>();
            inputs.put("query", "hello");
            Runner.runAgentStreaming("stream-agent-2", inputs);

            assertTrue(inputs.containsKey("conversation_id"));
            assertEquals("default_session", inputs.get("conversation_id"));
        }
    }

    // ==========================================
    // setDistPubsub / setSystemReplySub 测试
    // ==========================================

    @Nested
    @DisplayName("分布式组件设置测试")
    class DistributedComponentTests {

        @Test
        @DisplayName("setDistPubsub / getDistPubsub")
        void testSetGetDistPubsub() {
            MessageQueueBase mq = mock(MessageQueueBase.class);
            Runner.setDistPubsub(mq);
            assertSame(mq, Runner.getDistPubsub());

            Runner.setDistPubsub(null);
            assertNull(Runner.getDistPubsub());
        }

        @Test
        @DisplayName("setSystemReplySub / getSystemReplySub")
        void testSetGetSystemReplySub() {
            ReplyTopicSubscription sub = mock(ReplyTopicSubscription.class);
            Runner.setSystemReplySub(sub);
            assertSame(sub, Runner.getSystemReplySub());

            Runner.setSystemReplySub(null);
            assertNull(Runner.getSystemReplySub());
        }
    }

    // ==========================================
    // runWorkflow / runAgentGroup 占位测试
    // ==========================================

    @Nested
    @DisplayName("占位方法测试")
    class PlaceholderTests {

        @Test
        @DisplayName("runWorkflow抛出UnsupportedOperationException")
        void testRunWorkflowNotImplemented() {
            assertThrows(UnsupportedOperationException.class,
                    () -> Runner.runWorkflow("test-wf", Map.of()));
        }

        @Test
        @DisplayName("runAgentGroup - group不存在时抛出JiuWenBaseException")
        void testRunAgentGroupNotFound() {
            assertThrows(JiuWenBaseException.class,
                    () -> Runner.runAgentGroup("test-group", Map.of()));
        }

        @Test
        @DisplayName("release不抛异常")
        void testReleaseNoException() {
            assertDoesNotThrow(() -> Runner.release("test-session"));
        }
    }
}
