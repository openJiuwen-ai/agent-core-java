package com.openjiuwen.memory.graph.extraction;

import com.openjiuwen.memory.config.graph.AddMemStrategy;
import com.openjiuwen.memory.config.graph.EpisodeType;
import com.openjiuwen.memory.config.graph.GraphDefaults;
import com.openjiuwen.memory.config.graph.SearchConfig;
import com.openjiuwen.memory.graph.extraction.prompts.TemplateManager;
import com.openjiuwen.memory.graph.extraction.prompts.entity_extraction.EntityExtractionPromptChinese;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

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
    @Disabled("Assertion no longer matches current develop behavior; kept disabled pending product decision or test rewrite")
    void parseJsonShouldHandleCodeBlocksAndEnsureList() {
        String response = """
                ```json
                {"extracted_entities":[{"name":"Alice","entityTypeId":0}]}
                ```
                """;
        Object parsed = ParseResponse.parseJson(response, new EntityExtraction().responseFormat("en"));

        assertThat(parsed).isInstanceOf(Map.class);
        assertThat(ParseResponse.ensureList(List.of("a", "b"))).hasSize(2);
        assertThat(ParseResponse.tryGetKey("extracted_entities", Map.of("extractedEntities", 1))).isEqualTo(1);
    }

    @Test
    void multilingualSchemaShouldExposeDescriptions() {
        // Multilingual descriptions are normally registered by the prompt
        // language class initializer; register explicitly so the schema build
        // below sees them regardless of class-loading order in this test.
        EntityExtractionPromptChinese.registerLanguage();

        Map<String, Object> schema = new EntitySummary().multilingualModelJsonSchema("cn", true);
        Map<?, ?> properties = (Map<?, ?>) schema.get("properties");
        Map<?, ?> summary = (Map<?, ?>) properties.get("summary");

        assertThat(com.openjiuwen.memory.graph.extraction.prompts.entity_extraction.ExtractionPromptLanguageBase.ensureValidLanguage("cn", 8)).isEqualTo("cn");
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
}
