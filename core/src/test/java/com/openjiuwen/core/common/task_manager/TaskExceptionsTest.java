/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.common.task_manager;

import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TaskExceptionsTest {

    @Test
    void taskNotFoundErrorUsesFixedStatusAndCarriesPayload() {
        TaskNotFoundError error = new TaskNotFoundError("missing", Map.of("task", "t-1"), null, Map.of("owner", "slot"));

        assertThat(error.getStatus()).isEqualTo(StatusCode.COMMON_TASK_NOT_FOUND);
        assertThat(error.getMessage()).isEqualTo("missing");
        assertThat(error.getDetails()).isEqualTo(Map.of("task", "t-1"));
        assertThat(error.getParams()).containsEntry("owner", "slot");
    }

    @Test
    void duplicateTaskErrorUsesFixedStatusAndDefaults() {
        DuplicateTaskError error = new DuplicateTaskError();

        assertThat(error.getStatus()).isEqualTo(StatusCode.COMMON_TASK_CONFIG_ERROR);
        assertThat(error.getParams()).isEmpty();
    }
}
