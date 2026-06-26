/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.session.session_controller.test_schema} in
 * {@code tests/unit_tests/core/session/session_controller/test_schema.py}.</p>
 */
class SessionSchemaPythonParityTest {

    private static final List<String> PYTHON_TESTS = List.of(
            "TestSessionMeta::test_create_new",
            "TestSessionMeta::test_create_new_with_version",
            "TestSessionMeta::test_update_timestamp",
            "TestSessionMeta::test_increment_version",
            "TestSessionMeta::test_to_dict",
            "TestSessionMeta::test_from_dict",
            "TestSessionMeta::test_from_dict_missing_container_type",
            "TestScopeSessionsMeta::test_init",
            "TestScopeSessionsMeta::test_add_session",
            "TestScopeSessionsMeta::test_add_inactive_session",
            "TestScopeSessionsMeta::test_add_active_deactivates_others",
            "TestScopeSessionsMeta::test_get_session",
            "TestScopeSessionsMeta::test_get_session_not_found",
            "TestScopeSessionsMeta::test_remove_session",
            "TestScopeSessionsMeta::test_remove_session_not_found",
            "TestScopeSessionsMeta::test_activate_session",
            "TestScopeSessionsMeta::test_activate_nonexistent_session",
            "TestScopeSessionsMeta::test_deactivate_all_sessions",
            "TestScopeSessionsMeta::test_get_active_session",
            "TestScopeSessionsMeta::test_get_active_session_none",
            "TestScopeSessionsMeta::test_update_session_timestamp",
            "TestScopeSessionsMeta::test_update_session_timestamp_not_found",
            "TestScopeSessionsMeta::test_increment_session_version",
            "TestScopeSessionsMeta::test_increment_session_version_not_found",
            "TestScopeSessionsMeta::test_to_dict_and_from_dict",
            "TestScopeSessionsMeta::test_sort_sessions"
    );

    @TestFactory
    Collection<DynamicTest> pythonSessionSchemaCases() {
        return PYTHON_TESTS.stream()
                .map(name -> DynamicTest.dynamicTest(name, () -> runPythonCase(name)))
                .toList();
    }

    private void runPythonCase(String name) {
        switch (name) {
            case "TestSessionMeta::test_create_new" -> sessionMetaCreateNew();
            case "TestSessionMeta::test_create_new_with_version" -> sessionMetaCreateNewWithVersion();
            case "TestSessionMeta::test_update_timestamp" -> sessionMetaUpdateTimestamp();
            case "TestSessionMeta::test_increment_version" -> sessionMetaIncrementVersion();
            case "TestSessionMeta::test_to_dict" -> sessionMetaToDict();
            case "TestSessionMeta::test_from_dict" -> sessionMetaFromDict();
            case "TestSessionMeta::test_from_dict_missing_container_type" -> sessionMetaFromDictMissingContainerType();
            case "TestScopeSessionsMeta::test_init" -> scopeSessionsMetaInit();
            case "TestScopeSessionsMeta::test_add_session" -> scopeSessionsMetaAddSession();
            case "TestScopeSessionsMeta::test_add_inactive_session" -> scopeSessionsMetaAddInactiveSession();
            case "TestScopeSessionsMeta::test_add_active_deactivates_others" -> scopeSessionsMetaAddActiveDeactivatesOthers();
            case "TestScopeSessionsMeta::test_get_session" -> scopeSessionsMetaGetSession();
            case "TestScopeSessionsMeta::test_get_session_not_found" -> scopeSessionsMetaGetSessionNotFound();
            case "TestScopeSessionsMeta::test_remove_session" -> scopeSessionsMetaRemoveSession();
            case "TestScopeSessionsMeta::test_remove_session_not_found" -> scopeSessionsMetaRemoveSessionNotFound();
            case "TestScopeSessionsMeta::test_activate_session" -> scopeSessionsMetaActivateSession();
            case "TestScopeSessionsMeta::test_activate_nonexistent_session" -> scopeSessionsMetaActivateNonexistentSession();
            case "TestScopeSessionsMeta::test_deactivate_all_sessions" -> scopeSessionsMetaDeactivateAllSessions();
            case "TestScopeSessionsMeta::test_get_active_session" -> scopeSessionsMetaGetActiveSession();
            case "TestScopeSessionsMeta::test_get_active_session_none" -> scopeSessionsMetaGetActiveSessionNone();
            case "TestScopeSessionsMeta::test_update_session_timestamp" -> scopeSessionsMetaUpdateSessionTimestamp();
            case "TestScopeSessionsMeta::test_update_session_timestamp_not_found" -> scopeSessionsMetaUpdateSessionTimestampNotFound();
            case "TestScopeSessionsMeta::test_increment_session_version" -> scopeSessionsMetaIncrementSessionVersion();
            case "TestScopeSessionsMeta::test_increment_session_version_not_found" -> scopeSessionsMetaIncrementSessionVersionNotFound();
            case "TestScopeSessionsMeta::test_to_dict_and_from_dict" -> scopeSessionsMetaToDictAndFromDict();
            case "TestScopeSessionsMeta::test_sort_sessions" -> scopeSessionsMetaSortSessions();
            default -> throw new IllegalArgumentException("Unhandled Python test: " + name);
        }
    }

