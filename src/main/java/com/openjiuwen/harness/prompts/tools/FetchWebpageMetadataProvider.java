/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mirrors Python's {@code FetchWebpageMetadataProvider} in
 * {@code openjiuwen/harness/prompts/tools/web_tools.py}.
 */
public class FetchWebpageMetadataProvider implements ToolMetadataProvider {

    private static final Map<String, String> DESCRIPTION = Map.of(
            "cn",
            "通常配合 paid_search 或 free_search 使用：先搜索，再抓取结果页，不要只依赖摘要。"
                    + "抓取网页文本，返回状态码、标题和正文文本。通常配合 free_search 使用：先搜索，再抓取"
                    + "前几个结果页，而不是只依赖搜索摘要。可设置 max_chars=0 关闭截断，也可以调大 "
                    + "timeout_seconds 处理慢站点。"
                    + "适用场景：文档、博客、新闻、API 参考等普通网页。"
                    + "代码仓地址（GitHub/GitLab/Gitee/Gitcode/Bitbucket 等）一般不适合用本工具——"
                    + "网页只能看到渲染后的目录页;要读源码、看历史、跨文件搜索，"
                    + "更顺手的方式是用 shell 工具(bash 或 powershell)执行 `git clone` 拉到本地。",
            "en",
            "Fetch webpage text content from a URL and return status, title, and plain text. "
                    + "Usually used after paid_search or free_search: search first, then fetch the top few result pages "
                    + "instead of reasoning only from snippets. Set max_chars=0 to disable clipping and "
                    + "use a larger timeout_seconds for slow pages. "
                    + "Best fit: documentation, blog posts, news, API references, and similar general web content. "
                    + "Git repository URLs (GitHub/GitLab/Gitee/Gitcode/Bitbucket, etc.) are usually a poor fit - "
                    + "the webpage only shows a rendered file tree, while reading source, history, or searching across "
                    + "files is far easier after a local `git clone` via the shell tool (bash or powershell)."
    );

    @Override
    public String getName() {
        return "fetch_webpage";
    }

    @Override
    public String getDescription(String language) {
        return DESCRIPTION.getOrDefault(language, DESCRIPTION.get("cn"));
    }

    @Override
    public Map<String, Object> getInputParams(String language) {
        return getFetchWebpageInputParams(language);
    }

    public static Map<String, Object> getFetchWebpageInputParams(String language) {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("url", property("string", description(language, "要抓取的网页 URL。", "Webpage URL to fetch.")));
        properties.put("max_chars", property(
                "integer",
                description(language, "返回内容最大字符数；设为 0 表示不截断。", "Maximum content characters. Set to 0 to disable clipping."),
                20000
        ));
        properties.put("timeout_seconds", property(
                "integer",
                description(language, "请求超时时间（秒）；慢站点可适当调大。", "Request timeout in seconds. Larger values can be used for slow websites."),
                45
        ));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("url"));
        return schema;
    }

    private static String description(String language, String chinese, String english) {
        return "en".equals(language) ? english : chinese;
    }

    private static Map<String, Object> property(String type, String description) {
        Map<String, Object> property = new LinkedHashMap<>();
        property.put("type", type);
        property.put("description", description);
        return property;
    }

    private static Map<String, Object> property(String type, String description, Object defaultValue) {
        Map<String, Object> property = property(type, description);
        property.put("default", defaultValue);
        return property;
    }
}
