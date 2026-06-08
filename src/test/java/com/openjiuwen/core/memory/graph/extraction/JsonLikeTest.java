/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JsonLikeTest {

    @Test
    void recognizesJsonObjectsAndArrays() {
        assertThat(JsonLike.isJsonLike(Map.of("a", 1))).isTrue();
        assertThat(JsonLike.isJsonLike(List.of(1, 2, 3))).isTrue();
        assertThat(JsonLike.isJsonLike("nope")).isFalse();
    }

    @Test
    void exposesTypedViews() {
        assertThat(JsonLike.asObject(Map.of("name", "demo"))).isPresent();
        assertThat(JsonLike.asList(List.of("demo"))).isPresent();
        assertThat(JsonLike.asObject(List.of("demo"))).isEmpty();
    }
}