    private void sessionMetaCreateNew() {
        SessionMeta meta = SessionMeta.createNew("session-1");

        assertThat(meta.getSessionId()).isEqualTo("session-1");
        assertThat(meta.isActive()).isTrue();
        assertThat(meta.getVersion()).isEqualTo(1);
        assertThat(meta.getCreatedAt()).isPositive();
        assertThat(meta.getUpdatedAt()).isPositive();
        assertThat(meta.getCreatedAt()).isEqualTo(meta.getUpdatedAt());
    }

    private void sessionMetaCreateNewWithVersion() {
        SessionMeta meta = SessionMeta.createNew("session-2", 5);

        assertThat(meta.getVersion()).isEqualTo(5);
    }

    private void sessionMetaUpdateTimestamp() {
        SessionMeta meta = SessionMeta.createNew("session-3");
        double oldUpdated = meta.getUpdatedAt();

        meta.updateTimestamp();

        assertThat(meta.getUpdatedAt()).isGreaterThanOrEqualTo(oldUpdated);
    }

    private void sessionMetaIncrementVersion() {
        SessionMeta meta = SessionMeta.createNew("session-4");

        meta.incrementVersion();

        assertThat(meta.getVersion()).isEqualTo(2);
    }

    private void sessionMetaToDict() {
        SessionMeta meta = SessionMeta.createNew("session-5");
        Map<String, Object> data = meta.toMap();

        assertThat(data).containsEntry("session_id", "session-5");
        assertThat(data).containsKeys("created_at", "updated_at", "version", "is_active");
    }

    private void sessionMetaFromDict() {
        SessionMeta meta = SessionMeta.createNew("session-6");
        Map<String, Object> data = new LinkedHashMap<>(meta.toMap());

        SessionMeta restored = SessionMeta.fromMap(data);

        assertThat(restored.getSessionId()).isEqualTo(meta.getSessionId());
        assertThat(restored.getVersion()).isEqualTo(meta.getVersion());
        assertThat(restored.isActive()).isEqualTo(meta.isActive());
    }

    private void sessionMetaFromDictMissingContainerType() {
        Map<String, Object> data = mutableMap(
                "session_id", "s1",
                "created_at", 1000.0D,
                "updated_at", 1000.0D,
                "version", 1,
                "is_active", true
        );

        SessionMeta restored = SessionMeta.fromMap(data);

        assertThat(restored.getDataContainerType()).isEqualTo(DataContainerFactory.DEFAULT_DATA_CONTAINER_TYPE);
    }

    private void scopeSessionsMetaInit() {
        ScopeSessionsMeta meta = new ScopeSessionsMeta("agent:a1:main");

        assertThat(meta.getSessionScopeKey()).isEqualTo("agent:a1:main");
        assertThat(meta.getActiveSession()).isNull();
        assertThat(meta.getSessions()).isEmpty();
    }

    private void scopeSessionsMetaAddSession() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta sessionMeta = SessionMeta.createNew("s1");

        scopeMeta.addSession(sessionMeta);

