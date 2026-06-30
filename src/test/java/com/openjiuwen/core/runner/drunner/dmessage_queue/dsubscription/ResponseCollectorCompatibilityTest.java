package com.openjiuwen.core.runner.drunner.dmessage_queue.dsubscription;

import com.openjiuwen.core.runner.drunner.dmessage_queue.message.DmqResponseMessage;
import com.openjiuwen.core.runner.drunner.dmessage_queue.message.ResultType;
import org.junit.jupiter.api.Test;

import java.util.Iterator;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResponseCollectorCompatibilityTest {

    @Test
    void collectorShouldReturnSingleResultAndClose() throws Exception {
        ResponseCollector collector = new ResponseCollector("m-1", "remote-1", null, 5.0);
        DmqResponseMessage response = new DmqResponseMessage();
        response.setBody("ok");
        response.setLastChunk(true);

        collector.putMessage(response);

        assertThat(collector.result(1.0)).isEqualTo("ok");
        assertThat(collector.isCancelled()).isTrue();
    }

    @Test
    void collectorStreamShouldYieldChunksUntilLastChunk() {
        ResponseCollector collector = new ResponseCollector("m-2", "remote-1", null, 5.0);
        DmqResponseMessage first = new DmqResponseMessage();
        first.setBody("chunk-1");
        first.setLastChunk(false);
        DmqResponseMessage last = new DmqResponseMessage();
        last.setBody("ignored");
        last.setLastChunk(true);
        collector.putMessage(first);
        collector.putMessage(last);

        Iterator<Object> stream = collector.stream(1.0);

        assertThat(stream.hasNext()).isTrue();
        assertThat(stream.next()).isEqualTo("chunk-1");
        assertThat(stream.hasNext()).isFalse();
    }

    @Test
    void collectorShouldTranslateQueueFullAndTtlCancellation() {
        ResponseCollector collector = new ResponseCollector("m-3", "remote-1", null, 5.0);
        collector.close(CancelReason.QUEUE_FULL);

        assertThatThrownBy(() -> collector.result(0.1))
                .isInstanceOf(CancellationException.class);

        ResponseCollector ttlCollector = new ResponseCollector("m-4", "remote-1", null, 0.01);
        assertThatThrownBy(() -> ttlCollector.result(0.1))
                .isInstanceOf(TimeoutException.class);
    }

    @Test
    void collectorShouldSurfaceRemoteErrorResponses() {
        ResponseCollector collector = new ResponseCollector("m-5", "remote-1", null, 5.0);
        DmqResponseMessage response = new DmqResponseMessage();
        response.setResultType(ResultType.ERROR);
        response.setErrorCode(123);
        response.setErrorMsg("boom");
        response.setLastChunk(true);
        collector.putMessage(response);

        assertThatThrownBy(() -> collector.result(1.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("boom");
    }
}
