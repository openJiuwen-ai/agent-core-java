/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.controller.legacy.constants;

import java.util.Map;

/**
 * Intent detection constants.
 * Mirrors Python's {@code IntentDetectionConstants}.
 * 
 * @since 0.1.7
 */
public final class IntentDetectionConstants {
    /**
     * USER_PROMPT.
     * 
     * @since 0.1.7
     */
    public static final String USER_PROMPT = "user_prompt";

    /**
     * CATEGORY_LIST.
     * 
     * @since 0.1.7
     */
    public static final String CATEGORY_LIST = "category_list";

    /**
     * DEFAULT_CLASS.
     * 
     * @since 0.1.7
     */
    public static final String DEFAULT_CLASS = "default_class";

    /**
     * ENABLE_HISTORY.
     * 
     * @since 0.1.7
     */
    public static final String ENABLE_HISTORY = "enable_history";

    /**
     * ENABLE_INPUT.
     * 
     * @since 0.1.7
     */
    public static final String ENABLE_INPUT = "enable_input";

    /**
     * EXAMPLE_CONTENT.
     * 
     * @since 0.1.7
     */
    public static final String EXAMPLE_CONTENT = "example_content";

    /**
     * CHAT_HISTORY_MAX_TURN.
     * 
     * @since 0.1.7
     */
    public static final String CHAT_HISTORY_MAX_TURN = "chat_history_max_turn";

    /**
     * CHAT_HISTORY.
     * 
     * @since 0.1.7
     */
    public static final String CHAT_HISTORY = "chat_history";

    /**
     * INPUT.
     * 
     * @since 0.1.7
     */
    public static final String INPUT = "input";

    /**
     * ROLE_MAP.
     * 
     * @since 0.1.7
     */
    public static final Map<String, String> ROLE_MAP = Map.of("user", "用户", "assistant", "助手", "system", "系统");

    /**
     * IntentDetectionConstants.
     * 
     * @since 0.1.7
     */
    private IntentDetectionConstants() {
    }
}
