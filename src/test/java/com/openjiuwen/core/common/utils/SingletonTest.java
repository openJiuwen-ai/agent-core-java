package com.openjiuwen.core.common.utils;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mirrors Python's {@code Singleton} in
 * {@code openjiuwen/core/common/utils/singleton.py}.
 */
class SingletonTest {

    @AfterEach
    void tearDown() {
        Singleton.clearAll();
    }

    @Test
    void returnsSameInstanceForSameClassAndCreatesOnlyOnce() throws Exception {
        AtomicInteger created = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Marker>> tasks = new ArrayList<>();
            for (int index = 0; index < 16; index++) {
                tasks.add(() -> Singleton.getInstance(Marker.class, () -> {
                    created.incrementAndGet();
                    return new Marker();
                }));
            }

            List<Future<Marker>> futures = executor.invokeAll(tasks);
            Marker first = futures.get(0).get();
            for (Future<Marker> future : futures) {
                assertThat(future.get()).isSameAs(first);
            }
            assertThat(created.get()).isEqualTo(1);
            assertThat(Singleton.hasInstance(Marker.class)).isTrue();
            assertThat(Singleton.getExistingInstance(Marker.class)).isSameAs(first);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    void keepsSeparateInstancesPerClassAndCanBeCleared() throws ExecutionException, InterruptedException {
        Marker marker = Singleton.getInstance(Marker.class, Marker::new);
        OtherMarker other = Singleton.getInstance(OtherMarker.class, OtherMarker::new);

        assertThat(marker).isNotSameAs(other);
        assertThat(Singleton.getExistingInstance(Marker.class)).isSameAs(marker);
        assertThat(Singleton.getExistingInstance(OtherMarker.class)).isSameAs(other);

        Singleton.clearAll();

        assertThat(Singleton.hasInstance(Marker.class)).isFalse();
        assertThat(Singleton.getExistingInstance(Marker.class)).isNull();
    }

    private static final class Marker {
    }

    private static final class OtherMarker {
    }
}
