/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.common;

import org.junit.jupiter.api.Test;

import java.util.AbstractMap;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryBaseUtilsTest {

    @Test
    void formatsAndParsesIndexNames() {
        assertThat(MemoryBaseUtils.generateIdxName("u1", "g1", "episode"))
                .isEqualTo("uid_u1_gid_g1_mtype_episode");
        assertThat(MemoryBaseUtils.parseMemtypeFromIdxName("uid_u1_gid_g1_mtype_episode"))
                .isEqualTo("episode");
    }

    @Test
    void convertsHitPairsIntoIdsAndScores() {
        MemoryBaseUtils.MemoryHitInfos infos = MemoryBaseUtils.parseMemoryHitInfos(List.of(
                new AbstractMap.SimpleEntry<>("doc-1", 0.9d),
                new AbstractMap.SimpleEntry<>("doc-2", 0.4d)));

        assertThat(infos.getIds()).containsExactly("doc-1", "doc-2");
        assertThat(infos.getScores()).containsEntry("doc-1", 0.9d).containsEntry("doc-2", 0.4d);
    }
}
