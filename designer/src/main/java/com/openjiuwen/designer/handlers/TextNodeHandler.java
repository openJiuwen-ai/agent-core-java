/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.handlers;

import com.openjiuwen.designer.execution.DesignerNodeException;
import com.openjiuwen.designer.execution.DesignerRunContext;
import com.openjiuwen.designer.execution.NodeHandler;
import com.openjiuwen.designer.execution.NodeOutcome;
import com.openjiuwen.designer.schema.DesignerAssetRef;
import com.openjiuwen.designer.schema.DesignerNode;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Writes brief or storyboard markdown nodes.
 *
 * @since 2026-11-06
 */
public final class TextNodeHandler implements NodeHandler {

    private static final Logger LOG = LoggerFactory.getLogger(TextNodeHandler.class);

    private final String pipeline;

    /**
     * Create a text handler.
     *
     * @param pipeline brief, storyboard, text, or table
     */
    public TextNodeHandler(String pipeline) {
        this.pipeline = pipeline;
    }

    @Override
    public NodeOutcome execute(DesignerNode node, DesignerRunContext context) {
        String text = approvedText(node, context);
        if (text.isBlank()) {
            throw new DesignerNodeException("node " + node.id() + " has no text input");
        }
        try {
            Files.createDirectories(context.outputDir());
            String stem = "designer_" + pipeline + "_" + DesignerHandlerSupport.safeStem(context.runId())
                    + "_" + DesignerHandlerSupport.safeStem(node.id()) + ".md";
            Path file = context.outputDir().resolve(stem);
            Files.writeString(file, text.endsWith("\n") ? text : text + System.lineSeparator(),
                    StandardCharsets.UTF_8);
            LOG.info("Designer text node written: node={}, pipeline={}, chars={}, path={}",
                    node.id(), pipeline, text.length(), file.getFileName());
            String kind = "storyboard".equals(pipeline) ? "table" : "text";
            DesignerAssetRef ref = DesignerHandlerSupport.asset(file, kind, "text/markdown");
            return new NodeOutcome("completed", text, ref, List.of(ref), null, List.of());
        } catch (IOException ex) {
            throw new DesignerNodeException("failed to write " + pipeline + " node: "
                    + ex.getMessage(), ex);
        }
    }

    private String approvedText(DesignerNode node, DesignerRunContext context) {
        String key = "storyboard".equals(pipeline) ? "approved_storyboard" : "approved_brief";
        Object approved = context.graph().metadata().get(key);
        String text = DesignerHandlerSupport.text(approved);
        if (!text.isBlank()) {
            return text;
        }
        return DesignerHandlerSupport.prompt(node, context.graph());
    }
}
