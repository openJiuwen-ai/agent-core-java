/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

/**
 * <p>Mirrors Python's {@code tests.unit_tests.core.session.session_controller.test_scope} in
 * {@code tests/unit_tests/core/session/session_controller/test_scope.py}.</p>
 */
class SessionScopePythonParityTest {

    @TestFactory
    Collection<DynamicTest> sessionScopePythonParity() {
        List<DynamicTest> tests = new ArrayList<>();
        add(tests, "TestMainScope::test_str", this::mainScopeStr);
        add(tests, "TestMainScope::test_from_string_valid", this::mainScopeFromStringValid);
        add(tests, "TestMainScope::test_from_string_invalid", this::mainScopeFromStringInvalid);
        add(tests, "TestMainScope::test_equality", this::mainScopeEquality);
        add(tests, "TestMainScope::test_hash", this::mainScopeHash);
        add(tests, "TestMainScope::test_not_equal_other_type", this::mainScopeNotEqualOtherType);
        add(tests, "TestDirectSubject::test_str", this::directSubjectStr);
        add(tests, "TestDirectSubject::test_from_string_valid", this::directSubjectFromStringValid);
        add(tests, "TestDirectSubject::test_from_string_invalid_prefix", this::directSubjectInvalidPrefix);
        add(tests, "TestDirectSubject::test_from_string_empty_user_id", this::directSubjectEmptyUserId);
        add(tests, "TestDirectSubject::test_equality", this::directSubjectEquality);
        add(tests, "TestDirectSubject::test_hash", this::directSubjectHash);
        add(tests, "TestGroupSubject::test_str", this::groupSubjectStr);
        add(tests, "TestGroupSubject::test_from_string_valid", this::groupSubjectFromStringValid);
        add(tests, "TestGroupSubject::test_from_string_invalid_prefix", this::groupSubjectInvalidPrefix);
        add(tests, "TestGroupSubject::test_from_string_empty_group_id", this::groupSubjectEmptyGroupId);
        add(tests, "TestGroupSubject::test_equality", this::groupSubjectEquality);
        add(tests, "TestGroupSubject::test_hash", this::groupSubjectHash);
        add(tests, "TestGroupUserSubject::test_str", this::groupUserSubjectStr);
        add(tests, "TestGroupUserSubject::test_from_string_valid", this::groupUserSubjectFromStringValid);
        add(tests, "TestGroupUserSubject::test_from_string_invalid_format",
                this::groupUserSubjectInvalidFormat);
        add(tests, "TestGroupUserSubject::test_from_string_empty_ids", this::groupUserSubjectEmptyIds);
        add(tests, "TestGroupUserSubject::test_equality", this::groupUserSubjectEquality);
        add(tests, "TestGroupUserSubject::test_hash", this::groupUserSubjectHash);
        add(tests, "TestSessionScope::test_str_scope_only", this::sessionScopeStrScopeOnly);
        add(tests, "TestSessionScope::test_str_scope_with_direct_subject", this::sessionScopeStrDirect);
        add(tests, "TestSessionScope::test_str_scope_with_group_subject", this::sessionScopeStrGroup);
        add(tests, "TestSessionScope::test_str_scope_with_group_user_subject", this::sessionScopeStrGroupUser);
        add(tests, "TestSessionScope::test_from_string_main_only", this::sessionScopeFromStringMainOnly);
        add(tests, "TestSessionScope::test_from_string_direct", this::sessionScopeFromStringDirect);
        add(tests, "TestSessionScope::test_from_string_group", this::sessionScopeFromStringGroup);
        add(tests, "TestSessionScope::test_from_string_group_user", this::sessionScopeFromStringGroupUser);
        add(tests, "TestSessionScope::test_from_string_unknown_scope", this::sessionScopeUnknownScope);
        add(tests, "TestSessionScope::test_from_string_unknown_subject", this::sessionScopeUnknownSubject);
        add(tests, "TestSessionScope::test_frozen", this::sessionScopeFrozen);
        add(tests, "TestSessionScopeKey::test_str", this::sessionScopeKeyStr);
        add(tests, "TestSessionScopeKey::test_from_string_valid", this::sessionScopeKeyFromStringValid);
        add(tests, "TestSessionScopeKey::test_from_string_main_only", this::sessionScopeKeyFromStringMainOnly);
        add(tests, "TestSessionScopeKey::test_from_string_invalid_prefix", this::sessionScopeKeyInvalidPrefix);
        add(tests, "TestSessionScopeKey::test_equality", this::sessionScopeKeyEquality);
        add(tests, "TestSessionScopeKey::test_hash", this::sessionScopeKeyHash);
        return tests;
    }

    private static void add(List<DynamicTest> tests, String pythonName, Executable executable) {
        tests.add(DynamicTest.dynamicTest(pythonName, executable));
    }

