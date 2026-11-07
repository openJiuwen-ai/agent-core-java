/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.handlers;

import com.openjiuwen.designer.execution.DesignerNodeException;
import com.openjiuwen.designer.execution.DesignerRunContext;
import com.openjiuwen.designer.execution.NodeHandler;
import com.openjiuwen.designer.execution.NodeOutcome;
import com.openjiuwen.designer.media.DesignerMediaPort;
import com.openjiuwen.designer.media.VideoRequest;
import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates one clip and extracts its last frame.
 *
 * @since 2026-11-06
 */
public final class ClipNodeHandler implements NodeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ClipNodeHandler.class);

    private static final int MAX_REFERENCES = 9;

    private final DesignerMediaPort media;

    /**
     * Create a clip handler.
     *
     * @param media media port
     */
    public ClipNodeHandler(DesignerMediaPort media) {
        this.media = media;
    }

    @Override
    public NodeOutcome execute(DesignerNode node, DesignerRunContext context) {
        context.checkCancelled();
        String prompt = DesignerHandlerSupport.prompt(node, context.graph());
        List<Path> references = referenceImages(node, context);
        Path firstFrame = firstFrame(node);
        String fileName = "designer_clip_" + DesignerHandlerSupport.safeStem(context.runId())
                + "_" + DesignerHandlerSupport.safeStem(node.id()) + ".mp4";
        LOG.info("Designer clip node start: node={}, index={}, promptChars={}, durationSec={}, "
                        + "references={}, firstFrame={}", node.id(),
                node.config().get("clip_index"), prompt.length(),
                DesignerHandlerSupport.duration(node), references.size(), firstFrame != null);
        Path output = media.generateVideo(new VideoRequest(prompt,
                DesignerHandlerSupport.videoRatio(context.graph()),
                DesignerHandlerSupport.videoResolution(context.graph()),
                DesignerHandlerSupport.duration(node), firstFrame, references,
                context.outputDir(), fileName));
        context.checkCancelled();
        DesignerAssetRef video = DesignerHandlerSupport.asset(output, "video", "video/mp4");
        LOG.info("Designer clip node done: node={}, path={}", node.id(), output);
        List<DesignerAssetRef> refs = new ArrayList<>();
        refs.add(video);
        String frameName = DesignerHandlerSupport.safeStem(node.id()) + "_lastframe.jpg";
        media.extractLastFrame(output, context.outputDir().resolve(frameName))
                .ifPresent(frame -> {
                    refs.add(DesignerHandlerSupport.asset(frame, "image", "image/jpeg"));
                    LOG.info("Designer clip last frame extracted: node={}, path={}", node.id(), frame);
                });
        return NodeOutcome.completedMedia("clip generated", video, refs);
    }

    private List<Path> referenceImages(DesignerNode node, DesignerRunContext context) {
        List<Path> references = new ArrayList<>();
        for (var edge : context.graph().edges()) {
            if (!edge.target().equals(node.id())) {
                continue;
            }
            context.outcome(edge.source()).ifPresent(outcome -> {
                if (outcome.isCompleted() && outcome.outputRef() != null
                        && "image".equals(outcome.outputRef().kind())) {
                    Path path = DesignerHandlerSupport.path(outcome.outputRef());
                    if (Files.isRegularFile(path) && references.size() < MAX_REFERENCES) {
                        references.add(path);
                    }
                }
            });
        }
        return references;
    }

    private Path firstFrame(DesignerNode node) {
        String path = DesignerHandlerSupport.text(node.config().get("first_frame"));
        if (path.isBlank()) {
            return null;
        }
        Path firstFrame = Path.of(path);
        if (!Files.isRegularFile(firstFrame)) {
            throw new DesignerNodeException("first_frame does not exist: " + firstFrame.getFileName());
        }
        return firstFrame;
    }
}
