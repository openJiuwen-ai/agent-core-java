/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.singleagent.interrupt;

import java.io.Serial;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Ask-user interrupt request with structured questions.
 * <p>
 * Aligned with Python {@code AskUserRequest}: {@code message} stays empty and the real
 * content lives in {@code questions}.
 *
 * @since 0.1.14
 */
public class AskUserRequest extends InterruptRequest {
    @Serial
    private static final long serialVersionUID = 1L;

    private List<Map<String, Object>> questions = new ArrayList<>();

    /**
     * Return questions to present to the user.
     *
     * @return questions list
     * @since 0.1.14
     */
    public List<Map<String, Object>> getQuestions() {
        return questions;
    }

    /**
     * Set questions to present to the user.
     *
     * @param questions questions from ask_user tool arguments
     * @since 0.1.14
     */
    public void setQuestions(List<Map<String, Object>> questions) {
        this.questions = questions != null ? questions : new ArrayList<>();
    }
}
