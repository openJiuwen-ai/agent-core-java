/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop;

/**
 * Constants for file system operations — chunk sizes and limits.
 * <p>
 * Mirrors the module-level constants in Python's {@code sys_operation/fs.py}.
 * 
 * @since 0.1.7
 */
public final class FsConstants {
    /**
     * FsConstants.
     * 
     * @since 0.1.7
     */
    private FsConstants() {
    }

    /**
     * DEFAULT_READ_CHUNK_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_READ_CHUNK_SIZE = 0;

    /**
     * DEFAULT_UPLOAD_CHUNK_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_UPLOAD_CHUNK_SIZE = 0;

    /**
     * DEFAULT_DOWNLOAD_CHUNK_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_DOWNLOAD_CHUNK_SIZE = 0;

    /**
     * DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_DOWNLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;

    /**
     * DEFAULT_UPLOAD_STREAM_CHUNK_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_UPLOAD_STREAM_CHUNK_SIZE = 1024 * 1024;

    /**
     * DEFAULT_READ_STREAM_CHUNK_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int DEFAULT_READ_STREAM_CHUNK_SIZE = 8192;

    /**
     * TAIL_CHUNK_SIZE.
     * 
     * @since 0.1.7
     */
    public static final int TAIL_CHUNK_SIZE = 1024;
}
