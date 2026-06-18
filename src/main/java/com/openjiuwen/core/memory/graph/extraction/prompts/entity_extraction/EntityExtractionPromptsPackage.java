/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;

import java.util.List;
import java.util.Map;

/**
 * Package bridge for entity extraction prompt helpers.
 *
 * <p>Mirrors Python's package initializer in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/__init__.py}.</p>
 */
public final class EntityExtractionPromptsPackage {

    public static final String PYTHON_MODULE =
            "openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/__init__.py";
    public static final List<String> ALL = List.of(
            "ensure_valid_language",
            "format_relation_definitions",
            "get_formatting_kwargs"
    );

    static {
        registerLanguages();
    }

    private EntityExtractionPromptsPackage() {
    }

    public static void registerLanguages() {
        EntityExtractionPromptChinese.registerLanguage();
        EntityExtractionPromptEnglish.registerLanguage();
    }

    public static String ensureValidLanguage(Object language, int maxLen) {
        return EntityExtractionPromptBase.ensureValidLanguage(language, maxLen);
    }

    public static String formatRelationDefinitions(List<EntityTypeDefinition.RelationDef> relationTypes) {
        return EntityExtractionPromptBase.formatRelationDefinitions(relationTypes);
    }

    public static String formatRelationDefinitions(List<EntityTypeDefinition.RelationDef> relationTypes,
                                                   String language) {
        return EntityExtractionPromptBase.formatRelationDefinitions(relationTypes, language);
    }

    public static Map<String, String> getFormattingKwargs(String sourceDescription,
                                                          MultilingualBaseModel outputModel,
                                                          int outputIndent,
                                                          String history,
                                                          String content) {
        return EntityExtractionPromptBase.getFormattingKwargs(
                sourceDescription,
                outputModel,
                outputIndent,
                history,
                content
        );
    }

    public static Map<String, String> getFormattingKwargs(String sourceDescription,
                                                          MultilingualBaseModel outputModel,
                                                          int outputIndent,
                                                          String history,
                                                          String content,
                                                          String language) {
        return EntityExtractionPromptBase.getFormattingKwargs(
                sourceDescription,
                outputModel,
                outputIndent,
                history,
                content,
                language
        );
    }
}
