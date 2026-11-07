/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

import com.openjiuwen.designer.schema.DesignerEdge;
import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerGraphNormalizer;
import com.openjiuwen.designer.schema.DesignerLayout;
import com.openjiuwen.designer.schema.DesignerNode;
import com.openjiuwen.designer.schema.DesignerSchema;
import com.openjiuwen.designer.workspace.DesignerGraphFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the deterministic brief/storyboard/character/scene/clip/compose DAG.
 *
 * @since 2026-11-06
 */
public final class SmartGraphBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(SmartGraphBuilder.class);

    private static final double COLUMN_BRIEF = 0.0d;
    private static final double COLUMN_STORYBOARD = 320.0d;
    private static final double COLUMN_CHARACTER = 640.0d;
    private static final double COLUMN_SCENE = 960.0d;
    private static final double COLUMN_CLIP = 1280.0d;
    private static final double COLUMN_COMPOSE = 1600.0d;

    private final DesignerGraphFactory graphFactory = new DesignerGraphFactory();
    private final DesignerGraphNormalizer normalizer = new DesignerGraphNormalizer();

    /**
     * Build a graph from analysis, brief, and storyboard text.
     *
     * @param projectId project id
     * @param prompt user prompt
     * @param analysis structured analysis
     * @param approvedBrief approved brief markdown
     * @param approvedStoryboard approved storyboard markdown
     * @param baseMetadata identity and media metadata
     * @return normalized graph
     */
    public DesignerGraph build(String projectId, String prompt, ScriptAnalysis analysis,
            String approvedBrief, String approvedStoryboard, Map<String, Object> baseMetadata) {
        ScriptAnalysis safe = ensureMinimum(analysis, prompt);
        List<DesignerNode> nodes = new ArrayList<>();
        List<DesignerEdge> edges = new ArrayList<>();
        nodes.add(brief(prompt));
        nodes.add(storyboard(prompt, safe));
        edges.add(edge("e_brief_storyboard", "n_brief", "n_storyboard"));
        Map<String, String> characterNodes = addCharacters(nodes, edges, safe);
        Map<String, String> sceneNodes = addScenes(nodes, edges, safe);
        List<String> clipIds = addClips(nodes, edges, safe, characterNodes, sceneNodes);
        addCompose(nodes, edges, safe, clipIds);
        Map<String, Object> metadata = new LinkedHashMap<>(baseMetadata);
        metadata.put("script_analysis", safe);
        metadata.put("approved_brief", approvedBrief == null ? "" : approvedBrief);
        metadata.put("approved_storyboard", approvedStoryboard == null ? "" : approvedStoryboard);
        metadata.put("style_lock", safe.styleLock());
        metadata.put("target_clip_count", safe.clips().size());
        metadata.put("director_composed_on_bootstrap", true);
        metadata.put("freeze_clip_topology", true);
        long now = System.currentTimeMillis();
        DesignerGraph graph = new DesignerGraph(DesignerSchema.GRAPH_SCHEMA_VERSION,
                graphFactory.newGraphId(), projectId, "Brief: " + safe.storyName(), prompt,
                "prompt", nodes, edges, metadata, now, now);
        DesignerGraph normalized = normalizer.normalize(graph);
        LOG.info("Smart graph built: graph={}, project={}, characters={}, scenes={}, clips={}, "
                        + "nodes={}, edges={}", normalized.graphId(), projectId,
                safe.characters().size(), safe.scenes().size(), safe.clips().size(),
                normalized.nodes().size(), normalized.edges().size());
        return normalized;
    }

    private DesignerNode brief(String prompt) {
        Map<String, Object> config = baseConfig("brief", prompt, List.of());
        return new DesignerNode("n_brief", "text", "Brief",
                config, layout(COLUMN_BRIEF, 0.0d), null);
    }

    private DesignerNode storyboard(String prompt, ScriptAnalysis analysis) {
        Map<String, Object> config = baseConfig("storyboard", prompt, List.of("n_brief"));
        config.put("planned_shots", analysis.clips().size());
        return new DesignerNode("n_storyboard", "table", "StoryBoard",
                config, layout(COLUMN_STORYBOARD, 0.0d), null);
    }

    private Map<String, String> addCharacters(List<DesignerNode> nodes, List<DesignerEdge> edges,
            ScriptAnalysis analysis) {
        Map<String, String> nodeByCharacter = new LinkedHashMap<>();
        int index = 0;
        for (CharacterSpec character : analysis.characters()) {
            index++;
            String nodeId = index == 1 ? "n_character_1" : "n_character_" + index;
            String prompt = character.name() + ": " + character.description();
            Map<String, Object> config = baseConfig("character", prompt, List.of("n_brief"));
            config.put("character_id", character.id());
            config.put("character_name", character.name());
            nodes.add(new DesignerNode(nodeId, "image", "Character " + index + ": "
                    + character.name(), config, layout(COLUMN_CHARACTER, (index - 1) * 200.0d), null));
            edges.add(edge("e_brief_" + nodeId, "n_brief", nodeId));
            nodeByCharacter.put(character.id(), nodeId);
            nodeByCharacter.putIfAbsent("char_" + index, nodeId);
        }
        return nodeByCharacter;
    }

    private Map<String, String> addScenes(List<DesignerNode> nodes, List<DesignerEdge> edges,
            ScriptAnalysis analysis) {
        Map<String, String> nodeBySetting = new LinkedHashMap<>();
        int index = 0;
        for (SceneSpec scene : analysis.scenes()) {
            index++;
            String nodeId = index == 1 ? "n_scene_1" : "n_scene_" + index;
            String prompt = scene.name() + ": " + scene.description();
            Map<String, Object> config = baseConfig("scene", prompt, List.of("n_brief"));
            config.put("setting_id", scene.settingId());
            nodes.add(new DesignerNode(nodeId, "image", "Scene " + index + ": " + scene.name(),
                    config, layout(COLUMN_SCENE, (index - 1) * 200.0d), null));
            edges.add(edge("e_brief_" + nodeId, "n_brief", nodeId));
            nodeBySetting.put(scene.settingId(), nodeId);
        }
        return nodeBySetting;
    }

    private List<String> addClips(List<DesignerNode> nodes, List<DesignerEdge> edges,
            ScriptAnalysis analysis, Map<String, String> characterNodes,
            Map<String, String> sceneNodes) {
        List<String> clipIds = new ArrayList<>();
        int index = 0;
        for (ClipSpec clip : analysis.clips()) {
            index++;
            String nodeId = "n_clip_" + index;
            clipIds.add(nodeId);
            List<String> inputs = new ArrayList<>();
            inputs.add("n_storyboard");
            for (String characterId : clip.onScreen()) {
                String characterNode = characterNodes.get(characterId);
                if (characterNode != null) {
                    inputs.add(characterNode);
                    edges.add(edge("e_" + characterNode + "_" + nodeId, characterNode, nodeId));
                }
            }
            String sceneNode = sceneNodes.getOrDefault(clip.settingId(),
                    sceneNodes.values().stream().findFirst().orElse(null));
            if (sceneNode != null) {
                inputs.add(sceneNode);
                edges.add(edge("e_" + sceneNode + "_" + nodeId, sceneNode, nodeId));
            }
            Map<String, Object> config = baseConfig("clip",
                    clip.clipPrompt().isBlank() ? clip.action() : clip.clipPrompt(), inputs);
            config.put("clip_index", index);
            config.put("clip_action", clip.action());
            config.put("camera", clip.camera());
            config.put("timeline", clip.timeline());
            config.put("setting_id", clip.settingId());
            config.put("scene_node_id", sceneNode == null ? "" : sceneNode);
            config.put("duration_sec", 5);
            nodes.add(new DesignerNode(nodeId, "video", "Clip " + index + ": " + clip.title(),
                    config, layout(COLUMN_CLIP, (index - 1) * 200.0d), null));
            edges.add(edge("e_storyboard_" + nodeId, "n_storyboard", nodeId));
        }
        return clipIds;
    }

    private void addCompose(List<DesignerNode> nodes, List<DesignerEdge> edges,
            ScriptAnalysis analysis, List<String> clipIds) {
        Map<String, Object> config = baseConfig("compose", "Compose " + analysis.storyName(), clipIds);
        config.put("delegate", "handler");
        nodes.add(new DesignerNode("n_compose", "video", "Final Composed: " + analysis.storyName(),
                config, layout(COLUMN_COMPOSE, 0.0d), null));
        for (String clipId : clipIds) {
            edges.add(edge("e_" + clipId + "_n_compose", clipId, "n_compose"));
        }
    }

    private static Map<String, Object> baseConfig(String pipeline, String prompt, List<String> inputs) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("role", role(pipeline));
        config.put("pipeline", pipeline);
        config.put("prompt", prompt == null ? "" : prompt);
        config.put("inputs", List.copyOf(inputs));
        config.put("delegate", "handler");
        config.put("agent_template", pipeline);
        config.put("interaction_mode", "generate");
        config.put("materials", List.of());
        return config;
    }

    private static String role(String pipeline) {
        return switch (pipeline) {
            case "brief" -> "text";
            case "storyboard" -> "table";
            case "character", "scene", "image" -> "image";
            case "music", "speech", "audio" -> "audio";
            default -> "video";
        };
    }

    private static DesignerLayout layout(double x, double y) {
        return new DesignerLayout(x, y, 280.0d, 160.0d);
    }

    private static DesignerEdge edge(String id, String source, String target) {
        return new DesignerEdge(id, source, target, "data", null);
    }

    private static ScriptAnalysis ensureMinimum(ScriptAnalysis analysis, String prompt) {
        if (analysis == null) {
            return new ScriptAnalysis("Untitled", Map.of(),
                    List.of(new CharacterSpec("char_1", "主角", prompt)),
                    List.of(new ClipSpec(1, "开场", prompt, "中景", List.of("char_1"),
                            "set_1", prompt, "0-5s")),
                    List.of(new SceneSpec("set_1", "场景 1", prompt)));
        }
        List<CharacterSpec> characters = analysis.characters().isEmpty()
                ? List.of(new CharacterSpec("char_1", "主角", prompt))
                : analysis.characters();
        List<SceneSpec> scenes = analysis.scenes().isEmpty()
                ? List.of(new SceneSpec("set_1", "场景 1", prompt))
                : analysis.scenes();
        List<ClipSpec> clips = analysis.clips().isEmpty()
                ? List.of(new ClipSpec(1, "开场", prompt, "中景",
                        List.of(characters.get(0).id()), scenes.get(0).settingId(), prompt, "0-5s"))
                : analysis.clips();
        return new ScriptAnalysis(analysis.storyName(), analysis.styleLock(), characters, clips, scenes);
    }
}
