/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.chat;

import com.openjiuwen.designer.schema.DesignerNodeActivity;

/**
 * Observer for one designer chat turn.
 *
 * @since 2026-11-06
 */
public interface DesignerChatObserver {

    /**
     * Publish a text delta.
     *
     * @param text delta text
     */
    default void onDelta(String text) {
    }

    /**
     * Publish the final answer.
     *
     * @param text final text
     */
    default void onFinal(String text) {
    }

    /**
     * Publish a leader activity line.
     *
     * @param activity activity
     */
    default void onLeaderActivity(DesignerNodeActivity activity) {
    }
}
