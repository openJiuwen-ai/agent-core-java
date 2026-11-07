/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.media;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Resolves the ffmpeg executable the same way the Python runtime does.
 *
 * <p>Order: {@code FFMPEG_BIN}, {@code ffmpeg} on {@code PATH}, then the binary
 * bundled with Python's {@code imageio_ffmpeg} package, then {@code ffmpeg} as a
 * last-resort name so the failure message stays actionable.</p>
 *
 * @since 2026-11-07
 */
final class FfmpegBinaryResolver {

    private static final Logger LOG = LoggerFactory.getLogger(FfmpegBinaryResolver.class);

    private static final String ENV_FFMPEG_BIN = "FFMPEG_BIN";
    private static final String ENV_PYTHON = "JIUWENSWARM_PYTHON";
    private static final String FFMPEG = "ffmpeg";
    private static final long PROBE_TIMEOUT_SECONDS = 10L;
    private static final long PYTHON_TIMEOUT_SECONDS = 20L;
    private static final int MAX_PROBE_OUTPUT = 4096;

    private FfmpegBinaryResolver() {
    }

    /**
     * Resolve the ffmpeg binary.
     *
     * @return executable path or name
     */
    static String resolve() {
        String configured = env(ENV_FFMPEG_BIN);
        if (!configured.isBlank()) {
            LOG.info("ffmpeg binary from FFMPEG_BIN: binary={}", configured);
            return configured;
        }
        if (probe(FFMPEG)) {
            LOG.info("ffmpeg binary resolved from PATH: binary={}", FFMPEG);
            return FFMPEG;
        }
        String bundled = pythonFfmpeg();
        if (bundled != null) {
            LOG.info("ffmpeg binary resolved from imageio_ffmpeg: binary={}", bundled);
            return bundled;
        }
        LOG.warn("ffmpeg binary not found on PATH or in imageio_ffmpeg; "
                + "video composition will fail until ffmpeg is installed");
        return FFMPEG;
    }

    private static String pythonFfmpeg() {
        for (String python : pythonCandidates()) {
            String executable = queryPython(python);
            if (executable != null) {
                return executable;
            }
        }
        return null;
    }

    private static List<String> pythonCandidates() {
        List<String> candidates = new ArrayList<>();
        String configured = env(ENV_PYTHON);
        if (!configured.isBlank()) {
            candidates.add(configured);
        }
        candidates.add("python");
        candidates.add("python3");
        return candidates;
    }

    private static String queryPython(String python) {
        List<String> command = List.of(python, "-c",
                "import imageio_ffmpeg; print(imageio_ffmpeg.get_ffmpeg_exe())");
        Process process;
        try {
            process = new ProcessBuilder(command)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException ex) {
            return null;
        }
        try {
            if (!process.waitFor(PYTHON_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return null;
            }
            if (process.exitValue() != 0) {
                return null;
            }
            byte[] bytes = process.getInputStream().readNBytes(MAX_PROBE_OUTPUT);
            String executable = new String(bytes, StandardCharsets.UTF_8).strip();
            if (executable.isBlank()) {
                return null;
            }
            Path path = Path.of(executable);
            return Files.isRegularFile(path) ? path.toString() : null;
        } catch (IOException ex) {
            return null;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return null;
        } finally {
            process.destroy();
        }
    }

    private static boolean probe(String binary) {
        Process process;
        try {
            process = new ProcessBuilder(binary, "-version")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException ex) {
            return false;
        }
        try {
            if (!process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } finally {
            process.destroy();
        }
    }

    private static String env(String name) {
        String value = System.getenv(name);
        return value == null ? "" : value.strip();
    }
}
