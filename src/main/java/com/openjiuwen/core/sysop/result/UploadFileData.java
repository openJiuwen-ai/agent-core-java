/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */
package com.openjiuwen.core.sysop.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data structure for upload file operation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UploadFileData {
    private String localPath;
    private String targetPath;
    private long size;
}
