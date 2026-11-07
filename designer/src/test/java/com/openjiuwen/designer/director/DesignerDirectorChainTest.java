/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.designer.director;

import com.openjiuwen.designer.schema.DesignerGraph;
import com.openjiuwen.designer.schema.DesignerSchema;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Director chain tests with a deterministic model.
 *
 * @since 2026-11-06
 */
class DesignerDirectorChainTest {

    private static final String ANALYSIS = """
            {"story_name":"Ocean","style_lock":{"look":"painterly","medium":"2D"},
            "characters":[{"id":"char_1","name":"Gull","description":"a white gull"}],
            "clips":[{"clip_index":1,"title":"dawn","action":"gull flies","camera":"wide",
            "on_screen":["char_1"],"setting_id":"set_1","clip_prompt":"gull over sea",
            "timeline":"0-4s"}],
            "scenes":[{"setting_id":"set_1","name":"Sea","description":"dawn sea"}]}
            """;

    private static final String BRIEF = """
            # Brief

            A short painterly film about a gull crossing a dawn sea. The camera stays wide.
            Identity is fixed: a white gull with a red beak. The sea remains calm then brightens.
            """.repeat(2);

    private static final String SHOTS = """
            {"shots":[{"clip_index":1,"title":"dawn","action":"gull flies","camera":"wide",
            "on_screen":["char_1"],"setting_id":"set_1","clip_prompt":"gull over dawn sea",
            "timeline":"0-4s"}],"language_lock":"none","bgm_lock":"ambient"}
            """;

    @Test
    void authorsGraphFromPrompt() {
        DesignerTextModelPort model = (system, user, maxTokens) -> {
            if (system.contains("Return JSON only") && system.contains("characters")) {
                return ANALYSIS;
            }
            if (system.contains("creative brief")) {
                return BRIEF;
            }
            if (system.contains("Review the brief")) {
                return "{\"ok\":true,\"patched_brief_markdown\":\"\",\"notes\":\"ok\"}";
            }
            if (system.contains("shot list")) {
                return SHOTS;
            }
            if (system.contains("Review the shot list")) {
                return SHOTS;
            }
            return "{}";
        };

        DirectorResult result = new DesignerDirectorChain(model).run("ocean film", "default",
                Map.of("session_id", "design_1", "model_name", "test"));

        DesignerGraph graph = result.graph();
        assertThat(graph.schemaVersion()).isEqualTo(DesignerSchema.GRAPH_SCHEMA_VERSION);
        assertThat(graph.source()).isEqualTo("prompt");
        assertThat(graph.nodes()).extracting("id")
                .contains("n_brief", "n_storyboard", "n_character_1", "n_scene_1",
                        "n_clip_1", "n_compose");
        assertThat(graph.metadata()).containsKeys("script_analysis", "approved_brief",
                "approved_storyboard", "style_lock");
        assertThat(result.storyboard()).contains("| # |");
    }
}
