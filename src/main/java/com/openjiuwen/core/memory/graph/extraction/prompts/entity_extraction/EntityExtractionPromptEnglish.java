/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * English language strings and prompt descriptions for entity extraction.
 * <p>
 * Mirrors Python's {@code register_language} in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/en.py}.
 * </p>
 */
public final class EntityExtractionPromptEnglish {

    public static final String LANGUAGE_CODE = "en";

    static {
        registerLanguage();
    }

    private EntityExtractionPromptEnglish() {
    }

    public static void registerLanguage() {
        EntityExtractionPromptBase.SOURCE_DESCRIPTION.put(
                LANGUAGE_CODE,
                "\n<source_description>\n{source_description}\n</source_description>\n");
        EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.put(LANGUAGE_CODE, "Definition for relevant JSON objects");
        EntityExtractionPromptBase.OUTPUT_FORMAT.put(LANGUAGE_CODE, "Output Definition (Final Output NEEDS to be JSON)");
        EntityExtractionPromptBase.DISPLAY_ENTITY.put(LANGUAGE_CODE, "{i}. {name}:\n{content}");
        EntityExtractionPromptBase.MARK_CURRENT_MSG.put(
                LANGUAGE_CODE,
                "<current_messages>\n{content}\n</current_messages>\n");
        EntityExtractionPromptBase.MARK_HISTORY_MSG.put(
                LANGUAGE_CODE,
                "<history_messages>\n{history}\n</history_messages>\n");
        EntityExtractionPromptBase.RELATION_FORMAT.put(
                LANGUAGE_CODE,
                "{name} (<{lhs}>-[{name}]-<{rhs}>): {description}");
        EntityExtractionPromptBase.NO_RELATION_GIVEN.put(LANGUAGE_CODE, "None");

        EntityTypeDefinition.ENTITY_DEFINITION_DESCRIPTION.put(
                LANGUAGE_CODE,
                ": Default entity type, pick this if no other option is suitable.");
        EntityTypeDefinition.HUMAN_ENTITY_DESCRIPTION.put(
                LANGUAGE_CODE,
                ": Represent a human, can either be the user or other people.");
        EntityTypeDefinition.AI_ENTITY_DESCRIPTION.put(
                LANGUAGE_CODE,
                ": Represent an AI assistant, can be a chatbot or other types of AI agents.");
        EntityTypeDefinition.RELATION_DEFINITION_DESCRIPTION.put(LANGUAGE_CODE, ": Default relation type.");
        MultilingualBaseModel.registerMultilingualDescription(LANGUAGE_CODE, multilingualDescription());
        EntityExtractionPromptBase.REGISTERED_LANGUAGE.add(LANGUAGE_CODE);
    }

    private static Map<String, String> multilingualDescription() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("{{[ent_def_name]}}", "Name of extracted entity");
        descriptions.put("{{[ent_def_type]}}", "Type ID of extracted entity, needs to be from the list of provided entity types");
        descriptions.put("{{[ent_ext_list]}}", "List of extracted entities");
        descriptions.put("{{[ent_summary]}}", "Important information regarding the entity, a short & concise summary within 250 words");
        descriptions.put("{{[ent_attributes]}}", "Entity attributes");
        descriptions.put("{{[ent_info]}}", "Extracted entity attributes and summary");
        descriptions.put("{{[ent_valid_since]}}",
                "Date for when this entity starts to be valid, please use ISO format YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[ent_valid_until]}}",
                "Date for when this entity stops being valid, please use ISO format YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[ent_dupe_name]}}", "Name of existing entity");
        descriptions.put("{{[ent_dupe_id]}}", "ID of existing entity");
        descriptions.put("{{[ent_dupe_id_list]}}", "List of IDs for entities that may be deplicate of this existing entity");
        descriptions.put("{{[ent_dupe_list]}}", "List of duplicate entities");
        descriptions.put("{{[rel_valid_since]}}",
                "Date for when this fact / relation starts to be valid, please use ISO format YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[rel_valid_until]}}",
                "Date for when this fact / relation stops being valid, please use ISO format YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[rel_fact]}}", "Fact regarding the relation");
        descriptions.put("{{[rel_name]}}", "Name of factual relation");
        descriptions.put("{{[rel_source_name]}}", "Name of source entity");
        descriptions.put("{{[rel_source_id]}}", "ID of source entity");
        descriptions.put("{{[rel_target_name]}}", "Name of target entity");
        descriptions.put("{{[rel_target_id]}}", "ID of target entity");
        descriptions.put("{{[rel_ext_list]}}", "List of extracted relations");
        descriptions.put("{{[rel_filter_list]}}", "List of IDs for facts that are likely relevant to the provided entity");
        descriptions.put("{{[rel_filter_reasoning]}}",
                "A brief reasoning for why certain facts are irrelevant, no need to be extensive (within 150 words)");
        descriptions.put("{{[rel_dupe_need_merge]}}", "Whether merging is required, if no merge then other fields can be left empty");
        descriptions.put("{{[rel_dupe_reasoning]}}", "Why do we need to merge the new relation with existing?");
        descriptions.put("{{[rel_dupe_content]}}", "Updated fact regarding the relation");
        descriptions.put("{{[rel_dupe_id_list]}}", "List of IDs for existing relations that should be merged within the new relation");
        descriptions.put("{{[year]}}", "year");
        descriptions.put("{{[month]}}", "month");
        descriptions.put("{{[day]}}", "day");
        descriptions.put("{{[hour]}}", "hour");
        descriptions.put("{{[minute]}}", "minute");
        descriptions.put("{{[second]}}", "second");
        descriptions.put("{{[tz_name]}}", "Timezone's name");
        descriptions.put("{{[tz_offset]}}", "Offset from UTC (use +HH:MM format)");
        descriptions.put("{{[tz_reason]}}", "Why this candidate");
        descriptions.put("{{[tz_list]}}", "List of candidate timezones");
        descriptions.put(":", ":");
        return descriptions;
    }
}
