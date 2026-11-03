/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.constants;

import java.util.Map;

/**
 * Mirrors Python's {@code IntentDetectionConstants} in
 * {@code openjiuwen/core/controller/legacy/constants.py}.
 */
public final class IntentDetectionConstants {

    public static final String USER_PROMPT = "user_prompt";
    public static final String CATEGORY_LIST = "category_list";
    public static final String DEFAULT_CLASS = "default_class";
    public static final String ENABLE_HISTORY = "enable_history";
    public static final String ENABLE_INPUT = "enable_input";
    public static final String EXAMPLE_CONTENT = "example_content";
    public static final String CHAT_HISTORY_MAX_TURN = "chat_history_max_turn";
    public static final String CHAT_HISTORY = "chat_history";
    public static final String INPUT = "input";

    public static final Map<String, String> ROLE_MAP = Map.of(
            "user", "用户",
            "assistant", "助手",
            "system", "系统"
    );

    private IntentDetectionConstants() {
    }
}
