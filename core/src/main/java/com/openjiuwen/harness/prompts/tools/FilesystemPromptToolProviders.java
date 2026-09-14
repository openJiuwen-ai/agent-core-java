/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Mirrors Python's {@code openjiuwen.harness.prompts.tools.filesystem} in
 * {@code openjiuwen/harness/prompts/tools/filesystem.py}.
 */
public final class FilesystemPromptToolProviders {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private FilesystemPromptToolProviders() {
    }

    private static Map<String, Object> parseSchema(String json) {
        try {
            return OBJECT_MAPPER.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse embedded prompt-tool schema JSON", ex);
        }
    }

    private static String resolve(String chinese, String english, String language) {
        return "en".equals(language) ? english : chinese;
    }

    private static final String READ_FILE_DESCRIPTION_CN = "增强版文件读取工具。支持文本、图片、PDF 与 Jupyter Notebook。";
    private static final String READ_FILE_DESCRIPTION_EN = "Enhanced file reader for text, images, PDFs, and Jupyter notebooks.";
    private static final String READ_FILE_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"要读取的绝对路径\"},\"offset\":{\"type\":\"integer\",\"description\":\"要跳过的行数（0 表示从头读取）。仅在文件过大无法一次读完时提供\"},\"limit\":{\"type\":\"integer\",\"description\":\"最多读取的行数（默认及上限均为 2000）。仅在文件过大无法一次读完时提供\"},\"pages\":{\"type\":\"string\",\"description\":\"PDF 专属页码范围，例如 '1-5'、'3'、'10-20'。每次最多 20 页\"},\"caption\":{\"type\":\"string\",\"description\":\"可选。读取 skills/… 下的图片时，填入 SKILL.md 中的图片说明文字（Markdown alt），用于多模态用户提示。\"}},\"required\":[\"file_path\"]}";
    private static final String READ_FILE_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"Absolute path of the file to read\"},\"offset\":{\"type\":\"integer\",\"description\":\"Number of lines to skip before reading (0 = start of file). Only provide when the file is too large to read at once\"},\"limit\":{\"type\":\"integer\",\"description\":\"Maximum number of lines to read (default and cap: 2000). Only provide when the file is too large to read at once\"},\"pages\":{\"type\":\"string\",\"description\":\"PDF-only page range, e.g. '1-5', '3', '10-20'. Maximum 20 pages per request\"},\"caption\":{\"type\":\"string\",\"description\":\"Optional. When reading an image under skills/, pass the figure caption (markdown alt text from SKILL.md) for the multimodal user prompt.\"}},\"required\":[\"file_path\"]}";