    private void mainScopeStr() {
        assertEquals("main", new MainScope().toString());
    }

    private void mainScopeFromStringValid() {
        assertInstanceOf(MainScope.class, MainScope.fromString("main"));
    }

    private void mainScopeFromStringInvalid() {
        assertMessageContains(() -> MainScope.fromString("other"), "Expected 'main'");
    }

    private void mainScopeEquality() {
        assertEquals(new MainScope(), new MainScope());
    }

    private void mainScopeHash() {
        assertEquals(new MainScope().hashCode(), new MainScope().hashCode());
    }

    private void mainScopeNotEqualOtherType() {
        assertNotEquals(new MainScope(), "main");
    }

    private void directSubjectStr() {
        assertEquals("direct:user1", new DirectSubject("user1").toString());
    }

    private void directSubjectFromStringValid() {
        DirectSubject subject = DirectSubject.fromString("direct:user1");
        assertEquals("user1", subject.userId());
    }

    private void directSubjectInvalidPrefix() {
        assertMessageContains(() -> DirectSubject.fromString("group:user1"), "must start with 'direct:'");
    }

    private void directSubjectEmptyUserId() {
        assertMessageContains(() -> DirectSubject.fromString("direct:"), "userId cannot be empty");
    }

    private void directSubjectEquality() {
        assertAll(
                () -> assertEquals(new DirectSubject("user1"), new DirectSubject("user1")),
                () -> assertNotEquals(new DirectSubject("user1"), new DirectSubject("user2")));
    }

    private void directSubjectHash() {
        Set<DirectSubject> subjects = new HashSet<>();
        subjects.add(new DirectSubject("user1"));
        subjects.add(new DirectSubject("user1"));

        assertAll(
                () -> assertEquals(new DirectSubject("user1").hashCode(), new DirectSubject("user1").hashCode()),
                () -> assertEquals(1, subjects.size()));
    }

    private void groupSubjectStr() {
        assertEquals("group:grp1", new GroupSubject("grp1").toString());
    }

    private void groupSubjectFromStringValid() {
        GroupSubject subject = GroupSubject.fromString("group:grp1");
        assertEquals("grp1", subject.groupId());
    }

    private void groupSubjectInvalidPrefix() {
        assertMessageContains(() -> GroupSubject.fromString("direct:grp1"), "must start with 'group:'");
    }

    private void groupSubjectEmptyGroupId() {
        assertMessageContains(() -> GroupSubject.fromString("group:"), "groupId cannot be empty");
    }

    private void groupSubjectEquality() {
        assertAll(
                () -> assertEquals(new GroupSubject("grp1"), new GroupSubject("grp1")),
                () -> assertNotEquals(new GroupSubject("grp1"), new GroupSubject("grp2")));
    }

    private void groupSubjectHash() {
        assertEquals(new GroupSubject("grp1").hashCode(), new GroupSubject("grp1").hashCode());
    }

    private void groupUserSubjectStr() {
        assertEquals("group:grp1:user:user1", new GroupUserSubject("grp1", "user1").toString());
    }

    private void groupUserSubjectFromStringValid() {
        GroupUserSubject subject = GroupUserSubject.fromString("group:grp1:user:user1");
        assertAll(
                () -> assertEquals("grp1", subject.groupId()),
                () -> assertEquals("user1", subject.userId()));
    }

    private void groupUserSubjectInvalidFormat() {
        assertMessageContains(() -> GroupUserSubject.fromString("group:grp1"), "format");
    }

    private void groupUserSubjectEmptyIds() {
        assertAll(
                () -> assertMessageContains(() -> GroupUserSubject.fromString("group::user:user1"),
                        "cannot be empty"),
                () -> assertMessageContains(() -> GroupUserSubject.fromString("group:grp1:user:"),
                        "cannot be empty"));
    }

    private void groupUserSubjectEquality() {
        assertAll(
                () -> assertEquals(new GroupUserSubject("g1", "u1"), new GroupUserSubject("g1", "u1")),
                () -> assertNotEquals(new GroupUserSubject("g1", "u1"), new GroupUserSubject("g1", "u2")),
                () -> assertNotEquals(new GroupUserSubject("g1", "u1"), new GroupUserSubject("g2", "u1")));
    }

    private void groupUserSubjectHash() {
        assertEquals(new GroupUserSubject("g1", "u1").hashCode(), new GroupUserSubject("g1", "u1").hashCode());
    }

    private void sessionScopeStrScopeOnly() {
        assertEquals("main", new SessionScope(new MainScope(), null).toString());
    }

    private void sessionScopeStrDirect() {
        SessionScope scope = new SessionScope(new MainScope(), new DirectSubject("user1"));
        assertEquals("main:direct:user1", scope.toString());
    }

