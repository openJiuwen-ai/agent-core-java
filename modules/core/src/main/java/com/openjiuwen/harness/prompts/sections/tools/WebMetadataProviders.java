/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.prompts.sections.tools;

import java.util.List;
import java.util.Map;

/**
 * Web search/fetch metadata providers.
 *
 * @since 0.1.7
 */
final class WebMetadataProviders {
    /**
     * WebMetadataProviders.
     * 
     * @since 0.1.7
     */
    private WebMetadataProviders() {
    }

    static final class FreeSearchMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "free_search";
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
            return text(language, "免费搜索，返回结果 URL 和摘要；如果 paid_search 可用或已配置 API，优先使用 paid_search。",
                    "Free search. If paid_search is available/configured, call paid_search first; use free_search "
                            + "only as fallback or when explicitly requested.");
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
            return querySchema(language,
                    text(language, "搜索查询文本。查询最新、当前、今年、实时、近期信息时，必须使用系统提示中的当前年份或日期。",
                            "Free search query text. For latest/current/recent information, use the current year or "
                                    + "date from the system prompt."),
                    8, 20);
        }
    }

    static final class PaidSearchMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "paid_search";
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
            return text(language, "配置 API 时这是首选联网搜索工具；付费搜索支持 provider=auto|bocha|perplexity|serper|jina。",
                    "Paid search via Bocha/Perplexity/SERPER/JINA. When available, this is the preferred web "
                            + "search tool.");
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
            return ToolSchemaSupport.objectSchema(ToolSchemaSupport.properties(new Object[]{"query",
                    ToolSchemaSupport.property("string", text(language, "付费搜索查询文本。", "Paid search query text.")),
                    "provider",
                    Map.of("type", "string", "description", "Provider: auto|bocha|perplexity|serper|jina.", "default",
                            "auto"),
                    "max_results",
                    Map.of("type", "integer", "description",
                            text(language, "最大 URL 数（1-20）。", "Maximum number of URLs (1-20)."), "default", 8),
                    "timeout_seconds",
                    Map.of("type", "integer", "description",
                            text(language, "请求超时时间（秒，10-120）。", "Request timeout in seconds (10-120)."), "default",
                            45)}),
                    List.of("query"));
        }
    }

    static final class FetchWebpageMetadataProvider implements ToolMetadataProvider {
        /**
         * getName.
         * 
         * @return the result
         * @since 0.1.7
         */
        @Override
        public String getName() {
            return "fetch_webpage";
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
            return text(language, "抓取网页文本，返回状态码、标题和正文文本。通常配合 paid_search 或 free_search 使用。",
                    "Fetch webpage text content from a URL and return status, title, and plain text. Usually used "
                            + "after paid_search or free_search.");
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
            return ToolSchemaSupport.objectSchema(ToolSchemaSupport.properties(new Object[]{"url",
                    ToolSchemaSupport.property("string", text(language, "要抓取的网页 URL。", "Webpage URL to fetch.")),
                    "max_chars",
                    Map.of("type", "integer", "description",
                            text(language, "返回内容最大字符数；设为 0 表示不截断。",
                                    "Maximum content characters. Set to 0 to disable clipping."),
                            "default", 20000),
                    "timeout_seconds",
                    Map.of("type", "integer", "description",
                            text(language, "请求超时时间（秒）；慢站点可适当调大。",
                                    "Request timeout in seconds. Larger values can be used for slow websites."),
                            "default", 45)}),
                    List.of("url"));
        }
    }

    /**
     * querySchema.
     * 
     * @param language language
     * @param queryDescription queryDescription
     * @param defaultMaxResults defaultMaxResults
     * @param defaultTimeout defaultTimeout
     * @return the result
     * @since 0.1.7
     */
    private static Map<String, Object> querySchema(String language, String queryDescription, int defaultMaxResults,
            int defaultTimeout) {
        return ToolSchemaSupport.objectSchema(ToolSchemaSupport
                .properties(new Object[]{"query", ToolSchemaSupport.property("string", queryDescription), "max_results",
                        Map.of("type", "integer", "description",
                                text(language, "最大结果数（1-20）。", "Maximum number of results (1-20)."), "default",
                                defaultMaxResults),
                        "timeout_seconds",
                        Map.of("type", "integer", "description",
                                text(language, "请求超时时间（秒，5-60）。", "Request timeout in seconds (5-60)."), "default",
                                defaultTimeout)}),
                List.of("query"));
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
    private static String text(String language, String cn, String en) {
        return ToolSchemaSupport.localized(language, cn, en);
    }
}
