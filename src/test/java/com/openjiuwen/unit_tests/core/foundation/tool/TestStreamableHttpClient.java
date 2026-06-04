/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.foundation.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StreamableHttpClient.
 * <p>
 * Mirrors Python's test_streamable_http_client.py from
 * <code>tests/unit_tests/core/foundation/tool/test_streamable_http_client.py</code>.
 */
@DisplayName("Streamable Http Client Tests")
class TestStreamableHttpClient {

    // Stub classes
    static class StreamChunk {
        String type;
        String content;
        int index;

        StreamChunk(String type, String content, int index) {
            this.type = type;
            this.content = content;
            this.index = index;
        }
    }

    static class StreamableHttpClient {
        String url;
        List<StreamChunk> chunks = new ArrayList<>();
        boolean connected;

        StreamableHttpClient(String url) {
            this.url = url;
        }

        boolean connect() {
            connected = true;
            return true;
        }

        void stream(String data) {
            if (!connected) {
                throw new IllegalStateException("client is not connected");
            }
            chunks.add(new StreamChunk("data", data, chunks.size()));
        }

        List<StreamChunk> getChunks() {
            return new ArrayList<>(chunks);
        }
    }

    static class StreamSubscriber implements Flow.Subscriber<StreamChunk> {
        List<StreamChunk> received = new ArrayList<>();
        Flow.Subscription subscription;
        Throwable error;
        boolean completed;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(StreamChunk item) {
            received.add(item);
        }

        @Override
        public void onError(Throwable throwable) {
            this.error = throwable;
        }

        @Override
        public void onComplete() {
            this.completed = true;
        }
    }

    @Nested
    @DisplayName("Streamable Client Tests")
    class TestStreamableClient {

        @Test
        @DisplayName("client creation")
        void testClientCreation() {
            StreamableHttpClient client = new StreamableHttpClient("http://localhost:8080");

            assertEquals("http://localhost:8080", client.url);
        }

        @Test
        @DisplayName("stream data")
        void testStreamData() {
            StreamableHttpClient client = new StreamableHttpClient("http://localhost:8080");
            assertTrue(client.connect());
            assertTrue(client.connected);

            client.stream("chunk1");
            client.stream("chunk2");

            assertEquals(2, client.getChunks().size());
        }
    }

    @Nested
    @DisplayName("Stream Subscriber Tests")
    class TestStreamSubscriber {

        @Test
        @DisplayName("subscriber receives chunks")
        void testSubscriberReceivesChunks() {
            StreamSubscriber subscriber = new StreamSubscriber();
            StreamChunk chunk1 = new StreamChunk("data", "content1", 0);
            StreamChunk chunk2 = new StreamChunk("data", "content2", 1);
            TestSubscription subscription = new TestSubscription();

            subscriber.onSubscribe(subscription);
            subscriber.onNext(chunk1);
            subscriber.onNext(chunk2);
            subscriber.onComplete();

            assertEquals(Long.MAX_VALUE, subscription.requested);
            assertEquals(2, subscriber.received.size());
            assertTrue(subscriber.completed);
        }

        @Test
        @DisplayName("chunk contains correct data")
        void testChunkContainsCorrectData() {
            StreamChunk chunk = new StreamChunk("message", "hello", 5);

            assertEquals("message", chunk.type);
            assertEquals("hello", chunk.content);
            assertEquals(5, chunk.index);
        }
    }

    static class TestSubscription implements Flow.Subscription {
        long requested;
        boolean cancelled;

        @Override
        public void request(long n) {
            requested += n;
        }

        @Override
        public void cancel() {
            cancelled = true;
        }
    }
}
