/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.chat;

/**
 * One designer chat request.
 *
 * @param sessionId design session id
 * @param prompt user message
 * @param modelName selected model
 * @param userId caller user
 * @param channelId caller channel
 * @since 2026-11-06
 */
public record DesignerChatRequest(
        String sessionId,
        String prompt,
        String modelName,
        String userId,
        String channelId) {
}
