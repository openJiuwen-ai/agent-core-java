/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.handlers;

import com.openjiuwen.designer.execution.NodeHandlerRegistry;
import com.openjiuwen.designer.media.DesignerFfmpeg;
import com.openjiuwen.designer.media.DesignerMediaPort;

/**
 * Builds the built-in designer handler registry.
 *
 * @since 2026-11-06
 */
public final class DesignerHandlerFactory {

    /**
     * Create the standard handler registry.
     *
     * @param media media port
     * @param ffmpeg ffmpeg wrapper
     * @return handler registry
     */
    public NodeHandlerRegistry create(DesignerMediaPort media, DesignerFfmpeg ffmpeg) {
        return new NodeHandlerRegistry()
                .register("brief", new TextNodeHandler("brief"))
                .register("storyboard", new TextNodeHandler("storyboard"))
                .register("text", new TextNodeHandler("text"))
                .register("table", new TextNodeHandler("table"))
                .register("character", new ImageNodeHandler(media, "character"))
                .register("scene", new ImageNodeHandler(media, "scene"))
                .register("image", new ImageNodeHandler(media, "image"))
                .register("clip", new ClipNodeHandler(media))
                .register("video", new ClipNodeHandler(media))
                .register("compose", new ComposeNodeHandler(media))
                .register("music", new AudioNodeHandler(ffmpeg))
                .register("speech", new AudioNodeHandler(ffmpeg))
                .register("audio", new AudioNodeHandler(ffmpeg))
                .register("user_reference", new ReferenceNodeHandler());
    }
}
