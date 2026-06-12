/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.spawn;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.agent_teams.messager.InProcessMessager;
import com.openjiuwen.agent_teams.messager.MessagerTransportConfig;
import com.openjiuwen.agent_teams.schema.events.EventMessage;
import java.util.LinkedHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Focused parity tests for {@link SharedResources}.
 *
 * <p>Mirrors Python's {@code shared_resources} module in
 * {@code openjiuwen/agent_teams/spawn/shared_resources.py}.</p>
 */
class SharedResourcesTest {

    @BeforeEach
    void setUp() {
        SharedResources.cleanupSharedResources();
        SharedResources.setDatabaseFactoryForTests(FakeSharedDatabase::new);
    }

    @AfterEach
    void tearDown() {
        SharedResources.cleanupSharedResources();
        SharedResources.resetDatabaseFactoryForTests();
    }

    @Test
    void sharedRuntimeIsProcessGlobalUntilCleanup() {
        Object first = SharedResources.getSharedRuntime();
        Object second = SharedResources.getSharedRuntime();

        assertThat(second).isSameAs(first);

        SharedResources.cleanupSharedResources();

        assertThat(SharedResources.getSharedRuntime()).isNotSameAs(first);
    }

    @Test
    void memoryConfigUsesSingleMemorySingletonOutsideDbInstanceMap() {
        SharedResources.SharedDatabase first = SharedResources.getSharedDb(SharedResources.SharedDbConfig.memory());
        SharedResources.SharedDatabase second = SharedResources.getSharedDb(
                new SharedResources.SharedDbConfig("memory", "ignored-by-python")
        );

        assertThat(second).isSameAs(first);
        assertThat(first.config().dbType()).isEqualTo("memory");
        assertThat(SharedResources.databaseInstanceCountForTests()).isZero();
    }

    @Test
    void nonMemoryConfigIsCachedByDbTypeAndConnectionString() {
        SharedResources.SharedDatabase first = SharedResources.getSharedDb(
                new SharedResources.SharedDbConfig("sqlite", "team.db")
        );
        SharedResources.SharedDatabase sameKey = SharedResources.getSharedDb(
                new SharedResources.SharedDbConfig("sqlite", "team.db")
        );
        SharedResources.SharedDatabase differentConnection = SharedResources.getSharedDb(
                new SharedResources.SharedDbConfig("sqlite", "other.db")
        );
        SharedResources.SharedDatabase differentType = SharedResources.getSharedDb(
                new SharedResources.SharedDbConfig("postgresql", "team.db")
        );

        assertThat(sameKey).isSameAs(first);
        assertThat(differentConnection).isNotSameAs(first);
        assertThat(differentType).isNotSameAs(first);
        assertThat(SharedResources.databaseInstanceCountForTests()).isEqualTo(3);
    }

    @Test
    void cleanupClearsRuntimeDatabasesAndInProcessBus() {
        Object runtime = SharedResources.getSharedRuntime();
        SharedResources.SharedDatabase memory = SharedResources.getSharedDb(SharedResources.SharedDbConfig.memory());
        SharedResources.getSharedDb(new SharedResources.SharedDbConfig("sqlite", "team.db"));

        AtomicInteger deliveries = new AtomicInteger();
        InProcessMessager subscriber = new InProcessMessager(transport("subscriber"));
        InProcessMessager publisher = new InProcessMessager(transport("publisher"));
        subscriber.subscribe(
                "topic",
                message -> {
                    deliveries.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                }
        ).toCompletableFuture().join();
        publisher.publish(new String("topic"), event()).toCompletableFuture().join();

        SharedResources.cleanupSharedResources();
        publisher.publish(new String("topic"), event()).toCompletableFuture().join();

        assertThat(deliveries).hasValue(1);
        assertThat(SharedResources.databaseInstanceCountForTests()).isZero();
        assertThat(SharedResources.getSharedRuntime()).isNotSameAs(runtime);
        assertThat(SharedResources.getSharedDb(SharedResources.SharedDbConfig.memory())).isNotSameAs(memory);
    }

    private static MessagerTransportConfig transport(String nodeId) {
        MessagerTransportConfig config = new MessagerTransportConfig();
        config.setNodeId(nodeId);
        return config;
    }

    private static EventMessage event() {
        return new EventMessage("team_created", new LinkedHashMap<>(), "");
    }

    /**
     * Test double for the database constructors lazily called by the Python module.
     *
     * <p>Mirrors Python's lazy {@code TeamDatabase} and {@code InMemoryTeamDatabase}
     * creation sites in {@code openjiuwen/agent_teams/spawn/shared_resources.py}.</p>
     */
    private record FakeSharedDatabase(SharedResources.SharedDbConfig config)
            implements SharedResources.SharedDatabase {
    }
}
