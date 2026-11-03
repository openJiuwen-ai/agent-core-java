/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * File system operation constants.
 *
 * @since 0.1.12
 */
public final class FsConstants {

    /** Default chunk size for read stream operations. */
    public static final int DEFAULT_READ_STREAM_CHUNK_SIZE = 8192;

    /** Default chunk size for upload stream operations. */
    public static final int DEFAULT_UPLOAD_STREAM_CHUNK_SIZE = 8192;

    /** Default chunk size for download stream operations. */
    public static final int DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE = 8192;

    private FsConstants() {
        // Prevent instantiation
    }
}
