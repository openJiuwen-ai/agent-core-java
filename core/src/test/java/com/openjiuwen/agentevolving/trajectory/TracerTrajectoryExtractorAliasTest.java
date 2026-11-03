/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.agentevolving.trajectory;

import com.openjiuwen.agentevolving.trajectory.extractor.TrajectoryExtractor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ensures the Python-compatible alias remains a thin subclass of {@link TrajectoryExtractor}.
 */
class TracerTrajectoryExtractorAliasTest {

    @Test
    void aliasExtendsTrajectoryExtractor() {
        TracerTrajectoryExtractor extractor = new TracerTrajectoryExtractor();
        assertThat(extractor).isInstanceOf(TrajectoryExtractor.class);
        assertThat(extractor.extract(new Object(), "case-1").getCaseId()).isEqualTo("case-1");
    }
}
