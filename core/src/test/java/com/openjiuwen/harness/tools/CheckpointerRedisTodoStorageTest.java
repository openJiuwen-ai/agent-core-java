/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.openjiuwen.core.foundation.store.kv.InMemoryKVStore;
import com.openjiuwen.core.session.checkpointer.Checkpointer;
import com.openjiuwen.core.session.checkpointer.CheckpointerFactory;
import com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer;
import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;
import com.openjiuwen.extensions.store.kv.RedisStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Verifies Redis checkpointer reuse, Todo expiration and legacy storage compatibility.
 *
 * @since 0.1.16
 */
class CheckpointerRedisTodoStorageTest {
    private final Checkpointer previous = CheckpointerFactory.getCheckpointer();
    private final RedisStore store = mock(RedisStore.class);

    @AfterEach
    void restoreDefault() {
        CheckpointerFactory.setDefaultCheckpointer(previous);
    }

    private TodoStorage create(Map<String, Object> ttl, Map<String, Object> todo) {
        CheckpointerFactory.setDefaultCheckpointer(new RedisCheckpointer(store, ttl));
        return TodoStorageFactory.create(CheckpointerRedisTodoStorageProvider.TYPE, todo);
    }

    @Test
    void spiProviderBorrowsStoreAndInheritsEffectiveTtl() throws Exception {
        Map<String, Object> source = new HashMap<>(Map.of("default_ttl", 1.25, "refresh_on_read", true));
        TodoStorage todo = create(source, Map.of());
        source.put("default_ttl", 100);
        todo.save("session", List.of(TodoItem.create("task")));
        verify(store).set(eq("session:todo"), any(), eq(Duration.ofSeconds(75)));
        when(store.get("session:todo")).thenReturn("[]");
        todo.load("session");
        verify(store).refreshTtl(List.of("session:todo"), Duration.ofSeconds(75));
        verify(store, never()).close();
    }

    @Test
    void explicitPolicyOverridesWithoutChangingCheckpointer() throws Exception {
        TodoStorage todo = create(Map.of("default_ttl", 60, "refresh_on_read", true),
                Map.of("ttl", Map.of("default_ttl", 0.025)));
        todo.save("session", List.of());
        verify(store).set("session:todo", "[]", Duration.ofSeconds(2));
        when(store.get("session:todo")).thenReturn("[]");
        todo.load("session");
        verify(store, never()).refreshTtl(any(), any(Duration.class));
        assertThat(CheckpointerFactory.getCheckpointer()).isInstanceOfSatisfying(RedisCheckpointer.class, cp -> {
            assertThat(cp.getEffectiveTtl()).contains(Duration.ofHours(1));
            assertThat(cp.isRefreshOnRead()).isTrue();
        });
    }

    @Test
    void inheritedTtlPreservesCheckpointerTruncation() throws Exception {
        TodoStorage todo = create(Map.of("default_ttl", 0.025), Map.of());
        todo.save("session", List.of());
        verify(store).set("session:todo", "[]", Duration.ofSeconds(1));
    }

    @Test
    void customProvidersKeepTheirOwnConfigurationContract() {
        TodoStorage custom = mock(TodoStorage.class);
        Map<String, Object> conf = Map.of("ttl", "provider-specific-policy");
        TodoStorageFactory.register("custom-expiry-test", new TodoStorageProvider() {
            @Override
            public String typeName() {
                return "custom-expiry-test";
            }

            @Override
            public TodoStorage create(Map<String, Object> actual) {
                assertThat(actual).isEqualTo(conf);
                return custom;
            }
        });
        assertThat(TodoStorageFactory.create("custom-expiry-test", conf)).isSameAs(custom);
    }

    @Test
    void legacyConstructorAndKvProviderDoNotInheritDefaultTtl() throws Exception {
        create(Map.of("default_ttl", 60), Map.of());
        new KvTodoStorage(store).save("legacy", List.of());
        TodoStorageFactory.create("kv", Map.of("sharedKvStore", store)).save("kv", List.of());
        verify(store).set("legacy:todo", "[]");
        verify(store).set("kv:todo", "[]");
        verify(store, never()).set(any(), any(), any(Duration.class));
    }

    @Test
    void absentCheckpointerTtlUsesOrdinaryWriteAndDoesNotRenewMissingKeys() throws Exception {
        TodoStorage todo = create(null, Map.of());
        assertThat(CheckpointerFactory.getCheckpointer()).isInstanceOfSatisfying(RedisCheckpointer.class,
                cp -> assertThat(cp.getEffectiveTtl()).isEmpty());
        todo.save("session", List.of());
        assertThat(todo.load("missing")).isEmpty();
        verify(store).set("session:todo", "[]");
        verify(store, never()).refreshTtl(any(), any(Duration.class));
    }

    @Test
    void missingKeyDoesNotRenewEvenWhenEnabled() throws Exception {
        TodoStorage todo = create(Map.of("default_ttl", 1, "refresh_on_read", true), Map.of("ttl", Map.of()));
        assertThat(todo.load("missing")).isEmpty();
        verify(store, never()).refreshTtl(any(), any(Duration.class));
    }

    @Test
    void rejectsNonRedisDefaultAndIndependentConnections() {
        CheckpointerFactory.setDefaultCheckpointer(new InMemoryCheckpointer());
        assertThatThrownBy(() -> TodoStorageFactory.create("checkpointer_redis", Map.of()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("RedisCheckpointer");
        for (String key : List.of("sharedKvStore", "kvStoreConf", "kvStoreType", "connection")) {
            assertThatThrownBy(() -> create(null, Map.of(key, store)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(doubles = {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.MAX_VALUE})
    void rejectsInvalidMinutes(double minutes) {
        assertThatThrownBy(() -> create(null, Map.of("ttl", Map.of("default_ttl", minutes))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(store);
    }

    @Test
    void rejectsIncompletePolicyAndUnsupportedBackend() {
        for (Object invalid : List.of("bad", Map.of("refresh_on_read", true),
                Map.of("default_ttl", "1"), Map.of("default_ttl", 1, "refresh_on_read", "true"))) {
            assertThatThrownBy(() -> create(null, Map.of("ttl", invalid)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> TodoStorageFactory.create("kv",
                Map.of("sharedKvStore", new InMemoryKVStore(), "ttl", Map.of("default_ttl", 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TodoStorageFactory.create("file", Map.of("ttl", Map.of("default_ttl", 1))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ttlCommandFailureIsNotReportedAsSuccessfulToolWrite() {
        TodoStorage todo = create(Map.of("default_ttl", 1), Map.of());
        doThrow(new IllegalStateException("write failed")).when(store).set(any(), any(), any(Duration.class));
        ToolOutput output = new TodoTool(todo).create("session", List.of("task"));
        assertThat(output.isSuccess()).isFalse();
        assertThat(output.getError()).contains("write failed");
    }
}
