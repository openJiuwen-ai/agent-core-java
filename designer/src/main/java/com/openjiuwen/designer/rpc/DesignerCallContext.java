/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.rpc;

/**
 * Caller context for one designer RPC.
 *
 * @param sessionId caller session id, when known
 * @param userId caller user id, when known
 * @param channelId caller channel id, when known
 * @since 2026-11-06
 */
public record DesignerCallContext(
        String sessionId,
        String userId,
        String channelId) {

    /** Empty context for tests and standalone calls. */
    public static final DesignerCallContext EMPTY = new DesignerCallContext("", "", "");
}
