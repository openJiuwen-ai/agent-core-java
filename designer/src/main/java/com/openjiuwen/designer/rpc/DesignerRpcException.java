/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.rpc;

/**
 * Designer RPC failure with a stable wire code.
 *
 * @since 2026-11-06
 */
public class DesignerRpcException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final DesignerErrorCode code;

    /**
     * Create a designer RPC failure.
     *
     * @param code stable error code
     * @param message human-readable reason
     */
    public DesignerRpcException(DesignerErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * Return the stable error code.
     *
     * @return error code
     */
    public DesignerErrorCode code() {
        return code;
    }
}
