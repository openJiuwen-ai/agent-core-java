/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

import com.openjiuwen.designer.execution.DesignerNodeException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the ffmpeg wrapper used by the designer media pipeline.
 *
 * @since 2026-11-07
 */
class DesignerFfmpegTest {

    @TempDir
    private Path tempDir;

    @Test
    void lastFrameExtractionIsBestEffortWhenFfmpegIsMissing() throws IOException {
        Path video = Files.write(tempDir.resolve("clip.mp4"), new byte[] {0, 1, 2});
        DesignerFfmpeg ffmpeg = new DesignerFfmpeg(
                tempDir.resolve("missing-ffmpeg").toString());

        assertThat(ffmpeg.extractLastFrame(video, tempDir.resolve("frame.jpg"))).isEmpty();
    }

    @Test
    void composeCopiesSingleClipWithoutFfmpeg() throws IOException {
        Path video = Files.write(tempDir.resolve("clip.mp4"), new byte[] {1, 2, 3});
        DesignerFfmpeg ffmpeg = new DesignerFfmpeg(
                tempDir.resolve("missing-ffmpeg").toString());

        Path output = ffmpeg.compose(List.of(video), tempDir.resolve("final.mp4"));

        assertThat(output).hasBinaryContent(new byte[] {1, 2, 3});
    }

    @Test
    void composeReportsMissingFfmpegForMultipleClips() throws IOException {
        Path first = Files.write(tempDir.resolve("one.mp4"), new byte[] {1});
        Path second = Files.write(tempDir.resolve("two.mp4"), new byte[] {2});
        DesignerFfmpeg ffmpeg = new DesignerFfmpeg(
                tempDir.resolve("missing-ffmpeg").toString());

        assertThatThrownBy(() -> ffmpeg.compose(List.of(first, second), tempDir.resolve("final.mp4")))
                .isInstanceOf(DesignerNodeException.class)
                .hasMessageContaining("FFMPEG_BIN");
    }
}
