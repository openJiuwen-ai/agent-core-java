/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.RunnerTermination;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseCollectorTest {

    @Test
    void resultReturnsMessageBodyAndClosesCollector() throws Exception {
        ResponseCollector collector = new ResponseCollector("msg-1", "receiver-1", "req-1", 5.0d);
        DmqResponseMessage response = response("payload-1", false);

        collector.putMessage(response).get(1, TimeUnit.SECONDS);

        assertThat(collector.result().get(1, TimeUnit.SECONDS)).isEqualTo("payload-1");
        assertThat(collector.isCancelled()).isTrue();
        assertThat(collector.isExpired()).isFalse();
    }

    @Test
    void streamReturnsChunksAndSkipsLastChunkMarker() throws Exception {
        ResponseCollector collector = new ResponseCollector("msg-2", "receiver-1", "req-1", 5.0d);

        collector.putMessage(response("chunk-1", false)).get(1, TimeUnit.SECONDS);
        collector.putMessage(response("ignored-final", true)).get(1, TimeUnit.SECONDS);

        assertThat(collector.stream().get(1, TimeUnit.SECONDS)).isEqualTo(List.of("chunk-1"));
        assertThat(collector.isCancelled()).isTrue();
    }

    @Test
    void resultTimeoutMarksCollectorExpired() {
        ResponseCollector collector = new ResponseCollector("msg-3", "receiver-1", "req-1", 5.0d);

        assertThatThrownBy(() -> collector.result(0.01d).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(TimeoutException.class)
                .hasMessageContaining("timeout waiting for result");
        assertThat(collector.isExpired()).isTrue();
        assertThat(collector.isCancelled()).isTrue();
    }

    @Test
    void startExpiresCollectorAndWakesWaitingResult() throws Exception {
        ResponseCollector collector = new ResponseCollector("msg-ttl", "receiver-1", "req-1", 0.02d);
        collector.start().get(1, TimeUnit.SECONDS);

        assertThatThrownBy(() -> collector.result(1.0d).join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(TimeoutException.class);
        assertThat(collector.isExpired()).isTrue();
        assertThat(collector.isCancelled()).isTrue();
    }

    @Test
    void closeWakesWaitingResultWithRunnerTermination() throws Exception {
        ResponseCollector collector = new ResponseCollector("msg-4", "receiver-1", "req-1", 5.0d);

        var future = collector.result(5.0d);
        awaitAsyncWaiter();
        collector.close(CancelReason.RUNNER_STOPPED).get(1, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(RunnerTermination.class);
    }

    @Test
    void queueFullCancelEventMapsToCancellation() throws Exception {
        ResponseCollector collector = new ResponseCollector("msg-5", "receiver-1", "req-1", 5.0d);
        var future = collector.result(5.0d);

        awaitAsyncWaiter();
        collector.close(CancelReason.QUEUE_FULL).get(1, TimeUnit.SECONDS);

        assertThatThrownBy(() -> future.join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(CancellationException.class)
                .hasMessageContaining("queue full");
    }

    @Test
    void putMessageAutoCancelsWhenQueueIsFull() throws Exception {
        ResponseCollector collector = new ResponseCollector("msg-full", "receiver-1", "req-1", 5.0d);
        for (int index = 0; index < ResponseCollector.MAX_QUEUE_SIZE; index++) {
            collector.putMessage(response("payload-" + index, false)).get(1, TimeUnit.SECONDS);
        }

        collector.putMessage(response("overflow", false)).get(1, TimeUnit.SECONDS);

        assertThat(collector.isCancelled()).isTrue();
        assertThat(collector.getQueueSize()).isEqualTo(1);
    }

    @Test
    void remoteErrorResponseBuildsFrameworkError() throws Exception {
        ResponseCollector collector = new ResponseCollector("msg-6", "receiver-1", "req-1", 5.0d);
        DmqResponseMessage response = response("", true);
        response.setResultType(ResultType.ERROR);
        response.setErrorCode(123);
        response.setErrorMsg("boom");

        collector.putMessage(response).get(1, TimeUnit.SECONDS);

        assertThatThrownBy(() -> collector.result().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(BaseError.class);
        assertThatThrownBy(() -> collector.result().join())
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(CancellationException.class)
                .hasMessageContaining("cancelled before request send");
    }

    @Test
    void cancelReasonPreservesPythonStringValues() {
        assertThat(CancelReason.RUNNER_STOPPED.getValue()).isEqualTo("runner_stopped");
        assertThat(CancelReason.TTL_EXPIRE.getValue()).isEqualTo("ttl_expire");
        assertThat(CancelReason.QUEUE_FULL.getValue()).isEqualTo("queue_full");
        assertThat(CancelReason.FINISH.getValue()).isEqualTo("finish");
        assertThat(CancelReason.fromValue("ttl_expire")).isEqualTo(CancelReason.TTL_EXPIRE);
    }

    private static DmqResponseMessage response(Object body, boolean lastChunk) {
        DmqResponseMessage response = new DmqResponseMessage();
        response.setMessageId("message-id");
        response.setBody(body);
        response.setReceiverId("receiver-1");
        response.setLastChunk(lastChunk);
        response.setResultType(ResultType.MESSAGE);
        response.setErrorCode(StatusCode.SUCCESS.getCode());
        return response;
    }

    private static void awaitAsyncWaiter() throws InterruptedException {
        Thread.sleep(50L);
    }
}
