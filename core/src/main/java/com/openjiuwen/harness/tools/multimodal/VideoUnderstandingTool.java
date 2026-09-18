/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools.multimodal;

import com.openjiuwen.harness.tools.AbstractHarnessTool;
import com.openjiuwen.harness.tools.ToolOutput;

import java.util.Map;

/**
 * Video understanding tool facade.
 *
 * <p>Mirrors Python's {@code VideoUnderstandingTool} in
 * {@code openjiuwen/harness/tools/multimodal/video_understanding.py}.</p>
 */
public class VideoUnderstandingTool extends AbstractHarnessTool {

    private final VideoInvoker invoker;

    public VideoUnderstandingTool(VideoInvoker invoker) {
        super(toolCard("video_understanding", "VideoUnderstandingTool", "Answer a question about video content."));
        this.invoker = invoker;
    }

    @Override
    protected Object invokeInternal(Map<String, Object> inputs, Map<String, Object> kwargs) throws Exception {
        String videoPath = requiredString(inputs, "video_path");
        String question = stringValue(inputs == null ? null : inputs.getOrDefault("question", ""));
        if (invoker == null) {
            return ToolOutput.failure("video model config is not configured");
        }
        return ToolOutput.success(invoker.answer(videoPath, question, inputs == null ? Map.of() : inputs));
    }

    public static String normalizeVideoUrl(String videoPath) {
        return videoPath == null ? "" : videoPath.trim();
    }

    @FunctionalInterface
    public interface VideoInvoker {
        Map<String, Object> answer(String videoPath, String question, Map<String, Object> inputs) throws Exception;
    }
}
