/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FilesystemPromptToolProvidersTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> castMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<String> castList(Object value) {
        return (List<String>) value;
    }

    @Test
    void readFileMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new FilesystemPromptToolProviders.ReadFileMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("read_file");
        assertThat(provider.getDescription("cn")).isEqualTo("增强版文件读取工具。支持文本、图片、PDF 与 Jupyter Notebook。");
        assertThat(provider.getDescription("en")).isEqualTo("Enhanced file reader for text, images, PDFs, and Jupyter notebooks.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("file_path", "offset", "limit", "pages", "caption"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("file_path"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void writeFileMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new FilesystemPromptToolProviders.WriteFileMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("write_file");
        assertThat(provider.getDescription("cn")).isEqualTo("写入文件内容。如果文件已存在，将完全覆盖。");
        assertThat(provider.getDescription("en")).isEqualTo("Write file contents. Overwrites existing files only after a full read_file call.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("file_path", "content"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("file_path", "content"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void editFileMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new FilesystemPromptToolProviders.EditFileMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("edit_file");
        assertThat(provider.getDescription("cn")).startsWith("增强版文件编辑工具，对已有文件执行精确的字符串替换操作，仅传输差量。");
        assertThat(provider.getDescription("en")).startsWith("Enhanced file edit tool. Performs exact string replacement on existing files");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("file_path", "old_string", "new_string", "replace_all"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(
                java.util.List.of("file_path", "old_string", "new_string"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void globMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new FilesystemPromptToolProviders.GlobMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("glob");
        assertThat(provider.getDescription("cn")).isEqualTo("使用 glob 模式查找文件。");
        assertThat(provider.getDescription("en")).isEqualTo("Find files using glob patterns with structured results, optional path input, and default result truncation.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("pattern", "path"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("pattern"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void listDirMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new FilesystemPromptToolProviders.ListDirMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("list_files");
        assertThat(provider.getDescription("cn")).isEqualTo("列出目录内容。");
        assertThat(provider.getDescription("en")).isEqualTo("List directory contents.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("path", "show_hidden"));
        assertThat(castList(schema.get("required"))).isEmpty();
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }

    @Test
    void grepMetadataProviderMatchesPythonContract() {
        ToolMetadataProvider provider = new FilesystemPromptToolProviders.GrepMetadataProvider();
        Map<String, Object> schema = provider.getInputParams("cn");

        assertThat(provider.getName()).isEqualTo("grep");
        assertThat(provider.getDescription("cn")).isEqualTo("在文件中搜索内容。支持正则表达式。");
        assertThat(provider.getDescription("en")).isEqualTo("Search file contents with regex, structured output modes, pagination, context lines, file-type filters, and glob filters.");
        assertThat(schema.get("type")).isEqualTo("object");
        assertThat(castMap(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(
                java.util.List.of("pattern", "path", "ignore_case", "glob", "output_mode", "-B", "-A", "-C",
                        "context", "-n", "-i", "type", "head_limit", "offset", "multiline"));
        assertThat(castList(schema.get("required"))).containsExactlyElementsOf(java.util.List.of("pattern"));
        assertThatCode(provider::validate).doesNotThrowAnyException();
    }
}
