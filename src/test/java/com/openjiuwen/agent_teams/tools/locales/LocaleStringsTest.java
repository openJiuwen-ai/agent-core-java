/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.tools.locales;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LocaleStringsTest {

    @Test
    void chineseAndEnglishLocaleTablesShareTheSameKeys() {
        assertThat(CnLocaleStrings.getAll()).hasSize(55);
        assertThat(EnLocaleStrings.getAll()).hasSize(55);
        assertThat(CnLocaleStrings.getAll().keySet()).containsExactlyInAnyOrderElementsOf(EnLocaleStrings.getAll().keySet());
    }

    @Test
    void localeValuesExposeRepresentativeStrings() {
        assertThat(CnLocaleStrings.get("send_message.summary")).isNotBlank();
        assertThat(CnLocaleStrings.get("spawn_member.role_type")).contains("bridge_agent");
        assertThat(EnLocaleStrings.get("build_team.display_name"))
                .isEqualTo("Human-readable display label for the team (e.g. 'Backend Platform Squad')");
        assertThat(EnLocaleStrings.get("approve_tool.auto_confirm")).contains("auto-approve future calls");
    }
}
