/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.session_controller;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionScopeTest {

    @Test
    void mainScopeSerializesAndParses() {
        assertThat(new MainScope().toString()).isEqualTo("main");
        assertThat(MainScope.fromString("main")).isEqualTo(new MainScope());
    }

    @Test
    void mainScopeRejectsUnknownValues() {
        assertThatThrownBy(() -> MainScope.fromString("other"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Expected 'main'");
    }

    @Test
    void directSubjectSerializesAndParses() {
        assertThat(new DirectSubject("user1").toString()).isEqualTo("direct:user1");
        assertThat(DirectSubject.fromString("direct:user1")).isEqualTo(new DirectSubject("user1"));
    }

    @Test
    void directSubjectRejectsInvalidValues() {
        assertThatThrownBy(() -> DirectSubject.fromString("group:user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with 'direct:'");
        assertThatThrownBy(() -> DirectSubject.fromString("direct:"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be empty");
    }

    @Test
    void groupSubjectSerializesAndParses() {
        assertThat(new GroupSubject("grp1").toString()).isEqualTo("group:grp1");
        assertThat(GroupSubject.fromString("group:grp1")).isEqualTo(new GroupSubject("grp1"));
    }

    @Test
    void groupUserSubjectSerializesAndParses() {
        GroupUserSubject subject = new GroupUserSubject("grp1", "user1");
        assertThat(subject.toString()).isEqualTo("group:grp1:user:user1");
        assertThat(GroupUserSubject.fromString("group:grp1:user:user1")).isEqualTo(subject);
    }

    @Test
    void groupUserSubjectRejectsInvalidFormat() {
        assertThatThrownBy(() -> GroupUserSubject.fromString("group:grp1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("format");
    }

    @Test
    void sessionScopeSerializesAndParsesSupportedSubjects() {
        SessionScope scopeOnly = new SessionScope(new MainScope(), null);
        SessionScope directScope = new SessionScope(new MainScope(), new DirectSubject("user1"));
        SessionScope groupScope = new SessionScope(new MainScope(), new GroupSubject("grp1"));
        SessionScope groupUserScope = new SessionScope(new MainScope(), new GroupUserSubject("grp1", "user1"));

        assertThat(scopeOnly.toString()).isEqualTo("main");
        assertThat(directScope.toString()).isEqualTo("main:direct:user1");
        assertThat(groupScope.toString()).isEqualTo("main:group:grp1");
        assertThat(groupUserScope.toString()).isEqualTo("main:group:grp1:user:user1");

        assertThat(SessionScope.fromString("main")).isEqualTo(scopeOnly);
        assertThat(SessionScope.fromString("main:direct:user1")).isEqualTo(directScope);
        assertThat(SessionScope.fromString("main:group:grp1")).isEqualTo(groupScope);
        assertThat(SessionScope.fromString("main:group:grp1:user:user1")).isEqualTo(groupUserScope);
    }

    @Test
    void sessionScopeRejectsUnknownScopeAndSubject() {
        assertThatThrownBy(() -> SessionScope.fromString("unknown:direct:user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown scope");
        assertThatThrownBy(() -> SessionScope.fromString("main:unknown_format"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown subject format");
    }

    @Test
    void sessionScopeKeySerializesAndParses() {
        SessionScope scope = new SessionScope(new MainScope(), new DirectSubject("user1"));
        SessionScopeKey key = new SessionScopeKey("agent1", scope);

        assertThat(key.toString()).isEqualTo("agent:agent1:main:direct:user1");
        assertThat(SessionScopeKey.fromString("agent:agent1:main:direct:user1")).isEqualTo(key);
        assertThat(SessionScopeKey.fromString("agent:agent2:main"))
                .isEqualTo(new SessionScopeKey("agent2", new SessionScope(new MainScope(), null)));
    }

    @Test
    void sessionScopeKeyRejectsInvalidPrefix() {
        assertThatThrownBy(() -> SessionScopeKey.fromString("main:direct:user1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must start with 'agent:'");
    }
}
