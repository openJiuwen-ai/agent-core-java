/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.checkpointer;

import java.util.Map;

/**
 * Built-in checkpointer provider for in-memory storage.
 * <p>
 * Creates an in-memory checkpointer instance from the optional {@code max-sessions}
 * and {@code ttl-seconds} configuration entries; when both are absent the shared
 * default instance is returned. Suitable for testing and single-process scenarios
 * where persistence is not required.
 *
 * @see CheckpointerProvider
 * @see com.openjiuwen.core.session.checkpointer.InMemoryCheckpointer
 * @since 0.1.7
 */
public final class InMemoryCheckpointerProvider implements CheckpointerProvider {

    /** Configuration key for the maximum number of tracked sessions. */
    public static final String CONF_MAX_SESSIONS = "max-sessions";

    /** Configuration key for the per-session TTL in seconds. */
    public static final String CONF_TTL_SECONDS = "ttl-seconds";

    /**
     * Returns the in-memory checkpointer type name.
     *
     * @return the type name "in_memory"
     * @since 0.1.7
     */
    @Override
    public String typeName() {
        return "in_memory";
    }

    /**
     * Creates an in-memory checkpointer from the configuration map.
     *
     * <p>Supported entries: {@code max-sessions} (number, non-positive disables capacity
     * eviction) and {@code ttl-seconds} (number, non-positive disables TTL eviction).
     * When neither entry is present, the shared default instance from
     * {@link CheckpointerFactory} is returned.</p>
     *
     * @param conf the configuration map, may be null or empty
     * @return a configured InMemoryCheckpointer, or the shared default instance
     * @since 0.1.7
     */
    @Override
    public Checkpointer create(Map<String, Object> conf) {
        Long ttlMillis = readPositiveLong(conf, CONF_TTL_SECONDS) == null
                ? null : readPositiveLong(conf, CONF_TTL_SECONDS) * 1000L;
        Integer maxSessions = readPositiveInt(conf, CONF_MAX_SESSIONS);
        if (ttlMillis == null && maxSessions == null) {
            return CheckpointerFactory.defaultInMemoryCheckpointer();
        }
        return new InMemoryCheckpointer(
                ttlMillis == null ? InMemoryCheckpointer.DEFAULT_TTL_MILLIS : ttlMillis,
                maxSessions == null ? InMemoryCheckpointer.DEFAULT_MAX_SESSIONS : maxSessions,
                System::currentTimeMillis);
    }

    private static Long readPositiveLong(Map<String, Object> conf, String key) {
        Object value = conf == null ? null : conf.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Long.parseLong(text.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer readPositiveInt(Map<String, Object> conf, String key) {
        Long value = readPositiveLong(conf, key);
        return value == null ? null : value.intValue();
    }
}
