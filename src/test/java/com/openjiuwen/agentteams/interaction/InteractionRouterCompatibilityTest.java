/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentteams.interaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import java.util.Optional;

/**
 * Mirrors Python 0.1.15 {@code interaction/test_router.py} + {@code test_payload.py}.
 * Validates mention parsing and reserved-name checks.
 */
class InteractionRouterCompatibilityTest {

    // --- parseMention ---

    @Test
    void parseMention_validMention_returnsTargetAndBody() {
        Optional<MentionRoute> result = Router.parseMention("@dev-1 hi there");
        assertThat(result).isPresent();
        assertThat(result.get().target()).isEqualTo("dev-1");
        assertThat(result.get().body()).isEqualTo("hi there");
    }

    @Test
    void parseMention_noAtPrefix_returnsEmpty() {
        assertThat(Router.parseMention("just a plain question")).isEmpty();
    }

    @Test
    void parseMention_emptyInput_returnsEmpty() {
        assertThat(Router.parseMention("")).isEmpty();
    }

    @Test
    void parseMention_nullInput_returnsEmpty() {
        assertThat(Router.parseMention(null)).isEmpty();
    }

    @Test
    void parseMention_atWithoutBody_returnsEmpty() {
        // Python: @dev-1 without body → not a valid mention (no space after target)
        assertThat(Router.parseMention("@dev-1")).isEmpty();
    }

    @Test
    void parseMention_hashWithoutSpace_isNotMention() {
        // Python: #hashtag is just text, not a mention
        assertThat(Router.parseMention("#hashtag is just text")).isEmpty();
    }

    @Test
    void parseMention_inlineAtInBody_isNotMention() {
        // Python: "hello @world this is content" → no leading @, not a mention
        assertThat(Router.parseMention("hello @world this is content")).isEmpty();
    }

    @Test
    void parseMention_atAll_broadcast() {
        Optional<MentionRoute> result = Router.parseMention("@all heads up everyone");
        assertThat(result).isPresent();
        assertThat(result.get().target()).isEqualTo("all");
        assertThat(result.get().body()).isEqualTo("heads up everyone");
    }

    @Test
    void parseMention_atStar_broadcast() {
        Optional<MentionRoute> result = Router.parseMention("@* status sync");
        assertThat(result).isPresent();
        assertThat(result.get().target()).isEqualTo("*");
    }

    // --- isReservedName ---

    @Test
    void isReservedName_humanAgent() {
        assertThat(Router.isReservedName("human_agent")).isTrue();
    }

    @Test
    void isReservedName_user() {
        assertThat(Router.isReservedName("user")).isTrue();
    }

    @Test
    void isReservedName_leader() {
        assertThat(Router.isReservedName("team_leader")).isTrue();
    }

    @Test
    void isReservedName_normalMember() {
        assertThat(Router.isReservedName("dev-1")).isFalse();
    }
}
