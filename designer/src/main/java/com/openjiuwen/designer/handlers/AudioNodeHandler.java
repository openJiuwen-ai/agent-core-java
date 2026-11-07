/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.handlers;

import com.openjiuwen.designer.execution.DesignerNodeException;
import com.openjiuwen.designer.execution.DesignerRunContext;
import com.openjiuwen.designer.execution.NodeHandler;
import com.openjiuwen.designer.execution.NodeOutcome;
import com.openjiuwen.designer.media.DesignerFfmpeg;
import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Provides uploaded audio or a silent placeholder bed.
 *
 * @since 2026-11-06
 */
public final class AudioNodeHandler implements NodeHandler {

    private final DesignerFfmpeg ffmpeg;

    /**
     * Create an audio handler.
     *
     * @param ffmpeg ffmpeg wrapper
     */
    public AudioNodeHandler(DesignerFfmpeg ffmpeg) {
        this.ffmpeg = ffmpeg;
    }

    @Override
    public NodeOutcome execute(DesignerNode node, DesignerRunContext context) {
        Path uploaded = uploadedAudio(node);
        if (uploaded != null) {
            DesignerAssetRef ref = DesignerHandlerSupport.asset(uploaded, "audio", mimeType(uploaded));
            return NodeOutcome.completedMedia("audio reference", ref, List.of(ref));
        }
        String fileName = "designer_audio_" + DesignerHandlerSupport.safeStem(context.runId())
                + "_" + DesignerHandlerSupport.safeStem(node.id()) + ".m4a";
        Path output = ffmpeg.silentAudio(context.outputDir().resolve(fileName), 18);
        DesignerAssetRef ref = DesignerHandlerSupport.asset(output, "audio", "audio/mp4");
        return NodeOutcome.completedMedia("silent audio bed", ref, List.of(ref));
    }

    private Path uploadedAudio(DesignerNode node) {
        Object upload = node.config().get("upload");
        if (upload instanceof java.util.Map<?, ?> map) {
            Object uri = map.get("uri");
            if (uri != null) {
                Path path = Path.of(String.valueOf(uri));
                if (Files.isRegularFile(path)) {
                    return path;
                }
            }
        }
        String userReference = DesignerHandlerSupport.text(node.config().get("user_reference_path"));
        if (!userReference.isBlank()) {
            Path path = Path.of(userReference);
            if (Files.isRegularFile(path)) {
                return path;
            }
        }
        return null;
    }

    private static String mimeType(Path path) {
        String name = path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".wav")) {
            return "audio/wav";
        }
        if (name.endsWith(".m4a") || name.endsWith(".aac")) {
            return "audio/mp4";
        }
        if (name.endsWith(".ogg")) {
            return "audio/ogg";
        }
        if (name.endsWith(".flac")) {
            return "audio/flac";
        }
        return "audio/mpeg";
    }
}
