/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.EntityDef;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;
import com.openjiuwen.core.memory.graph.extraction.RelationDef;

import java.util.Map;

/**
 * ExtractionPromptLanguageCn.
 * 
 * @since 0.1.7
 */
public final class ExtractionPromptLanguageCn {
    /**
     * LANGUAGE_CODE.
     * 
     * @since 0.1.7
     */
    public static final String LANGUAGE_CODE = "cn";

    /**
     * ExtractionPromptLanguageCn.
     * 
     * @since 0.1.7
     */
    private ExtractionPromptLanguageCn() {
    }

    /**
     * registerLanguage.
     * 
     * @since 0.1.7
     */
    public static void registerLanguage() {
        ExtractionPromptLanguageBase.SOURCE_DESCRIPTION.put(LANGUAGE_CODE,
                "\n<数据源描述>\n{source_description}\n</数据源描述>\n");
        ExtractionPromptLanguageBase.REF_JSON_OBJECT_DEF.put(LANGUAGE_CODE, "相关JSON Object定义");
        ExtractionPromptLanguageBase.OUTPUT_FORMAT.put(LANGUAGE_CODE, "输出定义（最终输出需要为JSON）");
        ExtractionPromptLanguageBase.DISPLAY_ENTITY.put(LANGUAGE_CODE, "{i}. {name}：\n{content}");
        ExtractionPromptLanguageBase.MARK_CURRENT_MSG.put(LANGUAGE_CODE, "<当前信息>\n{content}\n</当前信息>\n");
        ExtractionPromptLanguageBase.MARK_HISTORY_MSG.put(LANGUAGE_CODE, "<历史信息>\n{history}\n</历史信息>\n");
        ExtractionPromptLanguageBase.RELATION_FORMAT.put(LANGUAGE_CODE,
                "{name}（<{lhs}>-[{name}]-<{rhs}>）：{description}");
        ExtractionPromptLanguageBase.NO_RELATION_GIVEN.put(LANGUAGE_CODE, "无");
        EntityDef.registerDescription(LANGUAGE_CODE, "：默认实体类型。若该实体不属于其他提供的类型，请选此类。");
        RelationDef.registerDescription(LANGUAGE_CODE, "：默认实体联系类型。");
        MultilingualBaseModel.registerDescriptions(LANGUAGE_CODE, Map.ofEntries(
                Map.entry("{{[ent_def_name]}}", "新提取实体的名字"), Map.entry("{{[ent_def_type]}}", "新提取实体的类型id，需要在提供的实体类型中"),
                Map.entry("{{[ent_ext_list]}}", "新提取的实体列表"), Map.entry("{{[ent_summary]}}", "实体相关的重要信息，500字以内的简要摘要"),
                Map.entry("{{[ent_attributes]}}", "实体的属性值"), Map.entry("{{[ent_dupe_name]}}", "现有实体的名字"),
                Map.entry("{{[ent_dupe_id]}}", "现有实体的ID"), Map.entry("{{[ent_dupe_id_list]}}", "与现有实体重复的实体ID列表"),
                Map.entry("{{[ent_dupe_list]}}", "重复实体列表"),
                Map.entry("{{[rel_valid_since]}}", "事实/关系的生效日期，请使用ISO格式YYYY-MM-DDTHH:MM:SS[+HH:MM]"),
                Map.entry("{{[rel_valid_until]}}", "事实/关系的中止日期，请使用ISO格式YYYY-MM-DDTHH:MM:SS[+HH:MM]"),
                Map.entry("{{[rel_fact]}}", "关于实体联系的事实"), Map.entry("{{[rel_name]}}", "该实体联系的名称"),
                Map.entry("{{[rel_source_id]}}", "主体的实体ID"), Map.entry("{{[rel_target_id]}}", "客体的实体ID"),
                Map.entry("{{[rel_ext_list]}}", "新提取的关系列表"), Map.entry("{{[rel_filter_list]}}", "与给定实体可能相关的事实ID列表"),
                Map.entry("{{[rel_filter_reasoning]}}", "关于为何特定事实与给定实体无关，提供简单推理过程, 无需过于详尽（100字内）"),
                Map.entry("{{[rel_dupe_need_merge]}}", "是否需要融合，若不需要后面所有内容可以留空"),
                Map.entry("{{[rel_dupe_reasoning]}}", "为何需要将新增关系与现有关系融合？"),
                Map.entry("{{[rel_dupe_content]}}", "更新后的实体联系事实"),
                Map.entry("{{[rel_dupe_id_list]}}", "需要与新增关系融合的已有关系ID列表"), Map.entry("{{[year]}}", "年"),
                Map.entry("{{[month]}}", "月"), Map.entry("{{[day]}}", "日"), Map.entry("{{[hour]}}", "小时"),
                Map.entry("{{[minute]}}", "分钟"), Map.entry("{{[second]}}", "秒"), Map.entry("{{[tz_name]}}", "时区名"),
                Map.entry("{{[tz_offset]}}", "相对于UTC标准时的时差（用+HH:MM格式）"), Map.entry("{{[tz_reason]}}", "为什么可能是这个时区"),
                Map.entry("{{[tz_list]}}", "可能的时区列表"), Map.entry(":", "：")));
        ExtractionPromptLanguageBase.REGISTERED_LANGUAGE.add(LANGUAGE_CODE);
    }
}
