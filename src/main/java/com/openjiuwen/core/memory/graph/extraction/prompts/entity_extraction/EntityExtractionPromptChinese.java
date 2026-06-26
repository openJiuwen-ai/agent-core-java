/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.extraction.prompts.entity_extraction;

import com.openjiuwen.core.memory.graph.extraction.EntityTypeDefinition;
import com.openjiuwen.core.memory.graph.extraction.MultilingualBaseModel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Chinese language strings and prompt descriptions for entity extraction.
 * <p>
 * Mirrors Python's {@code register_language} in
 * {@code openjiuwen/core/memory/graph/extraction/prompts/entity_extraction/cn.py}.
 * </p>
 */
public final class EntityExtractionPromptChinese {

    public static final String LANGUAGE_CODE = "cn";

    static {
        registerLanguage();
    }

    private EntityExtractionPromptChinese() {
    }

    public static void registerLanguage() {
        EntityExtractionPromptBase.SOURCE_DESCRIPTION.put(
                LANGUAGE_CODE,
                "\n<数据源描述>\n{source_description}\n</数据源描述>\n");
        EntityExtractionPromptBase.REF_JSON_OBJECT_DEF.put(LANGUAGE_CODE, "相关JSON Object定义");
        EntityExtractionPromptBase.OUTPUT_FORMAT.put(LANGUAGE_CODE, "输出定义（最终输出需要为JSON）");
        EntityExtractionPromptBase.DISPLAY_ENTITY.put(LANGUAGE_CODE, "{i}. {name}：\n{content}");
        EntityExtractionPromptBase.MARK_CURRENT_MSG.put(LANGUAGE_CODE, "<当前信息>\n{content}\n</当前信息>\n");
        EntityExtractionPromptBase.MARK_HISTORY_MSG.put(LANGUAGE_CODE, "<历史信息>\n{history}\n</历史信息>\n");
        EntityExtractionPromptBase.RELATION_FORMAT.put(
                LANGUAGE_CODE,
                "{name}（<{lhs}>-[{name}]-<{rhs}>）：{description}");
        EntityExtractionPromptBase.NO_RELATION_GIVEN.put(LANGUAGE_CODE, "无");

        EntityTypeDefinition.ENTITY_DEFINITION_DESCRIPTION.put(
                LANGUAGE_CODE,
                "：默认实体类型。若该实体不属于其他提供的类型，请选此类。");
        EntityTypeDefinition.HUMAN_ENTITY_DESCRIPTION.put(
                LANGUAGE_CODE,
                "：代表人类的实体类型，可以是用户也可以是其他人。");
        EntityTypeDefinition.AI_ENTITY_DESCRIPTION.put(
                LANGUAGE_CODE,
                "：代表AI的实体类型，可能是聊天助手也可能是其他智能体。");
        EntityTypeDefinition.RELATION_DEFINITION_DESCRIPTION.put(LANGUAGE_CODE, "：默认实体联系类型。");
        MultilingualBaseModel.registerMultilingualDescription(LANGUAGE_CODE, multilingualDescription());
        EntityExtractionPromptBase.REGISTERED_LANGUAGE.add(LANGUAGE_CODE);
    }

    private static Map<String, String> multilingualDescription() {
        Map<String, String> descriptions = new LinkedHashMap<>();
        descriptions.put("{{[ent_def_name]}}", "新提取实体的名字");
        descriptions.put("{{[ent_def_type]}}", "新提取实体的类型id，需要在提供的实体类型中");
        descriptions.put("{{[ent_ext_list]}}", "新提取的实体列表");
        descriptions.put("{{[ent_summary]}}", "实体相关的重要信息，500字以内的简要摘要");
        descriptions.put("{{[ent_attributes]}}", "实体的属性值");
        descriptions.put("{{[ent_info]}}", "提取的实体属性与摘要");
        descriptions.put("{{[ent_valid_since]}}", "实体的生效日期，请使用ISO格式YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[ent_valid_until]}}", "实体的中止日期，请使用ISO格式YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[ent_dupe_name]}}", "现有实体的名字");
        descriptions.put("{{[ent_dupe_id]}}", "现有实体的ID");
        descriptions.put("{{[ent_dupe_id_list]}}", "与现有实体重复的实体ID列表");
        descriptions.put("{{[ent_dupe_list]}}", "重复实体列表");
        descriptions.put("{{[rel_valid_since]}}", "事实/关系的生效日期，请使用ISO格式YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[rel_valid_until]}}", "事实/关系的中止日期，请使用ISO格式YYYY-MM-DDTHH:MM:SS[+HH:MM]");
        descriptions.put("{{[rel_fact]}}", "关于实体联系的事实");
        descriptions.put("{{[rel_name]}}", "该实体联系的名称");
        descriptions.put("{{[rel_source_name]}}", "主体的实体名称");
        descriptions.put("{{[rel_source_id]}}", "主体的实体ID");
        descriptions.put("{{[rel_target_name]}}", "客体的实体名称");
        descriptions.put("{{[rel_target_id]}}", "客体的实体ID");
        descriptions.put("{{[rel_ext_list]}}", "新提取的关系列表");
        descriptions.put("{{[rel_filter_list]}}", "与给定实体可能相关的事实ID列表");
        descriptions.put("{{[rel_filter_reasoning]}}", "关于为何特定事实与给定实体无关，提供简单推理过程, 无需过于详尽（100字内）");
        descriptions.put("{{[rel_dupe_need_merge]}}", "是否需要融合，若不需要后面所有内容可以留空");
        descriptions.put("{{[rel_dupe_reasoning]}}", "为何需要将新增关系与现有关系融合？");
        descriptions.put("{{[rel_dupe_content]}}", "更新后的实体联系事实");
        descriptions.put("{{[rel_dupe_id_list]}}", "需要与新增关系融合的已有关系ID列表");
        descriptions.put("{{[year]}}", "年");
        descriptions.put("{{[month]}}", "月");
        descriptions.put("{{[day]}}", "日");
        descriptions.put("{{[hour]}}", "小时");
        descriptions.put("{{[minute]}}", "分钟");
        descriptions.put("{{[second]}}", "秒");
        descriptions.put("{{[tz_name]}}", "时区名");
        descriptions.put("{{[tz_offset]}}", "相对于UTC标准时的时差（用+HH:MM格式）");
        descriptions.put("{{[tz_reason]}}", "为什么可能是这个时区");
        descriptions.put("{{[tz_list]}}", "可能的时区列表");
        descriptions.put(":", "：");
        return descriptions;
    }
}
