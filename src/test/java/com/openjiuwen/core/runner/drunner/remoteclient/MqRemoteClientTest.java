// Copyright (c) Huawei Technologies Co., Ltd. 2025. All rights reserved.
package com.openjiuwen.core.runner.drunner.remoteclient;

import com.openjiuwen.core.common.exception.JiuWenBaseException;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.DistributedConfig;
import com.openjiuwen.core.runner.MessageQueueBase;
import com.openjiuwen.core.runner.Runner;
import com.openjiuwen.core.runner.RunnerConfig;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessagequeue.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription.ReplyTopicSubscription;
import com.openjiuwen.core.runner.drunner.dmessagequeue.dsubscription.ResponseCollector;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for MqRemoteClient.
 * 
 * 对应Python: tests/unit_tests/core/runner/drunner/remote_client/test_mq_remote_client.py
 */
@ExtendWith(MockitoExtension.class)
class MqRemoteClientTest {

    @Mock
    MessageQueueBase mq;

    @Mock
    ReplyTopicSubscription replySub;

    @Mock
    ResponseCollector collector;

    private RemoteClientConfig clientConfig;
    private RunnerConfig runnerConfig;

    @BeforeEach
    void setUp() {
        // 创建客户端配置
        clientConfig = new RemoteClientConfig();
        clientConfig.setId("test-agent");
        clientConfig.setProtocol(ProtocolEnum.MQ.getValue());
        clientConfig.setTopic("openjiuwen.single_agent.test-agent.v1");

        // 设置全局RunnerConfig
        runnerConfig = new RunnerConfig();
        DistributedConfig distConfig = new DistributedConfig();
        distConfig.setRequestTimeout(30.0);
        runnerConfig.setDistributedConfig(distConfig);
        RunnerConfig.setRunnerConfig(runnerConfig);
    }

    @AfterEach
    void tearDown() {
        RunnerConfig.setRunnerConfig(null);
        Runner.setDistPubsub(null);
        Runner.setSystemReplySub(null);
    }

    /**
     * 创建已启动的客户端（模拟start()后的状态）
     */
    private MqRemoteClient createStartedClient() {
        MqRemoteClient client = new MqRemoteClient(clientConfig);
        client.started = true;
        client.mq = mq;
        client.systemReplySub = replySub;
        client.replyTopic = "reply.test.instance";
        return client;
    }

    // ==========================================
    // TestMqRemoteClientInit - 初始化测试
    // ==========================================

    @Nested
    @DisplayName("初始化测试")
    class InitTests {

        @Test
        @DisplayName("初始化正确存储配置值")
        void testInitStoresConfigValues() {
            MqRemoteClient client = new MqRemoteClient(clientConfig);
            assertEquals("openjiuwen.single_agent.test-agent.v1", client.getTopic());
            assertEquals("test-agent", client.getRemoteId());
            assertSame(clientConfig, client.getConfig());
        }

        @Test
        @DisplayName("初始化设置默认状态")
        void testInitSetsDefaultState() {
            MqRemoteClient client = new MqRemoteClient(clientConfig);
            assertFalse(client.isStarted());
            assertNull(client.getMq());
            assertNull(client.getSystemReplySub());
        }
    }

    // ==========================================
    // TestMqRemoteClientStart - start()测试
    // ==========================================

    @Nested
    @DisplayName("start()测试")
    class StartTests {

        @Test
        @DisplayName("start()从Runner获取dist_pubsub和system_reply_sub")
        void testStartInitializesFromRunner() {
            when(replySub.getTopic()).thenReturn("reply.test.instance");
            Runner.setDistPubsub(mq);
            Runner.setSystemReplySub(replySub);

            MqRemoteClient client = new MqRemoteClient(clientConfig);
            client.start();

            assertTrue(client.isStarted());
            assertSame(mq, client.getMq());
            assertSame(replySub, client.getSystemReplySub());
            assertEquals("reply.test.instance", client.getReplyTopic());
        }

