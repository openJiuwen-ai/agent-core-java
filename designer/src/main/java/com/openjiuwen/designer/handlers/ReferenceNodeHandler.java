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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Uses an uploaded file directly as the node output.
 *
 * @since 2026-11-06
 */
public final class ReferenceNodeHandler implements NodeHandler {

    @Override
    public NodeOutcome execute(DesignerNode node, DesignerRunContext context) {
        Path file = uploaded(node);
        if (file == null) {
            if (node.outputRef() != null) {
                return new NodeOutcome("completed", null, node.outputRef(),
                        List.of(node.outputRef()), null, List.of());
            }
            throw new DesignerNodeException("user reference file is not available");
        }
        DesignerAssetRef ref = DesignerHandlerSupport.asset(file, node.type(), mimeType(node, file));
        return NodeOutcome.completedMedia("user reference", ref, List.of(ref));
    }

    private Path uploaded(DesignerNode node) {
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

    private static String mimeType(DesignerNode node, Path file) {
        Object upload = node.config().get("upload");
        if (upload instanceof java.util.Map<?, ?> map) {
            Object mime = map.get("mime_type");
            if (mime != null && !String.valueOf(mime).isBlank()) {
                return String.valueOf(mime);
            }
        }
        return "image".equals(node.type()) ? "image/png" : "application/octet-stream";
    }
}