    private void sessionScopeStrGroup() {
        SessionScope scope = new SessionScope(new MainScope(), new GroupSubject("grp1"));
        assertEquals("main:group:grp1", scope.toString());
    }

    private void sessionScopeStrGroupUser() {
        SessionScope scope = new SessionScope(new MainScope(), new GroupUserSubject("grp1", "user1"));
        assertEquals("main:group:grp1:user:user1", scope.toString());
    }

    private void sessionScopeFromStringMainOnly() {
        SessionScope scope = SessionScope.fromString("main");
        assertAll(
                () -> assertInstanceOf(MainScope.class, scope.scope()),
                () -> assertNull(scope.subject()));
    }

    private void sessionScopeFromStringDirect() {
        SessionScope scope = SessionScope.fromString("main:direct:user1");
        DirectSubject subject = assertInstanceOf(DirectSubject.class, scope.subject());
        assertAll(
                () -> assertInstanceOf(MainScope.class, scope.scope()),
                () -> assertEquals("user1", subject.userId()));
    }

    private void sessionScopeFromStringGroup() {
        SessionScope scope = SessionScope.fromString("main:group:grp1");
        GroupSubject subject = assertInstanceOf(GroupSubject.class, scope.subject());
        assertAll(
                () -> assertInstanceOf(MainScope.class, scope.scope()),
                () -> assertEquals("grp1", subject.groupId()));
    }

    private void sessionScopeFromStringGroupUser() {
        SessionScope scope = SessionScope.fromString("main:group:grp1:user:user1");
        GroupUserSubject subject = assertInstanceOf(GroupUserSubject.class, scope.subject());
        assertAll(
                () -> assertInstanceOf(MainScope.class, scope.scope()),
                () -> assertEquals("grp1", subject.groupId()),
                () -> assertEquals("user1", subject.userId()));
    }

    private void sessionScopeUnknownScope() {
        assertMessageContains(() -> SessionScope.fromString("unknown:direct:user1"), "Unknown scope");
    }

    private void sessionScopeUnknownSubject() {
        assertMessageContains(() -> SessionScope.fromString("main:unknown_format"), "Unknown subject format");
    }

    private void sessionScopeFrozen() throws NoSuchFieldException {
        assertAll(
                () -> assertTrue(Modifier.isFinal(SessionScope.class.getDeclaredField("scope").getModifiers())),
                () -> assertFalse(hasMethodNamed("setScope")));
    }

    private void sessionScopeKeyStr() {
        SessionScope scope = new SessionScope(new MainScope(), new DirectSubject("user1"));
        SessionScopeKey key = new SessionScopeKey("agent1", scope);
        assertEquals("agent:agent1:main:direct:user1", key.toString());
    }

    private void sessionScopeKeyFromStringValid() {
        SessionScopeKey key = SessionScopeKey.fromString("agent:agent1:main:direct:user1");
        DirectSubject subject = assertInstanceOf(DirectSubject.class, key.sessionScope().subject());
        assertAll(
                () -> assertEquals("agent1", key.agentId()),
                () -> assertInstanceOf(MainScope.class, key.sessionScope().scope()),
                () -> assertEquals("user1", subject.userId()));
    }

    private void sessionScopeKeyFromStringMainOnly() {
        SessionScopeKey key = SessionScopeKey.fromString("agent:agent2:main");
        assertAll(
                () -> assertEquals("agent2", key.agentId()),
                () -> assertNull(key.sessionScope().subject()));
    }

    private void sessionScopeKeyInvalidPrefix() {
        assertMessageContains(() -> SessionScopeKey.fromString("main:direct:user1"),
                "must start with 'agent:'");
    }

    private void sessionScopeKeyEquality() {
        SessionScope scope = new SessionScope(new MainScope(), null);
        SessionScopeKey key1 = new SessionScopeKey("a1", scope);
        SessionScopeKey key2 = new SessionScopeKey("a1", scope);
        assertEquals(key1, key2);
    }

    private void sessionScopeKeyHash() {
        SessionScope scope = new SessionScope(new MainScope(), null);
        SessionScopeKey key1 = new SessionScopeKey("a1", scope);
        SessionScopeKey key2 = new SessionScopeKey("a1", scope);
        Set<SessionScopeKey> keys = new HashSet<>();
        keys.add(key1);
        keys.add(key2);

        assertAll(
                () -> assertEquals(key1.hashCode(), key2.hashCode()),
                () -> assertEquals(1, keys.size()));
    }

    private static void assertMessageContains(Executable executable, String expectedMessagePart) {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, executable);
        assertTrue(exception.getMessage().contains(expectedMessagePart));
    }

    private static boolean hasMethodNamed(String methodName) {
        for (java.lang.reflect.Method method : SessionScope.class.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }
}
