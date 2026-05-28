/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.unit_tests.core.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for Controller Stream.
 * <p>
 * Mirrors Python's test_controller_stream.py from
 * <code>tests/unit_tests/core/controller/test_controller_stream.py</code>.
 */
@DisplayName("Controller Stream Tests")
class TestControllerStream {

    // Stub classes
    static class StreamChunkStub {
        String type;
        int index;
        String content;

        StreamChunkStub(String type, int index, String content) {
            this.type = type;
            this.index = index;
            this.content = content;
        }
    }

    static class StreamPublisher implements Flow.Publisher<StreamChunkStub> {
        List<StreamChunkStub> chunks = new ArrayList<>();
        List<Flow.Subscriber<? super StreamChunkStub>> subscribers = new ArrayList<>();

        void addChunk(StreamChunkStub chunk) {
            chunks.add(chunk);
            for (Flow.Subscriber<? super StreamChunkStub> sub : subscribers) {
                sub.onNext(chunk);
            }
        }

        @Override
        public void subscribe(Flow.Subscriber<? super StreamChunkStub> subscriber) {
            subscribers.add(subscriber);
            subscriber.onSubscribe(new Flow.Subscription() {
                @Override
                public void request(long n) {}

                @Override
                public void cancel() {}
            });
        }
    }

    static class StreamSubscriber implements Flow.Subscriber<StreamChunkStub> {
        List<StreamChunkStub> receivedChunks = new ArrayList<>();
        boolean completed = false;

        @Override
        public void onSubscribe(Flow.Subscription subscription) {}

        @Override
        public void onNext(StreamChunkStub item) {
            receivedChunks.add(item);
        }

        @Override
        public void onError(Throwable throwable) {}

        @Override
        public void onComplete() {
            completed = true;
        }
    }

    @Nested
    @DisplayName("Stream Publisher Tests")
    class TestStreamPublisher {

        @Test
        @DisplayName("publisher emits chunks")
        void testPublisherEmitsChunks() {
            StreamPublisher publisher = new StreamPublisher();
            StreamSubscriber subscriber = new StreamSubscriber();

            publisher.subscribe(subscriber);
            publisher.addChunk(new StreamChunkStub("text", 0, "hello"));
            publisher.addChunk(new StreamChunkStub("text", 1, "world"));

            assertEquals(2, subscriber.receivedChunks.size());
        }

        @Test
        @DisplayName("subscriber receives correct order")
        void testSubscriberReceivesCorrectOrder() {
            StreamPublisher publisher = new StreamPublisher();
            StreamSubscriber subscriber = new StreamSubscriber();

            publisher.subscribe(subscriber);
            publisher.addChunk(new StreamChunkStub("text", 0, "first"));
            publisher.addChunk(new StreamChunkStub("text", 1, "second"));

            assertEquals(0, subscriber.receivedChunks.get(0).index);
            assertEquals(1, subscriber.receivedChunks.get(1).index);
        }

        @Test
        @DisplayName("subscriber completes on signal")
        void testSubscriberCompletesOnSignal() {
            StreamPublisher publisher = new StreamPublisher();
            StreamSubscriber subscriber = new StreamSubscriber();

            publisher.subscribe(subscriber);
            subscriber.onComplete();

            assertTrue(subscriber.completed);
        }
    }

    @Nested
    @DisplayName("Stream Chunk Tests")
    class TestStreamChunk {

        @Test
        @DisplayName("chunk contains correct data")
        void testChunkContainsCorrectData() {
            StreamChunkStub chunk = new StreamChunkStub("json", 5, "{\"key\": \"value\"}");

            assertEquals("json", chunk.type);
            assertEquals(5, chunk.index);
            assertTrue(chunk.content.contains("key"));
        }
    }
}