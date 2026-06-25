/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModelHttpVersionTest {
    @Test
    void mapsToJdkVersions() {
        assertThat(ModelHttpVersion.HTTP_1_1.toJdkVersion()).isEqualTo(HttpClient.Version.HTTP_1_1);
        assertThat(ModelHttpVersion.HTTP_2.toJdkVersion()).isEqualTo(HttpClient.Version.HTTP_2);
    }

    @Test
    void parsesKnownAliases() {
        assertThat(ModelHttpVersion.fromValue("HTTP_1_1")).isEqualTo(ModelHttpVersion.HTTP_1_1);
        assertThat(ModelHttpVersion.fromValue("HTTP/1.1")).isEqualTo(ModelHttpVersion.HTTP_1_1);
        assertThat(ModelHttpVersion.fromValue("1.1")).isEqualTo(ModelHttpVersion.HTTP_1_1);
        assertThat(ModelHttpVersion.fromValue("HTTP_2")).isEqualTo(ModelHttpVersion.HTTP_2);
        assertThat(ModelHttpVersion.fromValue("HTTP/2")).isEqualTo(ModelHttpVersion.HTTP_2);
        assertThat(ModelHttpVersion.fromValue("2")).isEqualTo(ModelHttpVersion.HTTP_2);
        assertThat(ModelHttpVersion.fromValue("2.0")).isEqualTo(ModelHttpVersion.HTTP_2);
    }

    @Test
    void rejectsUnsupportedValues() {
        assertThatThrownBy(() -> ModelHttpVersion.fromValue("HTTP/3"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HTTP/3");
        assertThatThrownBy(() -> ModelHttpVersion.fromValue(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported http_version");
        assertThatThrownBy(() -> ModelHttpVersion.fromValue(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported http_version");
    }

    @Test
    void serializesJsonValue() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(ModelHttpVersion.HTTP_1_1)).isEqualTo("\"HTTP_1_1\"");
    }
}
