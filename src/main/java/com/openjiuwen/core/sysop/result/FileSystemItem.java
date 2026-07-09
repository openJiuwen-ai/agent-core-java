/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Base model for file/directory common properties.
 * 
 * @since 0.1.7
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileSystemItem {
    private String name;

    /** Full absolute path of the file/directory. */
    private String path;

    /** Size in bytes. */
    private long size;

    /** Last modification time (ISO format). */
    private String modifiedTime;

    /** Whether the item is a directory. */
    private boolean directory;

    /** File extension (only for files). */
    private String type;
}
