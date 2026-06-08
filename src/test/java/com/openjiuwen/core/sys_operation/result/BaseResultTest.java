/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sys_operation.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.openjiuwen.core.common.exception.StatusCode;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseResultTest {

    @Test
    void buildOperationErrorResultFormatsMessageAndPreservesData() {
        SampleResult result = BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SANDBOX_GATEWAY_ERROR,
                Map.of("operation", "publish", "error_msg", "socket closed"),
                SampleResult.class,
                "payload"
        );

        assertThat(result.getCode()).isEqualTo(StatusCode.SYS_OPERATION_SANDBOX_GATEWAY_ERROR.code());
        assertThat(result.getMessage()).isEqualTo("sandbox gateway error, operation: publish, error: socket closed");
        assertThat(result.getData()).isEqualTo("payload");
    }

    @Test
    void extraFieldsOverrideDefaultFieldsLikePythonKwargs() {
        SampleResult result = BaseResult.buildOperationErrorResult(
                StatusCode.ERROR,
                Map.of(),
                SampleResult.class,
                "payload",
                Map.of("message", "overridden", "detail", "custom")
        );

        assertThat(result.getCode()).isEqualTo(StatusCode.ERROR.code());
        assertThat(result.getMessage()).isEqualTo("overridden");
        assertThat(result.getDetail()).isEqualTo("custom");
    }

    @Test
    void missingFormatKeysFailFast() {
        assertThatThrownBy(() -> BaseResult.buildOperationErrorResult(
                StatusCode.SYS_OPERATION_SANDBOX_GATEWAY_ERROR,
                Map.of("operation", "publish"),
                SampleResult.class
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error_msg");
    }

    public static final class SampleResult extends BaseResult<String> {
        private String detail;

        public String getDetail() {
            return detail;
        }

        public void setDetail(String detail) {
            this.detail = detail;
        }
    }
}
