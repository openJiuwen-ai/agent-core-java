/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SessionScopeFactoryTest {

    @Test
    void createBuiltInScopesMatchesPythonFactory() {
        assertThat(SessionScopeFactory.createMain())
                .isEqualTo(new SessionScope(new MainScope(), null));
        assertThat(SessionScopeFactory.createDirect("user123"))
                .isEqualTo(new SessionScope(new MainScope(), new DirectSubject("user123")));
        assertThat(SessionScopeFactory.createGroup("group456"))
                .isEqualTo(new SessionScope(new MainScope(), new GroupSubject("group456")));
        assertThat(SessionScopeFactory.createGroupUser("group456", "user789"))
                .isEqualTo(new SessionScope(new MainScope(), new GroupUserSubject("group456", "user789")));
    }

    @Test
    void createCustomPreservesProvidedComponents() {
        Scope scope = new MainScope();
        Subject subject = new DirectSubject("user1");

        assertThat(SessionScopeFactory.createCustom(scope, subject))
                .isEqualTo(new SessionScope(scope, subject));
        assertThat(SessionScopeFactory.createCustom(scope, null))
                .isEqualTo(new SessionScope(scope, null));
    }

    @Test
    void fromStringDelegatesToSessionScopeParser() {
        assertThat(SessionScopeFactory.fromString("main"))
                .isEqualTo(new SessionScope(new MainScope(), null));
        assertThat(SessionScopeFactory.fromString("main:group:grp1:user:user1"))
                .isEqualTo(new SessionScope(new MainScope(), new GroupUserSubject("grp1", "user1")));
    }
}
