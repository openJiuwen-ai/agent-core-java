package com.openjiuwen.core.runner.drunner.server_adapter;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.mq.MessageQueueBase;
import com.openjiuwen.core.runner.mq.QueueMessage;
import com.openjiuwen.core.runner.mq.SubscriptionBase;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class MqServerAdapterCompatibilityTest {

    @Test
    void mqMessageUtilsShouldBuildStreamBatchAndErrorResponses() {
        DmqRequestMessage request = new DmqRequestMessage();
        request.setMessageId("msg-1");
        request.setSenderId("caller");
        request.setRequestId("req-1");

        DmqResponseMessage stream = MqMessageUtils.buildStreamResponse(request, "adapter", "chunk", 0, false);
        DmqResponseMessage batch = MqMessageUtils.buildBatchResponse(request, "adapter", Map.of("ok", true));
        DmqResponseMessage error = MqMessageUtils.buildErrorResponse(request, "adapter", new IllegalStateException("boom"));

        assertThat(stream.getReceiverId()).isEqualTo("caller");
        assertThat(stream.isLastChunk()).isFalse();
        assertThat(batch.isLastChunk()).isTrue();
        assertThat(error.getErrorMsg()).contains("boom");
    }

    @Test
    void mqServerAdapterShouldIgnoreExpiredAndHandleStopMessages() throws Exception {
        MqServerAdapter adapter = new MqServerAdapter(
                "agent-1",
                "agent.topic",
                inputs -> Map.of("ok", true),
                inputs -> List.<Object>of("a", "b").iterator()
        );

        FakeMessageQueue mq = new FakeMessageQueue();
        setField(adapter, "mq", mq);
        setField(adapter, "active", true);

        DmqRequestMessage expired = new DmqRequestMessage();
        expired.setMessageId("expired-1");
        expired.setType(DMessageType.INPUT);
        expired.setExpireAt((System.currentTimeMillis() / 1000.0) - 1);
        invokeHandleMessage(adapter, expired);

        assertThat(mq.produced).isEmpty();

        DmqRequestMessage stop = new DmqRequestMessage();
        stop.setMessageId("stop-1");
        stop.setType(DMessageType.STOP);
        invokeHandleMessage(adapter, stop);

        assertThat(mq.produced).isEmpty();
    }

    @Test
    void mqServerAdapterShouldProduceBatchAndStreamResponses() throws Exception {
        MqServerAdapter adapter = new MqServerAdapter(
                "agent-2",
                "agent.topic",
                inputs -> Map.of("value", "done"),
                inputs -> List.<Object>of("c1", "c2").iterator()
        );

        FakeMessageQueue mq = new FakeMessageQueue();
        setField(adapter, "mq", mq);
        setField(adapter, "active", true);

        DmqRequestMessage batch = new DmqRequestMessage();
        batch.setMessageId("batch-1");
        batch.setSenderId("caller");
        batch.setReplyTopic("reply.topic");
        batch.setType(DMessageType.INPUT);
        batch.setBody(Map.of("query", "hello"));
        invokeProcessMessage(adapter, batch);

        DmqRequestMessage stream = new DmqRequestMessage();
        stream.setMessageId("stream-1");
        stream.setSenderId("caller");
        stream.setReplyTopic("reply.topic");
        stream.setType(DMessageType.INPUT);
        stream.setEnableStream(true);
        stream.setBody(Map.of("query", "hello"));
        invokeProcessMessage(adapter, stream);

        assertThat(mq.produced).isNotEmpty();
        assertThat(mq.produced.stream().filter(msg -> msg instanceof DmqResponseMessage).count()).isGreaterThanOrEqualTo(4);
    }

    private static void invokeHandleMessage(MqServerAdapter adapter, DmqRequestMessage message) throws Exception {
        Method method = MqServerAdapter.class.getDeclaredMethod("handleMessage", DmqRequestMessage.class);
        method.setAccessible(true);
        method.invoke(adapter, message);
    }

    private static void invokeProcessMessage(MqServerAdapter adapter, DmqRequestMessage message) throws Exception {
        Method method = MqServerAdapter.class.getDeclaredMethod("processMessage", DmqRequestMessage.class);
        method.setAccessible(true);
        method.invoke(adapter, message);
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FakeMessageQueue extends MessageQueueBase {
        private final Map<String, FakeSubscription> subscriptions = new ConcurrentHashMap<>();
        private final List<QueueMessage> produced = new ArrayList<>();

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        @Override
        public SubscriptionBase subscribe(String topic) {
            FakeSubscription sub = new FakeSubscription();
            subscriptions.put(topic, sub);
            return sub;
        }

        @Override
        public void unsubscribe(String topic) {
            subscriptions.remove(topic);
        }

        @Override
        public void produceMessage(String topic, QueueMessage message) {
            produced.add(message);
        }
    }

    private static final class FakeSubscription extends SubscriptionBase {
        private com.openjiuwen.core.runner.mq.AsyncMessageHandler<Object, Object> handler;

        @Override
        public void setMessageHandler(com.openjiuwen.core.runner.mq.AsyncMessageHandler<Object, Object> handler) {
            this.handler = handler;
        }

        @Override
        public void activate() {
        }

        @Override
        public void deactivate() {
        }

        void emit(Object message) {
            if (handler != null) {
                handler.handle(message);
            }
        }
    }
}
