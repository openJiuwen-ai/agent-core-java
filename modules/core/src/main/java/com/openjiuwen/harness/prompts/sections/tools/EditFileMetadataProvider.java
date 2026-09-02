/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Edit-file tool metadata provider.
 * 
 * @since 0.1.7
 */
public final class EditFileMetadataProvider implements ToolMetadataProvider {
    /**
     * getName.
     * 
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getName() {
        return "edit_file";
    }

    /**
     * getDescription.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public String getDescription(String language) {
        return ToolSchemaSupport.localized(language, "增强版文件编辑工具，对已有文件执行精确的字符串替换操作，仅传输差量。",
                "Enhanced file edit tool. Performs exact string replacement on existing files, transmitting only "
                        + "the diff.");
    }

    /**
     * getInputParams.
     * 
     * @param language language
     * @return the result
     * @since 0.1.7
     */
    @Override
    public Map<String, Object> getInputParams(String language) {
        return ToolSchemaSupport.objectSchema(
                ToolSchemaSupport.properties(new Object[]{"file_path",
                        ToolSchemaSupport
                                .property("string", text(language, "目标文件的绝对路径", "Absolute path to the target file")),
                        "old_string",
                        ToolSchemaSupport.property("string",
                                text(language, "要替换的原始文本，必须在文件中唯一匹配，除非设置 replace_all=true",
                                        "The text to isReplace. Must match exactly once unless replace_all=true")),
                        "new_string",
                        ToolSchemaSupport.property("string",
                                text(language, "替换后的文本，必须与 old_string 不同",
                                        "The replacement text, must differ from old_string")),
                        "replace_all",
                        ToolSchemaSupport.property("boolean",
                                text(language, "是否替换文件中所有匹配项，默认 false",
                                        "Replace all occurrences of old_string in the file, default false"))}),
                List.of("file_path", "old_string", "new_string"));
    }

    /**
     * text.
     * 
     * @param language language
     * @param cn cn
     * @param en en
     * @return the result
     * @since 0.1.7
     */
    private String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
