/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.handlers;

import com.openjiuwen.designer.execution.DesignerRunContext;
import com.openjiuwen.designer.execution.NodeHandler;
import com.openjiuwen.designer.execution.NodeOutcome;
import com.openjiuwen.designer.media.DesignerMediaPort;
import com.openjiuwen.designer.media.ImageRequest;
import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/**
 * Generates character or scene stills through the media port.
 *
 * @since 2026-11-06
 */
public final class ImageNodeHandler implements NodeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ImageNodeHandler.class);

    private final DesignerMediaPort media;
    private final String pipeline;

    /**
     * Create an image handler.
     *
     * @param media media port
     * @param pipeline character, scene, or image
     */
    public ImageNodeHandler(DesignerMediaPort media, String pipeline) {
        this.media = media;
        this.pipeline = pipeline;
    }

    @Override
    public NodeOutcome execute(DesignerNode node, DesignerRunContext context) {
        context.checkCancelled();
        String prompt = DesignerHandlerSupport.prompt(node, context.graph());
        int[] size = DesignerHandlerSupport.imageSize(context.graph());
        String fileName = "designer_" + pipeline + "_" + DesignerHandlerSupport.safeStem(context.runId())
                + "_" + DesignerHandlerSupport.safeStem(node.id()) + ".png";
        LOG.info("Designer image node start: node={}, pipeline={}, promptChars={}, size={}x{}, ratio={}",
                node.id(), pipeline, prompt.length(), size[0], size[1],
                DesignerHandlerSupport.imageRatio(context.graph()));
        Path output = media.generateImage(new ImageRequest(prompt,
                DesignerHandlerSupport.imageRatio(context.graph()), size[0], size[1],
                context.outputDir(), fileName));
        LOG.info("Designer image node done: node={}, path={}", node.id(), output);
        DesignerAssetRef ref = DesignerHandlerSupport.asset(output, "image", "image/png");
        return NodeOutcome.completedMedia(pipeline + " image generated", ref, List.of(ref));
    }
}
