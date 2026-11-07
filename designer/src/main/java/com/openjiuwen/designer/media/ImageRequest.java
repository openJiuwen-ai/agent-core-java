/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

import java.nio.file.Path;
import java.util.List;

/**
 * Image generation request.
 *
 * @param prompt text prompt
 * @param aspectRatio requested ratio
 * @param width requested width, or zero to use the ratio
 * @param height requested height, or zero to use the ratio
 * @param outputDir output directory
 * @param fileName desired file name
 * @since 2026-11-06
 */
public record ImageRequest(
        String prompt,
        String aspectRatio,
        int width,
        int height,
        Path outputDir,
        String fileName) {

    public ImageRequest {
        prompt = prompt == null ? "" : prompt;
        aspectRatio = aspectRatio == null ? "1:1" : aspectRatio;
        fileName = fileName == null || fileName.isBlank() ? "image.png" : fileName;
    }

    /**
     * Return reference paths; MiniMax image-01 does not accept them.
     *
     * @return empty list
     */
    public List<Path> referencePaths() {
        return List.of();
    }
}
