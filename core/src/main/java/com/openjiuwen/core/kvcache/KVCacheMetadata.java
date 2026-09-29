/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.kvcache;

import java.util.Map;
import java.util.Optional;

/**
 * Provider-facing identity, lineage, and range metadata for KVC management.
 *
 * <p>Mirrors Python's {@code openjiuwen/core/kv_cache/kv_cache_metadata.py}.</p>
 *
 * @since 0.1.16
 */
public final class KVCacheMetadata {
    /** Environment key that overrides the session's affinity cache id. */
    public static final String KV_CACHE_AFFINITY_SESSION_ID_ENV = "kv_cache_affinity_session_id";

    /** Environment key that overrides the session's affinity parent cache id. */
    public static final String KV_CACHE_AFFINITY_PARENT_SESSION_ID_ENV = "kv_cache_affinity_parent_session_id";

    private KVCacheMetadata() {
    }

    /**
     * Return the stable child identity used by one context compressor.
     *
     * @param ownerCacheId owner (parent) cache id
     * @param compressorType compressor class key
     * @return child identity under the owner
     * @throws IllegalArgumentException when owner is blank or type unsupported
     */
    public static KVCacheIdentity contextCompressorCacheIdentity(String ownerCacheId, String compressorType) {
        String normalizedOwner = normalize(ownerCacheId);
        if (normalizedOwner.isEmpty()) {
            throw new IllegalArgumentException("owner_cache_id is required");
        }
        Optional<String> suffix = compressorSuffix(compressorType);
        if (suffix.isEmpty()) {
            throw new IllegalArgumentException("unsupported context compressor type: " + compressorType);
        }
        return new KVCacheIdentity(normalizedOwner + ":compressor:" + suffix.get(), normalizedOwner);
    }

    private static Optional<String> compressorSuffix(String compressorType) {
        if ("RoundLevelCompressor".equals(compressorType)) {
            return Optional.of("round-level");
        }
        if ("CurrentRoundCompressor".equals(compressorType)) {
            return Optional.of("current-round");
        }
        if ("DialogueCompressor".equals(compressorType)) {
            return Optional.of("dialogue");
        }
        return Optional.empty();
    }

    /**
     * Return identity kwargs pointing at itself as parent.
     *
     * @param cacheId session cache id
     * @return kwargs map with {@code session_id} and {@code parent_session_id}
     */
    public static Map<String, Object> selfParentKwargs(String cacheId) {
        Map<String, Object> kwargs = new java.util.LinkedHashMap<>();
        kwargs.put("session_id", cacheId);
        kwargs.put("parent_session_id", cacheId);
        return kwargs;
    }

    /**
     * Return the stable KV cache identity for one Team member.
     *
     * @param teamSessionId product team session id
     * @param teamId team id
     * @param memberId member id
     * @return stable member cache id
     * @throws IllegalArgumentException when any component is blank
     */
    public static String teamMemberCacheIdentity(String teamSessionId, String teamId, String memberId) {
        if (normalize(teamSessionId).isEmpty() || normalize(teamId).isEmpty() || normalize(memberId).isEmpty()) {
            throw new IllegalArgumentException("team_session_id, team_id and member_id are required");
        }
        return "team:" + teamSessionId + ":team:" + teamId + ":member:" + memberId;
    }

