/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.rpc;

/**
 * Public designer RPC method names.
 *
 * @since 2026-11-06
 */
public final class DesignerMethods {

    public static final String WORKSPACE_CREATE = "designer.workspace.create";
    public static final String WORKSPACE_GET = "designer.workspace.get";
    public static final String WORKSPACE_SESSION_CREATE = "designer.workspace.session.create";
    public static final String WORKSPACE_SESSION_COMPOSE = "designer.workspace.session.compose";
    public static final String GRAPH_GET = "designer.graph.get";
    public static final String GRAPH_LIST = "designer.graph.list";
    public static final String GRAPH_SAVE = "designer.graph.save";
    public static final String GRAPH_PATCH = "designer.graph.patch";
    public static final String MEDIA_CAPABILITIES = "designer.media.capabilities";
    public static final String RUN_START = "designer.run.start";
    public static final String RUN_GET = "designer.run.get";
    public static final String RUN_PAUSE = "designer.run.pause";
    public static final String RUN_CANCEL = "designer.run.cancel";
    public static final String AGENT_TEMPLATES_LIST = "agent_templates.list";
    public static final String AGENT_GROUPS_SHOW = "agent_groups.show";

    private DesignerMethods() {
    }
}
