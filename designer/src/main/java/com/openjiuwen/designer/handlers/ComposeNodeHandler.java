/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.handlers;

import com.openjiuwen.designer.execution.DesignerNodeException;
import com.openjiuwen.designer.execution.DesignerRunContext;
import com.openjiuwen.designer.execution.NodeHandler;
import com.openjiuwen.designer.execution.NodeOutcome;
import com.openjiuwen.designer.media.DesignerMediaPort;
import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Concatenates completed clips into the final film.
 *
 * @since 2026-11-06
 */
public final class ComposeNodeHandler implements NodeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ComposeNodeHandler.class);

    private static final long MIN_VIDEO_BYTES = 512L;

    private final DesignerMediaPort media;

    /**
     * Create a compose handler.
     *
     * @param media media port
     */
    public ComposeNodeHandler(DesignerMediaPort media) {
        this.media = media;
    }

    @Override
    public NodeOutcome execute(DesignerNode node, DesignerRunContext context) {
        context.checkCancelled();
        List<Path> clips = completedClips(node, context);
        if (clips.isEmpty()) {
            throw new DesignerNodeException("compose blocked: no completed clips");
        }
        String fileName = "designer_compose_" + DesignerHandlerSupport.safeStem(context.runId()) + ".mp4";
        LOG.info("Designer compose node start: node={}, clips={}", node.id(), clips.size());
        Path output = media.compose(clips, context.outputDir().resolve(fileName));
        validate(output);
        LOG.info("Designer compose node done: node={}, path={}", node.id(), output);
        DesignerAssetRef ref = DesignerHandlerSupport.asset(output, "video", "video/mp4");
        return NodeOutcome.completedMedia("composed film", ref, List.of(ref));
    }

    private List<Path> completedClips(DesignerNode node, DesignerRunContext context) {
        List<Path> clips = new ArrayList<>();
        for (var edge : context.graph().edges()) {
            if (!edge.target().equals(node.id())) {
                continue;
            }
            NodeOutcome outcome = context.outcome(edge.source()).orElse(null);
            if (outcome == null || !outcome.isCompleted() || outcome.outputRef() == null
                    || !"video".equals(outcome.outputRef().kind())) {
                throw new DesignerNodeException("compose blocked: clip " + edge.source()
                        + " is not completed");
            }
            Path path = DesignerHandlerSupport.path(outcome.outputRef());
            if (!Files.isRegularFile(path)) {
                throw new DesignerNodeException("compose blocked: clip file is missing");
            }
            clips.add(path);
        }
        return clips;
    }

    private static void validate(Path output) {
        try {
            if (!Files.isRegularFile(output) || Files.size(output) < MIN_VIDEO_BYTES) {
                throw new DesignerNodeException("compose produced an empty or tiny video");
            }
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to inspect composed video: " + ex.getMessage(), ex);
        }
    }
}
