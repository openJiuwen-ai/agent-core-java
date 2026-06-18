/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Focused tests for session controller metadata schemas.
 *
 * <p>Mirrors Python's {@code SessionMeta} and {@code ScopeSessionsMeta} in
 * {@code openjiuwen/core/session/session_controller/schema.py}.</p>
 */
class SessionSchemaTest {

    @Test
    void sessionMetaCreateNewUsesUtcSecondsDefaultsAndActiveState() {
        double before = System.currentTimeMillis() / 1000.0D;
        SessionMeta meta = SessionMeta.createNew("session-1");
        double after = System.currentTimeMillis() / 1000.0D;

        assertThat(meta.getSessionId()).isEqualTo("session-1");
        assertThat(meta.getVersion()).isEqualTo(1);
        assertThat(meta.isActive()).isTrue();
        assertThat(meta.getDataContainerType()).isEqualTo(DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
        assertThat(meta.getCreatedAt()).isBetween(before, after);
        assertThat(meta.getUpdatedAt()).isBetween(before, after);
    }

    @Test
    void sessionMetaToMapAndFromMapPreservePythonFieldNamesAndDefaultContainerType() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("session_id", "session-2");
        source.put("created_at", 1.5D);
        source.put("updated_at", 2.5D);
        source.put("version", 3);
        source.put("is_active", false);

        SessionMeta meta = SessionMeta.fromMap(source);

        assertThat(source).containsEntry("data_container_type", DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
        assertThat(meta.toMap()).containsEntry("session_id", "session-2")
                .containsEntry("created_at", 1.5D)
                .containsEntry("updated_at", 2.5D)
                .containsEntry("version", 3)
                .containsEntry("is_active", false)
                .containsEntry("data_container_type", DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
    }

    @Test
    void sessionMetaUpdatesTimestampAndVersionInPlace() {
        SessionMeta meta = new SessionMeta("session-3", 1.0D, 1.0D, 7, false, "custom");

        meta.incrementVersion();
        meta.updateTimestamp();

        assertThat(meta.getVersion()).isEqualTo(8);
        assertThat(meta.getUpdatedAt()).isGreaterThan(1.0D);
        assertThat(meta.getCreatedAt()).isEqualTo(1.0D);
    }

    @Test
    void scopeSessionsMetaManagesActiveSessionAndDescendingUpdateSort() {
        ScopeSessionsMeta scope = new ScopeSessionsMeta("scope-key");
        SessionMeta older = new SessionMeta("older", 1.0D, 1.0D, 1, true);
        SessionMeta newer = new SessionMeta("newer", 2.0D, 2.0D, 1, true);

        scope.addSession(older);
        scope.addSession(newer);

        assertThat(older.isActive()).isFalse();
        assertThat(newer.isActive()).isTrue();
        assertThat(scope.getActiveSession()).isEqualTo("newer");
        assertThat(scope.getSessions()).extracting(SessionMeta::getSessionId).containsExactly("newer", "older");
        assertThat(scope.getActiveSessionMeta()).isSameAs(newer);
    }

    @Test
    void scopeSessionsMetaRemoveActivateUpdateAndIncrementMirrorPythonReturnValues() {
        ScopeSessionsMeta scope = new ScopeSessionsMeta("scope-key");
        SessionMeta first = new SessionMeta("first", 1.0D, 1.0D, 1, false);
        SessionMeta second = new SessionMeta("second", 2.0D, 2.0D, 1, false);
        scope.setSessions(new ArrayList<>(List.of(first, second)));

        assertThat(scope.activateSession("first")).isTrue();
        assertThat(scope.activateSession("missing")).isFalse();
        assertThat(first.isActive()).isTrue();
        assertThat(second.isActive()).isFalse();
        assertThat(scope.updateSessionTimestamp("second")).isTrue();
        assertThat(scope.updateSessionTimestamp("missing")).isFalse();
        assertThat(scope.incrementSessionVersion("second")).isTrue();
        assertThat(scope.incrementSessionVersion("missing")).isFalse();
        assertThat(second.getVersion()).isEqualTo(2);
        assertThat(scope.removeSession("first")).isSameAs(first);
        assertThat(scope.getActiveSession()).isNull();
        assertThat(scope.removeSession("missing")).isNull();
    }

    @Test
    void scopeSessionsMetaToMapAndFromMapUseNestedSessionDictionaries() {
        Map<String, Object> data = Map.of(
                "session_scope_key", "scope-key",
                "active_session", "active",
                "sessions", List.of(Map.of(
                        "session_id", "active",
                        "created_at", 1.0D,
                        "updated_at", 4.0D,
                        "version", 5,
                        "is_active", true
                ))
        );

        ScopeSessionsMeta scope = ScopeSessionsMeta.fromMap(data);

        assertThat(scope.getSessionScopeKey()).isEqualTo("scope-key");
        assertThat(scope.getActiveSession()).isEqualTo("active");
        assertThat(scope.getSessions()).hasSize(1);
        assertThat(scope.getSessions().getFirst().getDataContainerType())
                .isEqualTo(DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
        assertThat(scope.toMap()).containsKeys("session_scope_key", "active_session", "sessions");
    }
}
