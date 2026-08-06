
package com.openjiuwen.core.memory.graph.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import com.openjiuwen.core.foundation.store.graph.Entity;
import com.openjiuwen.core.memory.config.graph.AddMemStrategy;
import com.openjiuwen.core.memory.config.graph.EpisodeType;
import com.openjiuwen.core.memory.config.graph.GraphDefaults;
import com.openjiuwen.core.memory.config.graph.SearchConfig;
import com.openjiuwen.core.memory.graph.extraction.prompts.TemplateManager;
import com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction.ExtractionPromptLanguageBase;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class GraphExtractionTest {
    @Test
    void graphConfigDefaultsShouldMatchPythonModule() {
        AddMemStrategy strategy = GraphDefaults.DEFAULT_STRATEGY;
        SearchConfig searchConfig = new SearchConfig();

        assertThat(EpisodeType.CONVERSATION.getValue()).isEqualTo(0);
        assertThat(strategy.isChineseEntity()).isTrue();
        assertThat(strategy.getSummaryTarget()).isEqualTo(250);
        assertThat(searchConfig.getBfsK()).isEqualTo(3);
        assertThat(searchConfig.getLanguage()).isEqualTo("en");
    }

    @Test
    void parseJsonShouldHandleCodeBlocksAndEnsureList() {
        String response = """
                ```json
                {"extracted_entities":[{"name":"Alice","entityTypeId":0}]}
                ```
                """;
        Object parsed =
            ParseResponse.parseJson(response, MultilingualBaseModel.responseFormat(EntityExtraction.class, "en"));

        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(ParseResponse.ensureList(List.of("a", "b"))).hasSize(2);
        assertThat(ParseResponse.tryGetKey("extracted_entities", Map.of("extractedEntities", 1))).isEqualTo(1);
    }

    @Test
    void multilingualSchemaShouldExposeDescriptions() {
        Map<String, Object> schema = MultilingualBaseModel.multilingualModelJsonSchema(EntitySummary.class, "cn", true);
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> summary = (Map<?, ?>) properties.get("summary");

        assertThat(ExtractionPromptLanguageBase.ensureValidLanguage("cn", 8)).isEqualTo("cn");
        assertThat(summary.get("description")).isEqualTo("实体相关的重要信息，500字以内的简要摘要");
    }

    @Test
    void templateManagerShouldLoadPromptResources() {
        TemplateManager manager = TemplateManager.getInstance();
        var template = manager.get("entity_extraction_conversation_cn");

        assertThat(template).isNotNull();
        assertThat(template.toMessages()).isNotEmpty();
        assertThat(manager.contains("entity_extraction_relation_en")).isTrue();
    }

    @Test
    void humanSummaryTargetShouldBeTwiceTheConfiguredLimit() {
        Entity entity = new Entity();
        entity.setName("A公司");
        Entity human = new Entity();
        human.setName("张明");
        human.setObjType("Human");

        ExtractionPrompts.PromptCall entityPrompt = ExtractionPrompts.extractEntityAttributes(
                entity, "content", "", "cn", Map.of("summary_target", "110"), 2);
        ExtractionPrompts.PromptCall humanPrompt = ExtractionPrompts.extractEntityAttributes(
                human, "content", "", "cn", Map.of("summary_target", "110"), 2);

        assertThat(entityPrompt.kwargs()).containsEntry("summary_target", "110");
        assertThat(humanPrompt.kwargs()).containsEntry("summary_target", 220);
    }
}
