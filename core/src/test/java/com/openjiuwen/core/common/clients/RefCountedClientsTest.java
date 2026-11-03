package com.openjiuwen.core.common.clients;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Mirrors Python's {@code openjiuwen.core.common.clients.ref_counted} in
 * {@code openjiuwen/core/common/clients/ref_counted.py}.
 */
class RefCountedClientsTest {

    @Test
    void resourceTracksStatsAndClosesWithKwargs() {
        TestResource resource = new TestResource();

        assertThat(resource.getRefCount()).isEqualTo(1);
        assertThat(resource.getCreatedAt()).isPositive();
        assertThat(resource.getLastUsed()).isEqualTo(resource.getCreatedAt());

        int incremented = resource.incrementRef();

        assertThat(incremented).isEqualTo(2);
        assertThat(resource.getLastUsed()).isNotEqualTo(resource.getCreatedAt());
        assertThat(resource.getAge()).isGreaterThanOrEqualTo(0.0d);

        resource.close(Map.of("reason", "manual")).join();

        assertThat(resource.isClosed()).isTrue();
        assertThat(resource.getRefCount()).isEqualTo(1);
        assertThat(resource.closedCount.get()).isEqualTo(1);
        assertThat(resource.lastKwargs).containsEntry("reason", "manual");
        assertThat(resource.getStats())
                .containsEntry("ref_count", 1)
                .containsEntry("closed", true)
                .containsKey("created_at")
                .containsKey("last_used")
                .containsEntry("age", 0.0d);
        assertThatThrownBy(resource::incrementRef)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Cannot increment ref on closed resource");
    }

    @Test
    void managerReusesResourceAndPreservesPythonDoubleDecrementClosePath() {
        TestManager manager = new TestManager();

        BaseRefResourceMgr.ResourceLease<TestResource> first = manager.acquire("alpha").join();
        BaseRefResourceMgr.ResourceLease<TestResource> second = manager.acquire("alpha").join();

        assertThat(first.isNew()).isTrue();
        assertThat(second.isNew()).isFalse();
        assertThat(second.resource()).isSameAs(first.resource());
        assertThat(first.resource().getRefCount()).isEqualTo(2);

        manager.release("alpha").join();
        assertThat(first.resource().getRefCount()).isEqualTo(1);
        assertThat(first.resource().isClosed()).isFalse();

        manager.release("alpha").join();

        assertThat(first.resource().isClosed()).isTrue();
        assertThat(first.resource().getRefCount()).isEqualTo(-1);
        assertThat(first.resource().closedCount.get()).isEqualTo(1);
        assertThat(manager.getStats().join()).containsEntry("total_resources", 0);
    }

    @Test
    void managerDropsClosedResourceAndCreatesFreshReplacement() {
        TestManager manager = new TestManager();
        TestResource first = manager.acquire("beta").join().resource();

        manager.close("beta").join();

        TestResource second = manager.acquire("beta").join().resource();

        assertThat(second).isNotSameAs(first);
        assertThat(second.getRefCount()).isEqualTo(1);
        assertThat(manager.createdCount.get()).isEqualTo(2);
    }

    private static final class TestManager extends BaseRefResourceMgr<TestResource> {
        private final AtomicInteger createdCount = new AtomicInteger();

        @Override
        protected String getResourceKey(Object config) {
            return String.valueOf(config);
        }

        @Override
        protected CompletableFuture<TestResource> createResource(Object config) {
            createdCount.incrementAndGet();
            return CompletableFuture.completedFuture(new TestResource());
        }
    }

    private static final class TestResource extends RefCountedResource {
        private final AtomicInteger closedCount = new AtomicInteger();
        private Map<String, Object> lastKwargs = Map.of();

        @Override
        protected CompletableFuture<Void> doClose(Map<String, Object> kwargs) {
            closedCount.incrementAndGet();
            lastKwargs = kwargs;
            return CompletableFuture.completedFuture(null);
        }
    }
}
