/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TripleBeamTest {

    @Test
    void beamExposesPythonStyleContainerOperations() {
        RetrievalResult first = new RetrievalResult("triple-1", 0.9d, Map.of("rank", 1), "doc-1", "chunk-1");
        RetrievalResult second = new RetrievalResult("triple-2", 0.8d, Map.of("rank", 2), "doc-2", "chunk-2");

        TripleBeam beam = new TripleBeam(List.of(first, second), 1.7d);

        assertThat(beam.size()).isEqualTo(2);
        assertThat(beam.get(0)).isSameAs(first);
        assertThat(beam.contains(new RetrievalResult("triple-1", 0.0d, Map.of(), "", ""))).isTrue();
        assertThat(beam.contains(new RetrievalResult("missing", 0.0d, Map.of(), "", ""))).isFalse();
        assertThat(beam.getTriples()).containsExactly(first, second);
        assertThat(beam.getScore()).isEqualTo(1.7d);
        assertThat(beam).containsExactly(first, second);
    }
}
