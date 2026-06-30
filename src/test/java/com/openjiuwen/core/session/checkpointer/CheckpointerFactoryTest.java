/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import com.openjiuwen.extensions.checkpointer.redis.RedisCheckpointer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests checkpointer config, providers, and factory registry.
 *
 * <p>Mirrors Python's {@code CheckpointerConfig}, {@code CheckpointerProvider},
 * and {@code CheckpointerFactory} in
 * {@code openjiuwen/core/session/checkpointer/checkpointer.py}.</p>
 */
class CheckpointerFactoryTest {

    @AfterEach
    void resetDefault() {
        CheckpointerFactory.releaseDefaultCheckpointer();
    }

    @Test
    void returnsDefaultInMemoryCheckpointer() {
        Checkpointer checkpointer = CheckpointerFactory.getCheckpointer();

        assertInstanceOf(InMemoryCheckpointer.class, checkpointer);
        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), CheckpointerFactory.getCheckpointer("in_memory"));
    }

    @Test
    void doesNotExposeRawDefaultCheckpointerSetter() {
        boolean exposesSetter = Arrays.stream(CheckpointerFactory.class.getDeclaredMethods())
                .anyMatch(method -> method.getName().equals("setDefaultCheckpointer"));

        assertFalse(exposesSetter);
    }

    @Test
    void typeSpecificCheckpointerOverridesDefault() {
        Checkpointer typed = new InMemoryCheckpointer();
        CheckpointerFactory.setCheckpointer("custom-store", typed);

        assertSame(typed, CheckpointerFactory.getCheckpointer("custom-store"));
        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), CheckpointerFactory.getCheckpointer("missing"));
    }

    @Test
    void registerAndCreateUseProvider() {
        Checkpointer expected = new InMemoryCheckpointer();
        CheckpointerFactory.register("unit-custom", conf -> expected);

        Checkpointer created = CheckpointerFactory.create(new CheckpointerConfig("unit-custom", Map.of("x", 1)));

        assertSame(expected, created);
    }

    @Test
    void installDefaultCheckpointerSetsDefaultUntilReleased() {
        CloseTrackingCheckpointer created = new CloseTrackingCheckpointer();
        CheckpointerFactory.register("unit-configured-closeable", conf -> created);

        CheckpointerFactory.installDefaultCheckpointer(new CheckpointerConfig("unit-configured-closeable", Map.of()));

        assertSame(created, CheckpointerFactory.getCheckpointer());
        assertFalse(created.closed);

        CheckpointerFactory.releaseDefaultCheckpointer();

        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), CheckpointerFactory.getCheckpointer());
        assertTrue(created.closed);
    }

    @Test
    void installDefaultCheckpointerWithNullConfigLeavesDefaultUnchanged() {
        Checkpointer existing = new InMemoryCheckpointer();
        CheckpointerFactory.register("unit-existing-default", conf -> existing);
        CheckpointerFactory.installDefaultCheckpointer(new CheckpointerConfig("unit-existing-default", Map.of()));

        CheckpointerFactory.installDefaultCheckpointer(null);

        assertSame(existing, CheckpointerFactory.getCheckpointer());
    }

    @Test
    void installDefaultCheckpointerReplacesCurrentDefaultAndClosesPreviousDefault() {
        CloseTrackingCheckpointer first = new CloseTrackingCheckpointer();
        CloseTrackingCheckpointer second = new CloseTrackingCheckpointer();
        CheckpointerFactory.register("unit-configured-replace-first", conf -> first);
        CheckpointerFactory.register("unit-configured-replace-second", conf -> second);

        CheckpointerFactory.installDefaultCheckpointer(
                new CheckpointerConfig("unit-configured-replace-first", Map.of()));
        CheckpointerFactory.installDefaultCheckpointer(
                new CheckpointerConfig("unit-configured-replace-second", Map.of()));

        assertSame(second, CheckpointerFactory.getCheckpointer());
        assertTrue(first.closed);
        assertFalse(second.closed);

        CheckpointerFactory.releaseDefaultCheckpointer();

        assertSame(CheckpointerFactory.defaultInMemoryCheckpointer(), CheckpointerFactory.getCheckpointer());
        assertTrue(second.closed);
    }

    @Test
    void createSupportsRedisProviderWithoutManualRegistration() {
        Checkpointer created = CheckpointerFactory.create(new CheckpointerConfig("redis", Map.of(
                "connection", Map.of("url", "redis://127.0.0.1:6379"),
                "dump_type", "json"
        )));

        assertInstanceOf(RedisCheckpointer.class, created);
    }

    @Test
    void createRejectsUnknownProvider() {
        assertThrows(
                IllegalArgumentException.class,
                () -> CheckpointerFactory.create(new CheckpointerConfig("missing-provider", Map.of()))
        );
    }

    @Test
    void configStringRedactsNestedUrlPasswords() {
        CheckpointerConfig config = new CheckpointerConfig("redis", Map.of(
                "url", "redis://user:secret@example.com:6379/0",
                "nested", Map.of("endpoint", "postgres://name:password@example.com/db"),
                "connection", Map.of(
                        "password", "plain-redis-password",
                        "api_key", "plain-api-key"),
                "list", List.of("http://public.example.com", "mysql://root:pw@example.com/db")
        ));

        String repr = config.repr();
        String text = config.toString();

        assertFalse(repr.contains("secret"));
        assertFalse(repr.contains("name:password"));
        assertFalse(repr.contains("plain-redis-password"));
        assertFalse(repr.contains("plain-api-key"));
        assertFalse(repr.contains("root:pw"));
        assertTrue(repr.contains(":***@example.com"));
        assertTrue(text.contains(":***@example.com"));
        assertFalse(text.contains("plain-redis-password"));
        assertFalse(text.contains("plain-api-key"));
    }

    private static final class CloseTrackingCheckpointer extends Checkpointer implements AutoCloseable {
        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }
    }
}