        @Test
        @DisplayName("重复调用start()时幂等")
        void testStartIsIdempotentWhenAlreadyStarted() {
            MqRemoteClient client = new MqRemoteClient(clientConfig);
            client.started = true;
            client.mq = mq;
            MessageQueueBase originalMq = client.mq;

            client.start();

            assertSame(originalMq, client.mq);
        }

        @Test
        @DisplayName("system_reply_sub为None时抛出RUNNER_DISTRIBUTED_MODE_REQUIRED异常")
        void testStartRaisesWhenSystemReplySubIsNone() {
            Runner.setDistPubsub(mq);
            Runner.setSystemReplySub(null);

            MqRemoteClient client = new MqRemoteClient(clientConfig);

            JiuWenBaseException ex = assertThrows(JiuWenBaseException.class, client::start);
            assertEquals(StatusCode.RUNNER_DISTRIBUTED_MODE_REQUIRED.getCode(), ex.getErrorCode());
        }
    }

    // ==========================================
    // TestMqRemoteClientStop - stop()测试
    // ==========================================

    @Nested
    @DisplayName("stop()测试")
    class StopTests {

        @Test
        @DisplayName("stop()将_started设置为False")
        void testStopSetsStartedFalse() {
            MqRemoteClient client = createStartedClient();
            assertTrue(client.isStarted());

            client.stop();

            assertFalse(client.isStarted());
        }
    }

    // ==========================================
    // TestMqRemoteClientInvoke - invoke()测试
    // ==========================================

    @Nested
    @DisplayName("invoke()测试")
    class InvokeTests {

        @Test
        @DisplayName("invoke()发送消息并返回结果")
        void testInvokeSendsMessageAndReturnsResult() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenReturn(Map.of("result", "success"));

            MqRemoteClient client = createStartedClient();
            Object result = client.invoke(Map.of("query", "hello"), 10.0);

            // 验证register_collector被调用
            verify(replySub).registerCollector(anyString(), eq("test-agent"), isNull(), eq(10.0));

            // 验证produce_message被调用
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mq).produceMessage(eq("openjiuwen.single_agent.test-agent.v1"), msgCaptor.capture());
            DmqRequestMessage sentMsg = (DmqRequestMessage) msgCaptor.getValue();
            assertEquals(DMessageType.INPUT.getValue(), sentMsg.getType());
            assertFalse(sentMsg.isEnableStream());
            assertEquals(Map.of("query", "hello"), sentMsg.getPayload());

            // 验证返回结果
            assertEquals(Map.of("result", "success"), result);

