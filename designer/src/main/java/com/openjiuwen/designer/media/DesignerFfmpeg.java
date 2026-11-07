/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

import com.openjiuwen.designer.execution.DesignerNodeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * ffmpeg process wrapper for designer composition and last-frame extraction.
 *
 * @since 2026-11-06
 */
public final class DesignerFfmpeg {

    private static final Logger LOG = LoggerFactory.getLogger(DesignerFfmpeg.class);

    private static final long TIMEOUT_SECONDS = 1800L;
    private static final int MAX_OUTPUT_CHARS = 4000;
    private static final String DEFAULT_BINARY = "ffmpeg";

    private final String binary;

    /**
     * Create a wrapper resolving ffmpeg from {@code FFMPEG_BIN}, {@code PATH}, or
     * Python's {@code imageio_ffmpeg} package.
     */
    public DesignerFfmpeg() {
        this(FfmpegBinaryResolver.resolve());
    }

    /**
     * Create a wrapper with an explicit binary.
     *
     * @param binary ffmpeg executable
     */
    public DesignerFfmpeg(String binary) {
        this.binary = binary == null || binary.isBlank() ? DEFAULT_BINARY : binary;
    }

    /**
     * Compose clips into one film.
     *
     * @param clips input clips
     * @param output output file
     * @return output file
     */
    public Path compose(List<Path> clips, Path output) {
        if (clips == null || clips.isEmpty()) {
            throw new DesignerNodeException("compose has no clips");
        }
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            LOG.info("ffmpeg compose start: clips={}, output={}, binary={}", clips.size(),
                    output.getFileName(), binary);
            if (clips.size() == 1) {
                Files.copy(clips.get(0), output, StandardCopyOption.REPLACE_EXISTING);
                LOG.info("ffmpeg compose copied single clip: output={}", output.getFileName());
                return output;
            }
            Path list = writeConcatList(clips);
            try {
                List<String> copy = new ArrayList<>();
                copy.add(binary);
                copy.addAll(List.of("-y", "-f", "concat", "-safe", "0", "-i", list.toString(),
                        "-c", "copy", output.toString()));
                if (!run(copy)) {
                    LOG.info("ffmpeg stream-copy concat failed; retrying with libx264/aac: output={}",
                            output.getFileName());
                    List<String> reencode = new ArrayList<>();
                    reencode.add(binary);
                    reencode.addAll(List.of("-y", "-f", "concat", "-safe", "0", "-i", list.toString(),
                            "-c:v", "libx264", "-c:a", "aac", "-movflags", "+faststart",
                            output.toString()));
                    runOrThrow(reencode);
                }
            } finally {
                Files.deleteIfExists(list);
            }
            LOG.info("ffmpeg compose done: clips={}, output={}, bytes={}", clips.size(),
                    output.getFileName(), Files.size(output));
            return output;
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to compose clips: " + ex.getMessage(), ex);
        }
    }

    /**
     * Extract the last frame of a clip.
     *
     * @param video input clip
     * @param output output image
     * @return output when extraction succeeded
     */
    public Optional<Path> extractLastFrame(Path video, Path output) {
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            List<String> command = List.of(binary, "-y", "-sseof", "-0.1", "-i", video.toString(),
                    "-frames:v", "1", "-update", "1", "-q:v", "2", output.toString());
            boolean extracted = run(command);
            LOG.info("ffmpeg last frame: video={}, output={}, ok={}", video.getFileName(),
                    output.getFileName(), extracted);
            return extracted ? Optional.of(output) : Optional.empty();
        } catch (IOException ex) {
            LOG.warn("ffmpeg last frame skipped: video={}, reason={}", video.getFileName(),
                    ex.getMessage());
            return Optional.empty();
        } catch (DesignerNodeException ex) {
            LOG.warn("ffmpeg last frame skipped: video={}, reason={}", video.getFileName(),
                    ex.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Create a silent audio bed.
     *
     * @param output output file
     * @param seconds duration in seconds
     * @return output file
     */
    public Path silentAudio(Path output, int seconds) {
        try {
            Files.createDirectories(output.toAbsolutePath().getParent());
            int duration = Math.max(4, Math.min(90, seconds));
            runOrThrow(List.of(binary, "-y", "-f", "lavfi", "-i", "anullsrc=r=44100:cl=stereo",
                    "-t", String.valueOf(duration), "-c:a", "aac", output.toString()));
            LOG.info("ffmpeg silent audio created: output={}, seconds={}", output.getFileName(),
                    duration);
            return output;
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to create silent audio: " + ex.getMessage(), ex);
        }
    }

    private Path writeConcatList(List<Path> clips) throws IOException {
        Path list = Files.createTempFile("designer-concat-", ".txt");
        List<String> lines = new ArrayList<>();
        for (Path clip : clips) {
            String path = clip.toAbsolutePath().normalize().toString().replace("'", "'\\''");
            lines.add("file '" + path + "'");
        }
        Files.write(list, lines, StandardCharsets.UTF_8);
        return list;
    }

    private void runOrThrow(List<String> command) {
        if (!run(command)) {
            throw new DesignerNodeException("ffmpeg failed: " + String.join(" ", command));
        }
    }

    private boolean run(List<String> command) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
            byte[] output = process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            if (process.exitValue() != 0) {
                String text = new String(output, StandardCharsets.UTF_8);
                throw new DesignerNodeException("ffmpeg exited with " + process.exitValue() + ": "
                        + text.substring(0, Math.min(text.length(), MAX_OUTPUT_CHARS)));
            }
            return true;
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to start ffmpeg (set FFMPEG_BIN, or install "
                    + "ffmpeg/imageio-ffmpeg): " + ex.getMessage(), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DesignerNodeException("ffmpeg interrupted", ex);
        }
    }
}
