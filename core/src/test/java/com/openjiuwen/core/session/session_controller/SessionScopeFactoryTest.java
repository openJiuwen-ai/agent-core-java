/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * <p>Mirrors Python's {@code SessionScopeFactory} in
 * {@code openjiuwen/core/session/session_controller/scope_factory.py}.</p>
 *
 * <p>Mirrors Python's {@code TestSessionScopeFactory} in
 * {@code tests/unit_tests/core/session/session_controller/test_scope_factory.py}.</p>
 */
class SessionScopeFactoryTest {

    @Test
    void testCreateMain() {
        SessionScope scope = SessionScopeFactory.createMain();

        assertThat(scope.scope()).isInstanceOf(MainScope.class);
        assertThat(scope.subject()).isNull();
        assertThat(scope.toString()).isEqualTo("main");
    }

    @Test
    void testCreateDirect() {
        SessionScope scope = SessionScopeFactory.createDirect("user1");

        assertThat(scope.scope()).isInstanceOf(MainScope.class);
        assertThat(scope.subject()).isInstanceOf(DirectSubject.class);
        assertThat(((DirectSubject) scope.subject()).userId()).isEqualTo("user1");
        assertThat(scope.toString()).isEqualTo("main:direct:user1");
    }

    @Test
    void testCreateGroup() {
        SessionScope scope = SessionScopeFactory.createGroup("grp1");

        assertThat(scope.scope()).isInstanceOf(MainScope.class);
        assertThat(scope.subject()).isInstanceOf(GroupSubject.class);
        assertThat(((GroupSubject) scope.subject()).groupId()).isEqualTo("grp1");
        assertThat(scope.toString()).isEqualTo("main:group:grp1");
    }

    @Test
    void testCreateGroupUser() {
        SessionScope scope = SessionScopeFactory.createGroupUser("grp1", "user1");

        assertThat(scope.scope()).isInstanceOf(MainScope.class);
        assertThat(scope.subject()).isInstanceOf(GroupUserSubject.class);
        GroupUserSubject subject = (GroupUserSubject) scope.subject();
        assertThat(subject.groupId()).isEqualTo("grp1");
        assertThat(subject.userId()).isEqualTo("user1");
        assertThat(scope.toString()).isEqualTo("main:group:grp1:user:user1");
    }

    @Test
    void testCreateCustom() {
        Scope scope = new MainScope();
        Subject subject = new DirectSubject("u1");

        SessionScope sessionScope = SessionScopeFactory.createCustom(scope, subject);

        assertThat(sessionScope.toString()).isEqualTo("main:direct:u1");
    }

    @Test
    void testCreateCustomNoSubject() {
        SessionScope scope = SessionScopeFactory.createCustom(new MainScope(), null);

        assertThat(scope.subject()).isNull();
        assertThat(scope.toString()).isEqualTo("main");
    }

    @Test
    void testFromStringMain() {
        SessionScope scope = SessionScopeFactory.fromString("main");

        assertThat(scope.scope()).isInstanceOf(MainScope.class);
        assertThat(scope.subject()).isNull();
    }

    @Test
    void testFromStringDirect() {
        SessionScope scope = SessionScopeFactory.fromString("main:direct:user1");

        assertThat(scope.subject()).isInstanceOf(DirectSubject.class);
        assertThat(((DirectSubject) scope.subject()).userId()).isEqualTo("user1");
    }

    @Test
    void testFromStringGroup() {
        SessionScope scope = SessionScopeFactory.fromString("main:group:grp1");

        assertThat(scope.subject()).isInstanceOf(GroupSubject.class);
        assertThat(((GroupSubject) scope.subject()).groupId()).isEqualTo("grp1");
    }

    @Test
    void testFromStringGroupUser() {
        SessionScope scope = SessionScopeFactory.fromString("main:group:grp1:user:user1");

        assertThat(scope.subject()).isInstanceOf(GroupUserSubject.class);
        GroupUserSubject subject = (GroupUserSubject) scope.subject();
        assertThat(subject.groupId()).isEqualTo("grp1");
        assertThat(subject.userId()).isEqualTo("user1");
    }
}