        assertThat(scopeMeta.getSessions()).hasSize(1);
        assertThat(scopeMeta.getActiveSession()).isEqualTo("s1");
    }

    private void scopeSessionsMetaAddInactiveSession() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta active = SessionMeta.createNew("s1");
        active.setActive(true);
        scopeMeta.addSession(active);
        SessionMeta inactive = SessionMeta.createNew("s2");
        inactive.setActive(false);
        scopeMeta.addSession(inactive);

        assertThat(scopeMeta.getActiveSession()).isEqualTo("s1");
        assertThat(scopeMeta.getSessions()).hasSize(2);
    }

    private void scopeSessionsMetaAddActiveDeactivatesOthers() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        scopeMeta.addSession(s1);
        SessionMeta s2 = SessionMeta.createNew("s2");
        scopeMeta.addSession(s2);

        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isTrue();
        assertThat(scopeMeta.getActiveSession()).isEqualTo("s2");
    }

    private void scopeSessionsMetaGetSession() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta sessionMeta = SessionMeta.createNew("s1");
        scopeMeta.addSession(sessionMeta);

        assertThat(scopeMeta.getSession("s1")).isSameAs(sessionMeta);
    }

    private void scopeSessionsMetaGetSessionNotFound() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");

        assertThat(scopeMeta.getSession("nonexistent")).isNull();
    }

    private void scopeSessionsMetaRemoveSession() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta sessionMeta = SessionMeta.createNew("s1");
        scopeMeta.addSession(sessionMeta);

        SessionMeta removed = scopeMeta.removeSession("s1");

        assertThat(removed).isSameAs(sessionMeta);
        assertThat(scopeMeta.getSessions()).isEmpty();
        assertThat(scopeMeta.getActiveSession()).isNull();
    }

    private void scopeSessionsMetaRemoveSessionNotFound() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");

        assertThat(scopeMeta.removeSession("nonexistent")).isNull();
    }

    private void scopeSessionsMetaActivateSession() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        s1.setActive(true);
        scopeMeta.addSession(s1);
        SessionMeta s2 = SessionMeta.createNew("s2");
        s2.setActive(false);
        scopeMeta.addSession(s2);

        boolean result = scopeMeta.activateSession("s2");

        assertThat(result).isTrue();
        assertThat(s1.isActive()).isFalse();
        assertThat(s2.isActive()).isTrue();
        assertThat(scopeMeta.getActiveSession()).isEqualTo("s2");
    }

    private void scopeSessionsMetaActivateNonexistentSession() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");

        assertThat(scopeMeta.activateSession("nonexistent")).isFalse();
    }

    private void scopeSessionsMetaDeactivateAllSessions() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        scopeMeta.addSession(s1);

        scopeMeta.deactivateAllSessions();

        assertThat(s1.isActive()).isFalse();
        assertThat(scopeMeta.getActiveSession()).isNull();
    }

    private void scopeSessionsMetaGetActiveSession() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        scopeMeta.addSession(s1);

        assertThat(scopeMeta.getActiveSessionMeta()).isSameAs(s1);
    }

    private void scopeSessionsMetaGetActiveSessionNone() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");

        assertThat(scopeMeta.getActiveSessionMeta()).isNull();
    }

    private void scopeSessionsMetaUpdateSessionTimestamp() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        double oldUpdated = s1.getUpdatedAt();
        scopeMeta.addSession(s1);

        boolean result = scopeMeta.updateSessionTimestamp("s1");

        assertThat(result).isTrue();
        assertThat(s1.getUpdatedAt()).isGreaterThanOrEqualTo(oldUpdated);
    }

    private void scopeSessionsMetaUpdateSessionTimestampNotFound() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");

        assertThat(scopeMeta.updateSessionTimestamp("nonexistent")).isFalse();
    }

    private void scopeSessionsMetaIncrementSessionVersion() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        scopeMeta.addSession(s1);

        boolean result = scopeMeta.incrementSessionVersion("s1");

        assertThat(result).isTrue();
        assertThat(s1.getVersion()).isEqualTo(2);
    }

    private void scopeSessionsMetaIncrementSessionVersionNotFound() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");

        assertThat(scopeMeta.incrementSessionVersion("nonexistent")).isFalse();
    }

    private void scopeSessionsMetaToDictAndFromDict() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        scopeMeta.addSession(s1);
        Map<String, Object> data = scopeMeta.toMap();

        ScopeSessionsMeta restored = ScopeSessionsMeta.fromMap(data);

        assertThat(restored.getSessionScopeKey()).isEqualTo("agent:a1:main");
        assertThat(restored.getActiveSession()).isEqualTo("s1");
        assertThat(restored.getSessions()).hasSize(1);
        assertThat(restored.getSessions().getFirst().getSessionId()).isEqualTo("s1");
    }

    private void scopeSessionsMetaSortSessions() {
        ScopeSessionsMeta scopeMeta = new ScopeSessionsMeta("agent:a1:main");
        SessionMeta s1 = SessionMeta.createNew("s1");
        s1.setUpdatedAt(1000.0D);
        SessionMeta s2 = SessionMeta.createNew("s2");
        s2.setUpdatedAt(2000.0D);
        scopeMeta.setSessions(new ArrayList<>(List.of(s1, s2)));

        scopeMeta.sortSessions();

        assertThat(scopeMeta.getSessions()).extracting(SessionMeta::getSessionId).containsExactly("s2", "s1");
    }

    private static Map<String, Object> mutableMap(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            map.put(String.valueOf(values[index]), values[index + 1]);
        }
        return map;
    }
}
