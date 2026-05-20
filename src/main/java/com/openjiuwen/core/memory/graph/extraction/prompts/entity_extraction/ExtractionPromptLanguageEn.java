/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.EntityDef;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;
import com.openjiuwen.core.memory.graph.extraction.RelationDef;

import java.util.Map;

/**
 * Auto-generated for codecheck compliance.
 */
public final class ExtractionPromptLanguageEn {
    /**
     * Auto-generated for codecheck compliance.
     */
    public static final String LANGUAGE_CODE = "en";

    private ExtractionPromptLanguageEn() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public static void registerLanguage() {
        ExtractionPromptLanguageBase.SOURCE_DESCRIPTION.put(
                LANGUAGE_CODE,
                "\n<source_description>\n{source_description}\n</source_description>\n");
        ExtractionPromptLanguageBase.REF_JSON_OBJECT_DEF.put(LANGUAGE_CODE, "Definition for relevant JSON objects");
        ExtractionPromptLanguageBase.OUTPUT_FORMAT.put(
                LANGUAGE_CODE,
                "Output Definition (Final Output NEEDS to be JSON)");
        ExtractionPromptLanguageBase.DISPLAY_ENTITY.put(LANGUAGE_CODE, "{i}. {name}:\n{content}");
        ExtractionPromptLanguageBase.MARK_CURRENT_MSG.put(
                LANGUAGE_CODE,
                "<current_messages>\n{content}\n</current_messages>\n");
        ExtractionPromptLanguageBase.MARK_HISTORY_MSG.put(
                LANGUAGE_CODE,
                "<history_messages>\n{history}\n</history_messages>\n");
        ExtractionPromptLanguageBase.RELATION_FORMAT.put(
                LANGUAGE_CODE,
                "{name} (<{lhs}>-[{name}]-<{rhs}>): {description}");
        ExtractionPromptLanguageBase.NO_RELATION_GIVEN.put(LANGUAGE_CODE, "None");
        EntityDef.registerDescription(
                LANGUAGE_CODE,
                ": Default entity type, pick this if no other option is suitable.");
        RelationDef.registerDescription(LANGUAGE_CODE, ": Default relation type.");
        MultilingualBaseModel.registerDescriptions(LANGUAGE_CODE, Map.ofEntries(
                Map.entry("{{[ent_def_name]}}", "Name of extracted entity"),
                Map.entry(
                        "{{[ent_def_type]}}",
                        "Type ID of extracted entity, needs to be from the list of provided entity types"),
                Map.entry("{{[ent_ext_list]}}", "List of extracted entities"),
                Map.entry(
                        "{{[ent_summary]}}",
                        "Important information regarding the entity, a short & concise summary within 250 words"),
                Map.entry("{{[ent_attributes]}}", "Entity attributes"),
                Map.entry("{{[ent_dupe_name]}}", "Name of existing entity"),
                Map.entry("{{[ent_dupe_id]}}", "ID of existing entity"),
                Map.entry(
                        "{{[ent_dupe_id_list]}}",
                        "List of IDs for entities that may be deplicate of this existing entity"),
                Map.entry("{{[ent_dupe_list]}}", "List of duplicate entities"),
                Map.entry(
                        "{{[rel_valid_since]}}",
                        "Date for when this fact / relation starts to be valid, "
                                + "please use ISO format YYYY-MM-DDTHH:MM:SS[+HH:MM]"),
                Map.entry(
                        "{{[rel_valid_until]}}",
                        "Date for when this fact / relation stops being valid, "
                                + "please use ISO format YYYY-MM-DDTHH:MM:SS[+HH:MM]"),
                Map.entry("{{[rel_fact]}}", "Fact regarding the relation"),
                Map.entry("{{[rel_name]}}", "Name of factual relation"),
                Map.entry("{{[rel_source_id]}}", "ID of source entity"),
                Map.entry("{{[rel_target_id]}}", "ID of target entity"),
                Map.entry("{{[rel_ext_list]}}", "List of extracted relations"),
                Map.entry(
                        "{{[rel_filter_list]}}",
                        "List of IDs for facts that are likely relevant to the provided entity"),
                Map.entry(
                        "{{[rel_filter_reasoning]}}",
                        "A brief reasoning for why certain facts are irrelevant, "
                                + "no need to be extensive (within 150 words)"),
                Map.entry(
                        "{{[rel_dupe_need_merge]}}",
                        "Whether merging is required, if no merge then other fields can be left empty"),
                Map.entry("{{[rel_dupe_reasoning]}}", "Why do we need to merge the new relation with existing?"),
                Map.entry("{{[rel_dupe_content]}}", "Updated fact regarding the relation"),
                Map.entry(
                        "{{[rel_dupe_id_list]}}",
                        "List of IDs for existing relations that should be merged within the new relation"),
                Map.entry("{{[year]}}", "year"),
                Map.entry("{{[month]}}", "month"),
                Map.entry("{{[day]}}", "day"),
                Map.entry("{{[hour]}}", "hour"),
                Map.entry("{{[minute]}}", "minute"),
                Map.entry("{{[second]}}", "second"),
                Map.entry("{{[tz_name]}}", "Timezone's name"),
                Map.entry("{{[tz_offset]}}", "Offset from UTC (use +HH:MM format)"),
                Map.entry("{{[tz_reason]}}", "Why this candidate"),
                Map.entry("{{[tz_list]}}", "List of candidate timezones"),
                Map.entry(":", ":")
        ));
        ExtractionPromptLanguageBase.REGISTERED_LANGUAGE.add(LANGUAGE_CODE);
    }
}