    /**
     * Mirrors Python's {@code ReadFileMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/filesystem.py}.
     */
    public static final class ReadFileMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "read_file";
        }

        @Override
        public String getDescription(String language) {
            return resolve(READ_FILE_DESCRIPTION_CN, READ_FILE_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(READ_FILE_SCHEMA_CN, READ_FILE_SCHEMA_EN, language));
        }
    }

    private static final String WRITE_FILE_DESCRIPTION_CN = "写入文件内容。如果文件已存在，将完全覆盖。";
    private static final String WRITE_FILE_DESCRIPTION_EN = "Write file contents. Overwrites existing files only after a full read_file call.";
    private static final String WRITE_FILE_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"要写入的文件路径\"},\"content\":{\"type\":\"string\",\"description\":\"要写入的内容\"}},\"required\":[\"file_path\",\"content\"]}";
    private static final String WRITE_FILE_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"Absolute path of the file to write\"},\"content\":{\"type\":\"string\",\"description\":\"Content to write\"}},\"required\":[\"file_path\",\"content\"]}";

    /**
     * Mirrors Python's {@code WriteFileMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/filesystem.py}.
     */
    public static final class WriteFileMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "write_file";
        }

        @Override
        public String getDescription(String language) {
            return resolve(WRITE_FILE_DESCRIPTION_CN, WRITE_FILE_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(WRITE_FILE_SCHEMA_CN, WRITE_FILE_SCHEMA_EN, language));
        }
    }

    private static final String EDIT_FILE_DESCRIPTION_CN = "增强版文件编辑工具，对已有文件执行精确的字符串替换操作，仅传输差量。\n\n核心行为：\n- 前置读取要求：编辑前必须通过 read_file 完整读取过该文件\n- 唯一性验证：old_string 须唯一匹配；多个匹配时须设置 replace_all=true 或提供更多上下文\n- 引号容错：自动尝试直引号与弯引号互转后匹配\n- 去消毒处理：自动将 HTML 实体（&lt; &gt; &amp; 等）还原为原始字符后匹配\n- 新文件创建：old_string='' 且目标文件不存在时创建新文件\n- 格式化处理：自动去除 new_string 行尾空白（.md/.mdx 文件除外）；保留文件原有行尾风格（LF/CRLF）\n- 外部修改检测：写入前通过时间戳 + 文件大小双重校验，若文件被外部修改则拒绝写入\n\n拒绝条件：文件超过 1 GiB / old_string 与 new_string 相同 / .ipynb 文件 / 文件不存在且 old_string 非空 / 文件已存在且 old_string 为空";
    private static final String EDIT_FILE_DESCRIPTION_EN = "Enhanced file edit tool. Performs exact string replacement on existing files, transmitting only the diff.\n\nCore behaviour:\n- Pre-read requirement: file must be fully read via read_file before editing\n- Uniqueness validation: old_string must match exactly once; set replace_all=true or add more context when multiple matches exist\n- Quote tolerance: automatically retries with straight/curly quote substitution\n- XML desanitization: reverses HTML entity encoding (&lt; &gt; &amp; etc.) before matching\n- New file creation: old_string='' and non-existent target path creates the file\n- Formatting: strips trailing whitespace from new_string lines (except .md/.mdx); preserves original EOL style (LF/CRLF)\n- External modification detection: rejects writes when mtime + size have changed since last read\n\nRejected when: file > 1 GiB / old_string == new_string / .ipynb file / file missing with non-empty old_string / file exists with empty old_string";
    private static final String EDIT_FILE_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"目标文件的绝对路径\"},\"old_string\":{\"type\":\"string\",\"description\":\"要替换的原始文本（空字符串可用于创建新文件或向空文件写入内容）。必须在文件中唯一匹配，否则须设置 replace_all=true 或提供更多上下文\"},\"new_string\":{\"type\":\"string\",\"description\":\"替换后的文本，必须与 old_string 不同\"},\"replace_all\":{\"type\":\"boolean\",\"description\":\"是否替换文件中所有匹配项，默认 false\"}},\"required\":[\"file_path\",\"old_string\",\"new_string\"]}";
    private static final String EDIT_FILE_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"file_path\":{\"type\":\"string\",\"description\":\"Absolute path to the target file\"},\"old_string\":{\"type\":\"string\",\"description\":\"The text to replace (empty string creates a new file or writes to an empty file). Must match exactly once unless replace_all=true or more context is provided\"},\"new_string\":{\"type\":\"string\",\"description\":\"The replacement text, must differ from old_string\"},\"replace_all\":{\"type\":\"boolean\",\"description\":\"Replace all occurrences of old_string in the file, default false\"}},\"required\":[\"file_path\",\"old_string\",\"new_string\"]}";

    /**
     * Mirrors Python's {@code EditFileMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/filesystem.py}.
     */
    public static final class EditFileMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "edit_file";
        }

        @Override
        public String getDescription(String language) {
            return resolve(EDIT_FILE_DESCRIPTION_CN, EDIT_FILE_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(EDIT_FILE_SCHEMA_CN, EDIT_FILE_SCHEMA_EN, language));
        }
    }

    private static final String GLOB_DESCRIPTION_CN = "使用 glob 模式查找文件。";
    private static final String GLOB_DESCRIPTION_EN = "Find files using glob patterns with structured results, optional path input, and default result truncation.";
    private static final String GLOB_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"description\":\"glob 模式（如 *.py, **/*.js）\"},\"path\":{\"type\":\"string\",\"description\":\"搜索目录，省略时默认当前工作目录\"}},\"required\":[\"pattern\"]}";
    private static final String GLOB_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"description\":\"Glob pattern (e.g. *.py, **/*.js)\"},\"path\":{\"type\":\"string\",\"description\":\"Directory to search. Defaults to the current working directory when omitted\"}},\"required\":[\"pattern\"]}";

    /**
     * Mirrors Python's {@code GlobMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/filesystem.py}.
     */
    public static final class GlobMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "glob";
        }

        @Override
        public String getDescription(String language) {
            return resolve(GLOB_DESCRIPTION_CN, GLOB_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(GLOB_SCHEMA_CN, GLOB_SCHEMA_EN, language));
        }
    }

    private static final String LIST_DIR_DESCRIPTION_CN = "列出目录内容。";
    private static final String LIST_DIR_DESCRIPTION_EN = "List directory contents.";
    private static final String LIST_DIR_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"目录路径\"},\"show_hidden\":{\"type\":\"boolean\",\"description\":\"显示隐藏文件\"}},\"required\":[]}";
    private static final String LIST_DIR_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\",\"description\":\"Directory path\"},\"show_hidden\":{\"type\":\"boolean\",\"description\":\"Show hidden files\"}},\"required\":[]}";

    /**
     * Mirrors Python's {@code ListDirMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/filesystem.py}.
     */
    public static final class ListDirMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "list_files";
        }

        @Override
        public String getDescription(String language) {
            return resolve(LIST_DIR_DESCRIPTION_CN, LIST_DIR_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(LIST_DIR_SCHEMA_CN, LIST_DIR_SCHEMA_EN, language));
        }
    }

    private static final String GREP_DESCRIPTION_CN = "在文件中搜索内容。支持正则表达式。";
    private static final String GREP_DESCRIPTION_EN = "Search file contents with regex, structured output modes, pagination, context lines, file-type filters, and glob filters.";
    private static final String GREP_SCHEMA_CN = "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"description\":\"搜索模式（正则表达式）\"},\"path\":{\"type\":\"string\",\"description\":\"搜索路径（文件或目录），默认为当前工作目录\"},\"ignore_case\":{\"type\":\"boolean\",\"description\":\"忽略大小写（兼容旧字段）\"},\"glob\":{\"type\":\"string\",\"description\":\"glob 过滤模式，例如 *.py 或 *.{ts,tsx}\"},\"output_mode\":{\"type\":\"string\",\"enum\":[\"content\",\"files_with_matches\",\"count\"],\"description\":\"输出模式：content、files_with_matches 或 count，默认 content\"},\"-B\":{\"type\":\"integer\",\"description\":\"每个匹配前显示的上下文行数，仅在 content 模式生效\"},\"-A\":{\"type\":\"integer\",\"description\":\"每个匹配后显示的上下文行数，仅在 content 模式生效\"},\"-C\":{\"type\":\"integer\",\"description\":\"每个匹配前后都显示的上下文行数，仅在 content 模式生效\"},\"context\":{\"type\":\"integer\",\"description\":\"-C 的别名，用于设置前后对称上下文行数\"},\"-n\":{\"type\":\"boolean\",\"description\":\"在 content 模式显示行号，默认 true\"},\"-i\":{\"type\":\"boolean\",\"description\":\"大小写不敏感搜索\"},\"type\":{\"type\":\"string\",\"description\":\"文件类型过滤，例如 py、js、ts，需要 rg\"},\"head_limit\":{\"type\":\"integer\",\"description\":\"只返回前 N 条记录或行。0 表示不限制，默认 250\"},\"offset\":{\"type\":\"integer\",\"description\":\"先跳过前 N 条记录或行，再应用 head_limit，默认 0\"},\"multiline\":{\"type\":\"boolean\",\"description\":\"启用多行正则模式，需要 rg\"}},\"required\":[\"pattern\"]}";
    private static final String GREP_SCHEMA_EN = "{\"type\":\"object\",\"properties\":{\"pattern\":{\"type\":\"string\",\"description\":\"Search pattern (regular expression)\"},\"path\":{\"type\":\"string\",\"description\":\"Search path (file or directory). Defaults to the current working directory\"},\"ignore_case\":{\"type\":\"boolean\",\"description\":\"Ignore case (legacy compatibility alias)\"},\"glob\":{\"type\":\"string\",\"description\":\"Glob filter pattern such as *.py or *.{ts,tsx}\"},\"output_mode\":{\"type\":\"string\",\"enum\":[\"content\",\"files_with_matches\",\"count\"],\"description\":\"Output mode: content, files_with_matches, or count. Defaults to content\"},\"-B\":{\"type\":\"integer\",\"description\":\"Lines of leading context before each match; only used in content mode\"},\"-A\":{\"type\":\"integer\",\"description\":\"Lines of trailing context after each match; only used in content mode\"},\"-C\":{\"type\":\"integer\",\"description\":\"Lines of context before and after each match; only used in content mode\"},\"context\":{\"type\":\"integer\",\"description\":\"Alias of -C for symmetric context lines\"},\"-n\":{\"type\":\"boolean\",\"description\":\"Show line numbers in content mode. Defaults to true\"},\"-i\":{\"type\":\"boolean\",\"description\":\"Case-insensitive search\"},\"type\":{\"type\":\"string\",\"description\":\"File type filter such as py, js, or ts. Requires rg\"},\"head_limit\":{\"type\":\"integer\",\"description\":\"Return only the first N entries or lines. Use 0 for unlimited. Defaults to 250\"},\"offset\":{\"type\":\"integer\",\"description\":\"Skip the first N entries or lines before applying head_limit. Defaults to 0\"},\"multiline\":{\"type\":\"boolean\",\"description\":\"Enable multiline regex mode. Requires rg\"}},\"required\":[\"pattern\"]}";

    /**
     * Mirrors Python's {@code GrepMetadataProvider} in
     * {@code openjiuwen/harness/prompts/tools/filesystem.py}.
     */
    public static final class GrepMetadataProvider implements ToolMetadataProvider {

        @Override
        public String getName() {
            return "grep";
        }

        @Override
        public String getDescription(String language) {
            return resolve(GREP_DESCRIPTION_CN, GREP_DESCRIPTION_EN, language);
        }

        @Override
        public Map<String, Object> getInputParams(String language) {
            return parseSchema(resolve(GREP_SCHEMA_CN, GREP_SCHEMA_EN, language));
        }
    }
}
