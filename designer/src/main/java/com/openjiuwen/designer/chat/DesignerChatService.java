/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.chat;

import com.openjiuwen.designer.director.DesignerDirectorChain;
import com.openjiuwen.designer.director.DirectorResult;
import com.openjiuwen.designer.event.DesignerEventSink;
import com.openjiuwen.designer.execution.DesignerNodeException;
import com.openjiuwen.designer.execution.DesignerRunCoordinator;
import com.openjiuwen.designer.schema.DesignerExecutionRun;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerNodeActivity;
import com.openjiuwen.designer.workspace.DesignerWorkspace;
import com.openjiuwen.designer.workspace.DesignerWorkspaceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Designer leader chat: autorun on a blank canvas and report run state.
 *
 * @since 2026-11-06
 */
public final class DesignerChatService {

    private static final Logger LOG = LoggerFactory.getLogger(DesignerChatService.class);

    private static final String DEFAULT_PROJECT_ID = "default";
    private static final String LEADER_SYSTEM = """
            You are the chief editor of the JiuwenSwarm design canvas.
            Reply briefly and warmly in Chinese unless the user clearly writes in another language.
            If the message is a test or small talk, introduce what you can create
            (video, story, poster, storyboard) and ask for the creative brief.
            Never claim that a canvas or media has already been generated.
            """;

    private final DesignerWorkspaceService workspaceService;
    private final DesignerRunCoordinator runCoordinator;
    private final DesignerEventSink eventSink;
    private final DesignerModelFactory modelFactory;

    /**
     * Create the chat service.
     *
     * @param workspaceService workspace service
     * @param runCoordinator run coordinator
     * @param eventSink event sink
     * @param modelFactory text model factory
     */
    public DesignerChatService(DesignerWorkspaceService workspaceService,
            DesignerRunCoordinator runCoordinator, DesignerEventSink eventSink,
            DesignerModelFactory modelFactory) {
        this.workspaceService = workspaceService;
        this.runCoordinator = runCoordinator;
        this.eventSink = eventSink;
        this.modelFactory = modelFactory;
    }

    /**
     * Handle one chat turn.
     *
     * @param request chat request
     * @param observer stream observer
     */
    public void chat(DesignerChatRequest request, DesignerChatObserver observer) {
        if (request.prompt() == null || request.prompt().isBlank()) {
            observer.onFinal("请描述你想创作的视频内容。");
            return;
        }
        try {
            DesignerWorkspace workspace = workspaceService.get(DEFAULT_PROJECT_ID, request.sessionId());
            DesignerGraph graph = workspace.graph();
            LOG.info("Designer chat start: session={}, graph={}, promptChars={}, nodes={}, creative={}",
                    request.sessionId(), graph.graphId(), request.prompt().length(),
                    graph.nodes().size(), looksCreative(request.prompt()));
            if (!graph.nodes().isEmpty()) {
                reportExisting(graph, observer);
                return;
            }
            if (!looksCreative(request.prompt())) {
                replyConversationally(request, observer);
                return;
            }
            activity(observer, request.sessionId(), "stage", "Director · reading the brief");
            DesignerDirectorChain chain = new DesignerDirectorChain(
                    modelFactory.create(request.modelName()));
            Map<String, Object> identity = new LinkedHashMap<>(graph.metadata());
            identity.put("session_id", request.sessionId());
            identity.put("model_name", request.modelName() == null ? "" : request.modelName());
            DirectorResult result = chain.run(request.prompt(), graph.projectId(), identity);
            DesignerGraph authored = workspaceService.saveGraph(result.graph().withMetadata(
                    withPostedRun(result.graph().metadata(), graph)));
            eventSink.onGraphUpdated(request.sessionId(), authored);
            LOG.info("Designer graph saved: session={}, graph={}, nodes={}, edges={}",
                    request.sessionId(), authored.graphId(), authored.nodes().size(),
                    authored.edges().size());
            activity(observer, request.sessionId(), "stage", "Director · canvas ready");
            DesignerRunCoordinator.DesignerRunStartResult started = runCoordinator.start(
                    authored.graphId(), "", false, "leader");
            LOG.info("Designer run started: session={}, graph={}, run={}, status={}",
                    request.sessionId(), authored.graphId(), started.run().runId(),
                    started.run().status());
            observer.onDelta("画布已生成，开始执行。\n");
            observer.onFinal(summary(authored, started.run()));
        } catch (DesignerNodeException ex) {
            LOG.warn("Designer chat failed: session={}, error={}", request.sessionId(),
                    ex.getMessage());
            observer.onFinal("创作失败: " + ex.getMessage());
        }
    }

    private void reportExisting(DesignerGraph graph, DesignerChatObserver observer) {
        if (runCoordinator.isActive(graph.graphId())) {
            observer.onFinal("画布正在执行中，完成后我会汇报结果。");
            return;
        }
        observer.onFinal("画布已有内容。可以指定节点重跑，或修改节点后再次运行。");
    }

    private void replyConversationally(DesignerChatRequest request, DesignerChatObserver observer) {
        try {
            String reply = modelFactory.create(request.modelName())
                    .generate(LEADER_SYSTEM, request.prompt(), 1024);
            observer.onFinal(reply);
        } catch (DesignerNodeException ex) {
            observer.onFinal("收到~我是设计画布的主编，可以帮你把想法变成视频、故事或画面。"
                    + "直接描述你想创作的内容即可，例如“做一个 30 秒的咖啡广告片”。");
        }
    }

    private static boolean looksCreative(String prompt) {
        String text = prompt == null ? "" : prompt.strip();
        if (text.length() > 12) {
            return true;
        }
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        for (String keyword : java.util.List.of("视频", "短片", "电影", "动画", "广告", "故事",
                "画面", "海报", "生成", "创作", "镜头", "分镜", "film", "video", "story",
                "scene", "clip", "image", "poster")) {
            if (lower.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Object> withPostedRun(Map<String, Object> metadata,
            DesignerGraph graph) {
        Map<String, Object> next = new LinkedHashMap<>(metadata);
        Object runId = graph.metadata().get("team_design_run_id");
        if (runId != null) {
            next.put("team_design_run_id", runId);
        }
        return next;
    }

    private static String summary(DesignerGraph graph, DesignerExecutionRun run) {
        long clips = graph.nodes().stream()
                .filter(node -> "clip".equals(node.config().get("pipeline"))).count();
        return "已生成 " + graph.nodes().size() + " 个节点（" + clips + " 个镜头），运行状态: "
                + run.status() + "。";
    }

    private void activity(DesignerChatObserver observer, String sessionId, String kind,
            String text) {
        DesignerNodeActivity activity = new DesignerNodeActivity(kind, text, "director",
                System.currentTimeMillis());
        eventSink.onLeaderActivity(sessionId, activity);
        observer.onLeaderActivity(activity);
    }
}
