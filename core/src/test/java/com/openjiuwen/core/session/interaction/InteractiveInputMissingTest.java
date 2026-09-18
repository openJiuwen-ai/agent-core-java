/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.session.interaction;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * <p>Mirrors Python's {@code TestInteractiveInput} in
 * {@code tests/unit_tests/core/session/interaction/test_interactive_input.py}.</p>
 */
class InteractiveInputMissingTest {

    @Test
    void testInvalidRawInputs() {
        BaseError error = assertThrows(BaseError.class, () -> new InteractiveInput(null));

        assertThat(error.getCode()).isEqualTo(StatusCode.INTERACTION_INPUT_INVALID.getCode());
    }

    @Test
    void testInvalidUpdate() {
        BaseError error = assertThrows(BaseError.class, () -> {
            InteractiveInput interactiveInput = new InteractiveInput();
            interactiveInput.update("id", null);
        });

        assertThat(error.getCode()).isEqualTo(StatusCode.INTERACTION_INPUT_INVALID.getCode());
    }
}
