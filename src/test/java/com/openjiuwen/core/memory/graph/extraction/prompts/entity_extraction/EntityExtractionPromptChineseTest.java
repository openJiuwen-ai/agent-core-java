/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mirrors Python's Chinese entity extraction language registration in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/cn.py}.
 */
class EntityExtractionPromptChineseTest {

    @BeforeEach
    void setUp() {
        clearRegistries();
    }

    @AfterEach
    void tearDown() {
        clearRegistries();
    }

    @Test
    void registerLanguagePopulatesPromptRegistries() {
        EntityExtractionPromptChinese.registerLanguage();

        assertTrue(EntityExtractionPromptBase.REGISTERED_LANGUAGE.contains("cn"));
        assertEquals("\n<数据源描述>\n来源\n</数据源描述>\n",
                EntityExtractionPromptBase.formatSourceDescription("来源", "cn"));
        assertEquals("相关JSON Object定义", EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.get("cn"));
        assertEquals("输出定义（最终输出需要为JSON）", EntityExtractionPromptBase.OUTPUT_FORMAT.get("cn"));
        assertEquals("2. 名称：\n内容",
                EntityExtractionPromptBase.formatExistingEntities(
                        java.util.List.of(Map.of("name", "名称", "content", "内容")),
                        2,
                        "cn"));
        assertEquals("无", EntityExtractionPromptBase.formatRelationDefinitions(null, "cn"));
    }

    @Test
    void registerLanguagePopulatesEntityTypeDescriptions() {
        EntityExtractionPromptChinese.registerLanguage();

        assertEquals("：默认实体类型。若该实体不属于其他提供的类型，请选此类。",
                EntityTypeDefinition.ENTITY_DEFINITION_DESCRIPTION.get("cn"));
        assertEquals("：代表人类的实体类型，可以是用户也可以是其他人。",
                EntityTypeDefinition.HUMAN_ENTITY_DESCRIPTION.get("cn"));
        assertEquals("：代表AI的实体类型，可能是聊天助手也可能是其他智能体。",
                EntityTypeDefinition.AI_ENTITY_DESCRIPTION.get("cn"));
        assertEquals("：默认实体联系类型。", EntityTypeDefinition.RELATION_DEFINITION_DESCRIPTION.get("cn"));
    }

    @Test
    void registerLanguagePopulatesMultilingualDescriptions() {
        EntityExtractionPromptChinese.registerLanguage();

        Map<String, String> descriptions = MultilingualBaseModel.getMultilingualDescription().get("cn");
        assertEquals("新提取实体的名字", descriptions.get("{{[ent_def_name]}}"));
        assertEquals("实体相关的重要信息，500字以内的简要摘要", descriptions.get("{{[ent_summary]}}"));
        assertEquals("需要与新增关系融合的已有关系ID列表", descriptions.get("{{[rel_dupe_id_list]}}"));
        assertEquals("相对于UTC标准时的时差（用+HH:MM格式）", descriptions.get("{{[tz_offset]}}"));
        assertEquals("：", descriptions.get(":"));
    }

    private static void clearRegistries() {
        EntityExtractionPromptBase.REGISTERED_LANGUAGE.clear();
        EntityExtractionPromptBase.SOURCE_DESCRIPTION.clear();
        EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.clear();
        EntityExtractionPromptBase.OUTPUT_FORMAT.clear();
        EntityExtractionPromptBase.DISPLAY_ENTITY.clear();
        EntityExtractionPromptBase.MARK_CURRENT_MSG.clear();
        EntityExtractionPromptBase.MARK_HISTORY_MSG.clear();
        EntityExtractionPromptBase.RELATION_FORMAT.clear();
        EntityExtractionPromptBase.NO_RELATION_GIVEN.clear();
        EntityTypeDefinition.ENTITY_DEFINITION_DESCRIPTION.clear();
        EntityTypeDefinition.RELATION_DEFINITION_DESCRIPTION.clear();
        EntityTypeDefinition.HUMAN_ENTITY_DESCRIPTION.clear();
        EntityTypeDefinition.AI_ENTITY_DESCRIPTION.clear();
        MultilingualBaseModel.getMultilingualDescription().clear();
    }
}
