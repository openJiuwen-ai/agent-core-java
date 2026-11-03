/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.context.offload.api;

/**
 * Runtime exception carrying a structured {@link OffloadErrorCode}.
 *
 * <p>Thrown by {@link OffloadStore} and {@link OffloadService}; the ReloaderTool catches
 * it and formats {@code <CODE>: handle=<id>} text so a reload failure never breaks the
 * agent turn (架构文档 §3.1 / §4.2).</p>
 *
 * @since 2026-01-01
 */
public class OffloadException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private final OffloadErrorCode code;

    public OffloadException(OffloadErrorCode code, String message) {
        super(message);
        this.code = code == null ? OffloadErrorCode.OFFLOAD_STORE_IO : code;
    }

    public OffloadException(OffloadErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.code = code == null ? OffloadErrorCode.OFFLOAD_STORE_IO : code;
    }

    public OffloadErrorCode code() {
        return code;
    }
}
