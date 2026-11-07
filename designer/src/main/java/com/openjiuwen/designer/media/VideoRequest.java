/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

import java.nio.file.Path;
import java.util.List;

/**
 * Video generation request.
 *
 * @param prompt text prompt
 * @param aspectRatio requested ratio
 * @param resolution requested resolution, 768P or 2K
 * @param durationSec duration in seconds
 * @param firstFramePath optional first frame
 * @param referencePaths reference stills
 * @param outputDir output directory
 * @param fileName desired file name
 * @since 2026-11-06
 */
public record VideoRequest(
        String prompt,
        String aspectRatio,
        String resolution,
        int durationSec,
        Path firstFramePath,
        List<Path> referencePaths,
        Path outputDir,
        String fileName) {

    public VideoRequest {
        prompt = prompt == null ? "" : prompt;
        aspectRatio = aspectRatio == null ? "16:9" : aspectRatio;
        resolution = resolution == null ? "768P" : resolution;
        referencePaths = referencePaths == null ? List.of() : List.copyOf(referencePaths);
        fileName = fileName == null || fileName.isBlank() ? "video.mp4" : fileName;
    }
}
