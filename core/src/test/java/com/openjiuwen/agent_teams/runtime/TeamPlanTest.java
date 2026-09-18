/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agent_teams.runtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class TeamPlanTest {

    @Test
    void mapSpecUsesSnakeCaseFlag() {
        assertThat(TeamPlan.isTeamPlanEnabled(Map.of("enable_team_plan", true))).isTrue();
        assertThat(TeamPlan.isTeamPlanEnabled(Map.of("enable_team_plan", ""))).isFalse();
    }

    @Test
    void beanSpecUsesCamelCaseAccessor() {
        assertThat(TeamPlan.isTeamPlanEnabled(new BeanSpec(true))).isTrue();
        assertThat(TeamPlan.isTeamPlanEnabled(new BeanSpec(false))).isFalse();
    }

    @Test
    void missingFlagDefaultsToFalse() {
        assertThat(TeamPlan.isTeamPlanEnabled(Map.of())).isFalse();
        assertThat(TeamPlan.isTeamPlanEnabled(new Object())).isFalse();
    }

    static final class BeanSpec {
        private final boolean enableTeamPlan;

        BeanSpec(boolean enableTeamPlan) {
            this.enableTeamPlan = enableTeamPlan;
        }

        public boolean isEnableTeamPlan() {
            return enableTeamPlan;
        }
    }
}