            // 验证unregister_collector被调用
            verify(replySub).unregisterCollector(anyString(), eq("test-agent"), isNull());
        }

        @Test
        @DisplayName("invoke()使用conversation_id构建message_id")
        void testInvokeUsesConversationIdInMessageId() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenReturn(Map.of());

            MqRemoteClient client = createStartedClient();
            client.invoke(Map.of("query", "hello", "conversation_id", "session-123"), 10.0);

            // 验证message_id包含conversation_id
            ArgumentCaptor<String> messageIdCaptor = ArgumentCaptor.forClass(String.class);
            verify(replySub).registerCollector(messageIdCaptor.capture(), anyString(), any(), any());
            assertTrue(messageIdCaptor.getValue().startsWith("session-123_"));
        }

        @Test
        @DisplayName("invoke() timeout=0时转换为None")
        void testInvokeTimeoutZeroConvertedToNone() throws Exception {
            runnerConfig.getDistributedConfig().setRequestTimeout(0);

            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenReturn(Map.of());

            MqRemoteClient client = createStartedClient();
            client.invoke(Map.of("query", "hello"), null);

            // 验证message的expire_at为null（因为timeout=null after 0→null conversion）
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mq).produceMessage(anyString(), msgCaptor.capture());
            DmqRequestMessage sentMsg = (DmqRequestMessage) msgCaptor.getValue();
            assertNull(sentMsg.getExpireAt());
        }

        @Test
        @DisplayName("invoke()未指定timeout时使用配置的request_timeout")
        void testInvokeUsesConfigTimeoutWhenNotSpecified() throws Exception {
            runnerConfig.getDistributedConfig().setRequestTimeout(60.0);

            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenReturn(Map.of());

            MqRemoteClient client = createStartedClient();
            client.invoke(Map.of("query", "hello"), null);

            // 验证register_collector被调用时ttl=60.0
            verify(replySub).registerCollector(anyString(), anyString(), any(), eq(60.0));
        }

        @Test
        @DisplayName("invoke()被取消时发送STOP消息")
        void testInvokeSendsStopOnCancelledError() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenThrow(new CancellationException());

            MqRemoteClient client = createStartedClient();
            assertThrows(CancellationException.class,
                () -> client.invoke(Map.of("query", "hello"), 10.0));

            // 验证发送了两次消息：一次INPUT，一次STOP
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mq, times(2)).produceMessage(anyString(), msgCaptor.capture());
            DmqRequestMessage stopMsg = (DmqRequestMessage) msgCaptor.getAllValues().get(1);
            assertEquals(DMessageType.STOP.getValue(), stopMsg.getType());
        }

        @Test
        @DisplayName("invoke()超时时重新抛出TimeoutError")
        void testInvokeReraisesTimeoutError() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenThrow(new TimeoutException("Request timed out"));

            MqRemoteClient client = createStartedClient();
            assertThrows(TimeoutException.class,
                () -> client.invoke(Map.of("query", "hello"), 10.0));
        }

        @Test
        @DisplayName("invoke()收到JiuWenBaseException时重新抛出")
        void testInvokeReraisesJiuWenException() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenThrow(new JiuWenBaseException(500, "Remote error"));

            MqRemoteClient client = createStartedClient();
            assertThrows(JiuWenBaseException.class,
                () -> client.invoke(Map.of("query", "hello"), 10.0));
        }

        @Test
        @DisplayName("invoke()无论成功还是失败都会注销collector")
        void testInvokeAlwaysUnregistersCollector() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.result(any())).thenThrow(new RuntimeException("Unexpected error"));

            MqRemoteClient client = createStartedClient();
            assertThrows(RuntimeException.class,
                () -> client.invoke(Map.of("query", "hello"), 10.0));

            // 验证unregister_collector仍然被调用
            verify(replySub).unregisterCollector(anyString(), eq("test-agent"), isNull());
        }
    }

    // ==========================================
    // TestMqRemoteClientStream - stream()测试
    // ==========================================

    @Nested
    @DisplayName("stream()测试")
    class StreamTests {

        @Test
        @DisplayName("stream()返回collector.stream()的结果")
        void testStreamYieldsResults() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.stream(any())).thenReturn(List.of(
                Map.of("chunk", 1), Map.of("chunk", 2), Map.of("chunk", 3)));

            MqRemoteClient client = createStartedClient();
            List<Object> results = client.stream(Map.of("prompt", "generate"), 10.0);

            assertEquals(3, results.size());
            assertEquals(Map.of("chunk", 1), results.get(0));
            assertEquals(Map.of("chunk", 2), results.get(1));
            assertEquals(Map.of("chunk", 3), results.get(2));

            // 验证enable_stream=true
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mq).produceMessage(anyString(), msgCaptor.capture());
            DmqRequestMessage sentMsg = (DmqRequestMessage) msgCaptor.getValue();
            assertTrue(sentMsg.isEnableStream());
        }

        @Test
        @DisplayName("stream()被取消时发送STOP消息")
        void testStreamSendsStopOnCancelledError() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.stream(any())).thenThrow(new CancellationException());

            MqRemoteClient client = createStartedClient();
            assertThrows(CancellationException.class,
                () -> client.stream(Map.of("prompt", "generate"), 10.0));

            // 验证发送了STOP消息
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mq, times(2)).produceMessage(anyString(), msgCaptor.capture());
            DmqRequestMessage stopMsg = (DmqRequestMessage) msgCaptor.getAllValues().get(1);
            assertEquals(DMessageType.STOP.getValue(), stopMsg.getType());
        }

        @Test
        @DisplayName("stream() timeout=0时转换为None")
        void testStreamTimeoutZeroConvertedToNone() throws Exception {
            runnerConfig.getDistributedConfig().setRequestTimeout(0);

            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.stream(any())).thenReturn(List.of(Map.of("chunk", 1)));

            MqRemoteClient client = createStartedClient();
            client.stream(Map.of("prompt", "generate"), null);

            // 验证message的expire_at为null
            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mq).produceMessage(anyString(), msgCaptor.capture());
            DmqRequestMessage sentMsg = (DmqRequestMessage) msgCaptor.getValue();
            assertNull(sentMsg.getExpireAt());
        }

        @Test
        @DisplayName("stream()超时时重新抛出TimeoutError")
        void testStreamReraisesTimeoutError() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.stream(any())).thenThrow(new TimeoutException("Stream timed out"));

            MqRemoteClient client = createStartedClient();
            assertThrows(TimeoutException.class,
                () -> client.stream(Map.of("prompt", "generate"), 10.0));
        }

        @Test
        @DisplayName("stream()无论成功还是失败都会注销collector")
        void testStreamAlwaysUnregistersCollector() throws Exception {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));
            when(replySub.registerCollector(anyString(), anyString(), any(), any())).thenReturn(collector);
            when(collector.stream(any())).thenThrow(new RuntimeException("Unexpected error"));

            MqRemoteClient client = createStartedClient();
            assertThrows(RuntimeException.class,
                () -> client.stream(Map.of("prompt", "generate"), 10.0));

            // 验证unregister_collector仍然被调用
            verify(replySub).unregisterCollector(anyString(), eq("test-agent"), isNull());
        }
    }

    // ==========================================
    // TestMqRemoteClientSendStopMessage - _send_stop_message()测试
    // ==========================================

    @Nested
    @DisplayName("sendStopMessage()测试")
    class SendStopMessageTests {

        @Test
        @DisplayName("sendStopMessage构建正确的STOP消息")
        void testSendStopMessageBuildsCorrectMessage() {
            when(mq.produceMessage(anyString(), any())).thenReturn(CompletableFuture.completedFuture(null));

            MqRemoteClient client = createStartedClient();
            client.sendStopMessage("test-message-id");

            ArgumentCaptor<Object> msgCaptor = ArgumentCaptor.forClass(Object.class);
            verify(mq).produceMessage(eq("openjiuwen.single_agent.test-agent.v1"), msgCaptor.capture());
            DmqRequestMessage stopMsg = (DmqRequestMessage) msgCaptor.getValue();

            assertEquals(DMessageType.STOP.getValue(), stopMsg.getType());
            assertEquals("test-message-id", stopMsg.getMessageId());
            assertEquals(Map.of(), stopMsg.getPayload());
            assertEquals("reply.test.instance", stopMsg.getSenderId());
            assertEquals("test-agent", stopMsg.getReceiverId());
            assertNotNull(stopMsg.getExpireAt());
        }

        @Test
        @DisplayName("sendStopMessage发送失败时捕获异常并记录日志，不向上抛出")
        void testSendStopMessageCatchesExceptionSilently() {
            when(mq.produceMessage(anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Network error")));

            MqRemoteClient client = createStartedClient();
            // 不应抛出异常
            assertDoesNotThrow(() -> client.sendStopMessage("test-message-id"));

            // 验证确实尝试发送了
            verify(mq).produceMessage(anyString(), any());
        }
    }
}