    /**
     * Resolve current and parent session ids for AscendAffinity hints.
     *
     * <p>Resolution order mirrors Python {@code resolve_session_lineage}:
     * {@code getCacheIdentity()} first, then the affinity env overrides,
     * then the runtime session id self-pointing.</p>
     *
     * @param session current session, may be {@code null}
     * @return lineage with both ids filled; empty when session unusable
     */
    public static Lineage resolveSessionLineage(Object session) {
        if (session == null) {
            return Lineage.EMPTY;
        }
        String sessionId = normalize(sessionIdOf(session));
        if (sessionId.isEmpty() && !supportsEnvLookup(session)) {
            return Lineage.EMPTY;
        }
        Optional<KVCacheIdentity> identity = cacheIdentityOf(session);
        if (identity.isPresent() && !normalize(identity.get().cacheId()).isEmpty()) {
            String cacheId = normalize(identity.get().cacheId());
            String parentCacheId = normalize(identity.get().parentCacheId());
            return new Lineage(cacheId, parentCacheId.isEmpty() ? cacheId : parentCacheId);
        }
        String cacheSessionId = envValue(session, KV_CACHE_AFFINITY_SESSION_ID_ENV);
        String parentSessionId = parentSessionIdOf(session);
        if (parentSessionId.isEmpty()) {
            parentSessionId = envValue(session, KV_CACHE_AFFINITY_PARENT_SESSION_ID_ENV);
        }
        String resolvedSessionId = cacheSessionId.isEmpty() ? sessionId : cacheSessionId;
        if (resolvedSessionId.isEmpty()) {
            return Lineage.EMPTY;
        }
        return new Lineage(resolvedSessionId, parentSessionId.isEmpty() ? resolvedSessionId : parentSessionId);
    }

    private static boolean supportsEnvLookup(Object session) {
        return envReader(session).isPresent();
    }

    private static String sessionIdOf(Object session) {
        if (session instanceof com.openjiuwen.core.session.AgentSessionApi api) {
            return api.getSessionId();
        }
        return invokeStringGetter(session, "getSessionId");
    }

    private static Optional<KVCacheIdentity> cacheIdentityOf(Object session) {
        return invokeGetter(session, "getCacheIdentity")
                .filter(KVCacheIdentity.class::isInstance)
                .map(KVCacheIdentity.class::cast);
    }

    private static String parentSessionIdOf(Object session) {
        return invokeStringGetter(session, "getParentSessionId");
    }

    private static Optional<java.lang.reflect.Method> envReader(Object session) {
        try {
            return Optional.of(session.getClass().getMethod("getEnv", String.class, Object.class));
        } catch (NoSuchMethodException ignored) {
            return Optional.empty();
        }
    }

    private static String envValue(Object session, String key) {
        Optional<java.lang.reflect.Method> reader = envReader(session);
        if (reader.isEmpty()) {
            return "";
        }
        try {
            Object value = reader.get().invoke(session, key, null);
            return normalize(value == null ? "" : String.valueOf(value));
        } catch (java.lang.reflect.InvocationTargetException | IllegalAccessException exception) {
            return "";
        }
    }

    private static Optional<Object> invokeGetter(Object target, String methodName) {
        try {
            return Optional.ofNullable(target.getClass().getMethod(methodName).invoke(target));
        } catch (NoSuchMethodException | IllegalAccessException
                | java.lang.reflect.InvocationTargetException ignored) {
            return Optional.empty();
        }
    }

    private static String invokeStringGetter(Object target, String methodName) {
        return invokeGetter(target, methodName)
                .map(value -> normalize(String.valueOf(value)))
                .orElse("");
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip();
    }

    /**
     * First changed index between the previous and next window sequence.
     *
     * @param oldSequence previous sequence
     * @param newSequence next sequence
     * @return empty when {@code newSequence} only appends to {@code oldSequence}
     */
    public static java.util.OptionalInt firstChangedIndex(java.util.List<?> oldSequence,
                                                          java.util.List<?> newSequence) {
        int shared = Math.min(oldSequence.size(), newSequence.size());
        for (int index = 0; index < shared; index++) {
            if (!objectsEqual(oldSequence.get(index), newSequence.get(index))) {
                return java.util.OptionalInt.of(index);
            }
        }
        if (newSequence.size() < oldSequence.size()) {
            return java.util.OptionalInt.of(newSequence.size());
        }
        return java.util.OptionalInt.empty();
    }

    private static boolean objectsEqual(Object left, Object right) {
        return java.util.Objects.equals(left, right);
    }

    /**
     * Resolved (session id, parent session id) pair for affinity hints.
     *
     * @since 0.1.16
     */
    public record Lineage(String sessionId, String parentSessionId) {
        /** Empty lineage returned when resolution fails. */
        public static final Lineage EMPTY = new Lineage(null, null);

        /**
         * Whether a usable session id was resolved.
         *
         * @return true when session id present
         */
        public boolean isPresent() {
            return sessionId != null && !sessionId.isEmpty();
        }
    }
}
