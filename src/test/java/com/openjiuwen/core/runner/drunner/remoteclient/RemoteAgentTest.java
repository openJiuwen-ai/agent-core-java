// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.RunnerConfig;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RemoteAgent.
 * 
 * <p>Note: The Python test_remote_agent.py contains integration tests that depend on
 * Runner + AgentAdapter. Those will be converted in Batch 8.
 * These are unit tests for RemoteAgent's error translation logic.
 * 
 * 对应Python: tests/unit_tests/core/runner/drunner/remote_client/test_remote_agent.py (unit部分)
 */
@ExtendWith(MockitoExtension.class)
class RemoteAgentTest {

    @Mock
    RemoteClient mockClient;

    private RunnerConfig runnerConfig;

    @BeforeEach
    void setUp() {
        // 设置RunnerConfig
        runnerConfig = new RunnerConfig();
        DistributedConfig distConfig = new DistributedConfig();
        distConfig.setRequestTimeout(30.0);
        runnerConfig.setDistributedConfig(distConfig);
        RunnerConfig.setRunnerConfig(runnerConfig);
    }

    @AfterEach
    void tearDown() {
        RunnerConfig.setRunnerConfig(null);
    }

    /**
     * 创建带mock客户端的RemoteAgent
     */
    private RemoteAgent createAgentWithMockClient(String agentId) {
        RemoteAgent agent = new RemoteAgent(agentId);
        agent.setClient(mockClient);
        return agent;
    }

    // ==========================================
    // 构造函数测试
    // ==========================================

    @Nested
    @DisplayName("构造函数测试")
    class ConstructorTests {

        @Test
        @DisplayName("简单构造函数正确存储agentId")
        void testSimpleConstructor() {
            RemoteAgent agent = new RemoteAgent("test-agent");
            assertEquals("test-agent", agent.getAgentId());
        }

        @Test
        @DisplayName("完整构造函数正确设置所有字段")
        void testFullConstructor() {
            RemoteAgent agent = new RemoteAgent("test-agent", "v1", "Test agent",
                "custom.topic.v1", ProtocolEnum.MQ, Map.of("key", "value"));

            assertEquals("test-agent", agent.getAgentId());
            assertEquals("v1", agent.getVersion());
            assertEquals("Test agent", agent.getDescription());
            assertEquals("custom.topic.v1", agent.getTopic());
            assertEquals(ProtocolEnum.MQ, agent.getProtocol());
            assertNotNull(agent.getConfig());
            assertEquals("test-agent", agent.getConfig().getId());
            assertEquals("custom.topic.v1", agent.getConfig().getTopic());
        }

        @Test
        @DisplayName("未提供topic时使用配置模板生成")
        void testTopicFromTemplate() {
            RemoteAgent agent = new RemoteAgent("weather-agent", "v1", null,
                null, ProtocolEnum.MQ, null);

            // 模板: openjiuwen.single_agent.{agent_id}.{version}
            assertEquals("openjiuwen.single_agent.weather-agent.v1", agent.getTopic());
        }

        @Test
        @DisplayName("便捷构造函数使用默认协议")
        void testConvenienceConstructor() {
            RemoteAgent agent = new RemoteAgent("my-agent", "");
            assertEquals("my-agent", agent.getAgentId());
            assertEquals("", agent.getVersion());
            assertEquals(ProtocolEnum.MQ, agent.getProtocol());
        }
    }

    // ==========================================
    // invoke()测试
    // ==========================================

    @Nested
    @DisplayName("invoke()测试")
    class InvokeTests {

        @Test
        @DisplayName("invoke()调用client.start()和client.invoke()并返回结果")
        void testInvokeReturnsResult() throws Exception {
            when(mockClient.invoke(any(), any())).thenReturn(Map.of("result", "success"));

            RemoteAgent agent = createAgentWithMockClient("test-agent");
            Object result = agent.invoke(Map.of("query", "hello"), 10.0);

            verify(mockClient).start();
            verify(mockClient).invoke(Map.of("query", "hello"), 10.0);
            assertEquals(Map.of("result", "success"), result);
        }

        @Test
        @DisplayName("invoke()将CancellationException转换为REMOTE_AGENT_REQUEST_CANCELLED")
        void testInvokeTranslatesCancelledError() throws Exception {
            when(mockClient.invoke(any(), any())).thenThrow(new CancellationException());

            RemoteAgent agent = createAgentWithMockClient("test-agent");
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class,
                () -> agent.invoke(Map.of("query", "hello"), 10.0));

            assertEquals(StatusCode.REMOTE_AGENT_REQUEST_CANCELLED.getCode(), ex.getErrorCode());
        }

        @Test
        @DisplayName("invoke()将TimeoutException转换为REMOTE_AGENT_REQUEST_TIMEOUT")
        void testInvokeTranslatesTimeoutError() throws Exception {
            when(mockClient.invoke(any(), any())).thenThrow(new TimeoutException("timeout"));

            RemoteAgent agent = createAgentWithMockClient("test-agent");
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class,
                () -> agent.invoke(Map.of("query", "hello"), 10.0));

            assertEquals(StatusCode.REMOTE_AGENT_REQUEST_TIMEOUT.getCode(), ex.getErrorCode());
        }

        @Test
        @DisplayName("invoke()传递JiuWenBaseException不做转换")
        void testInvokePassesThroughJiuWenException() throws Exception {
            when(mockClient.invoke(any(), any())).thenThrow(
                new JiuWenBaseException(500, "Remote error"));

            RemoteAgent agent = createAgentWithMockClient("test-agent");
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class,
                () -> agent.invoke(Map.of("query", "hello"), 10.0));

            // 原始异常直接传播（不被CancellationException/TimeoutException catch）
            assertEquals(500, ex.getErrorCode());
        }
    }

    // ==========================================
    // stream()测试
    // ==========================================

    @Nested
    @DisplayName("stream()测试")
    class StreamTests {

        @Test
        @DisplayName("stream()调用client.start()和client.stream()并返回结果")
        void testStreamReturnsChunks() throws Exception {
            when(mockClient.stream(any(), any())).thenReturn(
                List.of(Map.of("chunk", 1), Map.of("chunk", 2)));

            RemoteAgent agent = createAgentWithMockClient("test-agent");
            List<Object> result = agent.stream(Map.of("prompt", "generate"), 10.0);

            verify(mockClient).start();
            verify(mockClient).stream(Map.of("prompt", "generate"), 10.0);
            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("stream()将CancellationException转换为REMOTE_AGENT_REQUEST_CANCELLED")
        void testStreamTranslatesCancelledError() throws Exception {
            when(mockClient.stream(any(), any())).thenThrow(new CancellationException());

            RemoteAgent agent = createAgentWithMockClient("test-agent");
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class,
                () -> agent.stream(Map.of("prompt", "generate"), 10.0));

            assertEquals(StatusCode.REMOTE_AGENT_REQUEST_CANCELLED.getCode(), ex.getErrorCode());
        }

        @Test
        @DisplayName("stream()将TimeoutException转换为REMOTE_AGENT_REQUEST_TIMEOUT")
        void testStreamTranslatesTimeoutError() throws Exception {
            when(mockClient.stream(any(), any())).thenThrow(new TimeoutException("timeout"));

            RemoteAgent agent = createAgentWithMockClient("test-agent");
            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class,
                () -> agent.stream(Map.of("prompt", "generate"), 10.0));

            assertEquals(StatusCode.REMOTE_AGENT_REQUEST_TIMEOUT.getCode(), ex.getErrorCode());
        }
    }
}

