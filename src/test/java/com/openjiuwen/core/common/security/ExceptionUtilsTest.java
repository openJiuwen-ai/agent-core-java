package com.openjiuwen.core.common.security;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExceptionUtilsTest {

    @Test
    void formatValidationErrorMatchesPythonJoinBehavior() {
        String formatted = ExceptionUtils.formatValidationError(List.of(
                Map.of("loc", List.of("payload", 0, "name"), "msg", "field required"),
                Map.of("loc", List.of("payload", "score"))
        ));

        assertThat(formatted).isEqualTo("payload.0.name: field required\npayload.score: Unknown error");
    }
}
