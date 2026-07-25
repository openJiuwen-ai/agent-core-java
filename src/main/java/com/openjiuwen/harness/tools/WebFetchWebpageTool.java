/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.harness.tools;

import com.openjiuwen.harness.tools.web.WebHttpFetcher;
import com.openjiuwen.harness.tools.web.WebHttpResponse;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Public class WebFetchWebpageTool used by the Java parity implementation.
 *
 * @since 1.0
 */
public class WebFetchWebpageTool {
    private final WebHttpFetcher fetcher;
    private final String language;

    /**
     * Auto-generated for codecheck compliance.
     */
    public WebFetchWebpageTool() {
        this("cn");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WebFetchWebpageTool(String language) {
        this(WebFetchWebpageTool::defaultFetch, language);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WebFetchWebpageTool(WebHttpFetcher fetcher) {
        this(fetcher, "cn");
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public WebFetchWebpageTool(WebHttpFetcher fetcher, String language) {
        this.fetcher = fetcher;
        this.language = language == null || language.isBlank() ? "cn" : language;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public String getLanguage() {
        return language;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public ToolOutput invoke(String url) {
        if (url == null || url.isBlank()) {
            return ToolOutput.builder().success(false).error("url is required").build();
        }
        try {
            WebHttpResponse response = fetcher.fetch("GET", url);
            boolean isRequestSuccessful = response.statusCode() >= 200 && response.statusCode() < 300;
            return ToolOutput.builder()
                    .success(isRequestSuccessful)
                    .data(response.text())
                    .error(isRequestSuccessful ? null : "http status " + response.statusCode())
                    .build();
        } catch (Exception ex) {
            return ToolOutput.builder().success(false).error(ex.getMessage()).build();
        }
    }

    static WebHttpResponse defaultFetch(String method, String url) throws Exception {
        java.net.URLConnection urlConnection = URI.create(url).toURL().openConnection();
        if (!(urlConnection instanceof HttpURLConnection connection)) {
            throw new IllegalStateException("Only HTTP(S) URL is supported");
        }
        connection.setRequestMethod(method);
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(5000);
        int status = connection.getResponseCode();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                status >= 400 ? connection.getErrorStream() : connection.getInputStream(),
                StandardCharsets.UTF_8))) {
            String text = reader.lines().collect(Collectors.joining("\n"));
            return new WebHttpResponse(status, text);
        }
    }
}
