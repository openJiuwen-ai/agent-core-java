/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

import java.nio.file.Path;
import java.util.List;

/**
 * Media generation and composition port.
 *
 * @since 2026-11-06
 */
public interface DesignerMediaPort {

    /**
     * Whether image/video generation is configured.
     *
     * @return true when ready
     */
    boolean isConfigured();

    /**
     * Generate one image.
     *
     * @param request image request
     * @return generated file
     */
    Path generateImage(ImageRequest request);

    /**
     * Generate one video.
     *
     * @param request video request
     * @return generated file
     */
    Path generateVideo(VideoRequest request);

    /**
     * Compose clips into one film.
     *
     * @param clips input clips
     * @param output output file
     * @return output file
     */
    Path compose(List<Path> clips, Path output);

    /**
     * Extract the last frame of a clip.
     *
     * @param video input clip
     * @param output output image
     * @return output image, or empty when extraction failed
     */
    java.util.Optional<Path> extractLastFrame(Path video, Path output);
}
