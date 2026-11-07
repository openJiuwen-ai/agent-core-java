/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.rpc;

import java.util.Map;

/**
 * Transport-neutral designer RPC result.
 *
 * @param ok whether the call succeeded
 * @param payload success payload, when successful
 * @param error failure message, when unsuccessful
 * @param code stable failure code, when unsuccessful
 * @since 2026-11-06
 */
public record DesignerRpcResult(
        boolean ok,
        Map<String, Object> payload,
        String error,
        String code) {

    /**
     * Create a success result.
     *
     * @param payload response payload
     * @return success result
     */
    public static DesignerRpcResult ok(Map<String, Object> payload) {
        return new DesignerRpcResult(true, payload == null ? Map.of() : payload, null, null);
    }

    /**
     * Create a failure result.
     *
     * @param code stable error code
     * @param message human-readable reason
     * @return failure result
     */
    public static DesignerRpcResult error(DesignerErrorCode code, String message) {
        return new DesignerRpcResult(false, Map.of(), message, code.wireValue());
    }
}
