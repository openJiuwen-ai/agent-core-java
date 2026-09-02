
package com.openjiuwen.extensions.message_queue;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.runner.MessageQueueConfig;
import com.openjiuwen.core.runner.MessageQueueType;
import com.openjiuwen.core.runner.PulsarConfig;
import com.openjiuwen.core.runner.drunner.dmessage_queue.MessageQueueFactory;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DMessageType;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqRequestMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;
import com.openjiuwen.core.runner.mq.SubscriptionBase;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

class MessageQueuePulsarCompatibilityTest {
    @Test
    void factoryShouldInstantiatePulsarExtensionByReflection() {
        MessageQueueConfig config = MessageQueueConfig.builder().type(MessageQueueType.PULSAR.getValue())
                .pulsarConfig(PulsarConfig.builder().url("pulsar://localhost:6650").build()).build();

        Object mq = MessageQueueFactory.create(config);

        assertThat(mq).isInstanceOf(MessageQueuePulsar.class);
    }

    @Test
    void pulsarExtensionShouldDispatchSerializedDistributedMessages() throws Exception {
        FakePulsarRuntime runtime = new FakePulsarRuntime();
        MessageQueuePulsar mq = new MessageQueuePulsar(PulsarConfig.builder().url("pulsar://fake").build(), runtime);
        mq.start();

        SubscriptionBase subscription = mq.subscribe("reply-topic");
        BlockingQueue<DmqResponseMessage> received = new LinkedBlockingQueue<>();
        subscription.setMessageHandler(message -> {
            if (message instanceof DmqResponseMessage response) {
                received.add(response);
            }
            return CompletableFuture.completedFuture(null);
        });
        subscription.activate();

        DmqResponseMessage response = new DmqResponseMessage();
        response.setMessageId("msg-1");
        response.setSenderId("sender-1");
        response.setReceiverId("receiver-1");
        response.setRequestId("req-1");
        response.setType(DMessageType.OUTPUT);
        response.setResultType(ResultType.MESSAGE);
        response.setBody(Map.of("answer", "ok"));
        mq.produceMessage("reply-topic", response);

        DmqResponseMessage actual = received.poll(3, TimeUnit.SECONDS);
        assertThat(actual).isNotNull();
        assertThat(actual.getMessageId()).isEqualTo("msg-1");
        assertThat(actual.getBody()).isEqualTo(Map.of("answer", "ok"));

        mq.stop();
    }

    @Test
    void pulsarExtensionShouldRoundTripRequestMessages() throws Exception {
        FakePulsarRuntime runtime = new FakePulsarRuntime();
        MessageQueuePulsar mq = new MessageQueuePulsar(PulsarConfig.builder().url("pulsar://fake").build(), runtime);
        mq.start();

        SubscriptionBase subscription = mq.subscribe("agent-topic");
        BlockingQueue<DmqRequestMessage> received = new LinkedBlockingQueue<>();
        subscription.setMessageHandler(message -> {
            if (message instanceof DmqRequestMessage request) {
                received.add(request);
            }
            return CompletableFuture.completedFuture(null);
        });
        subscription.activate();

        DmqRequestMessage request = new DmqRequestMessage();
        request.setMessageId("msg-2");
        request.setSenderId("sender-2");
        request.setReceiverId("receiver-2");
        request.setReplyTopic("reply-topic");
        request.setType(DMessageType.INPUT);
        request.setBody(Map.of("query", "hello"));
        mq.produceMessage("agent-topic", request);

        DmqRequestMessage actual = received.poll(3, TimeUnit.SECONDS);
        assertThat(actual).isNotNull();
        assertThat(actual.getReplyTopic()).isEqualTo("reply-topic");
        assertThat(actual.getBody()).isEqualTo(Map.of("query", "hello"));

        mq.stop();
    }

    static final class FakePulsarRuntime implements MessageQueuePulsar.PulsarRuntime {
        private final Map<String, BlockingQueue<MessageQueuePulsar.ReceivedMessage>> topics = new ConcurrentHashMap<>();

        @Override
        public void start() {
        }

        @Override
        public MessageQueuePulsar.PulsarProducer newProducer(String topic) {
            BlockingQueue<MessageQueuePulsar.ReceivedMessage> queue =
                topics.computeIfAbsent(topic, ignored -> new LinkedBlockingQueue<>());
            return new MessageQueuePulsar.PulsarProducer() {
                @Override
                public void send(String key, byte[] payload) {
                    queue.add(new MessageQueuePulsar.ReceivedMessage(payload, key));
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public MessageQueuePulsar.PulsarConsumer newConsumer(String topic) {
            BlockingQueue<MessageQueuePulsar.ReceivedMessage> queue =
                topics.computeIfAbsent(topic, ignored -> new LinkedBlockingQueue<>());
            return new MessageQueuePulsar.PulsarConsumer() {
                @Override
                public MessageQueuePulsar.ReceivedMessage receive(long timeoutMillis) throws Exception {
                    return queue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
                }

                @Override
                public void acknowledge(MessageQueuePulsar.ReceivedMessage message) {
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void close() {
            topics.clear();
        }
    }
}
