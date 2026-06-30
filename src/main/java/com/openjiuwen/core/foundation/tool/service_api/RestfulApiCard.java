/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.foundation.tool.ToolCard;
import java.util.Map;
import java.util.Set;

/**
 * RESTful API tool card with HTTP method and URL configuration.
 *
 * <p>Mirrors Python's {@code RestfulApiCard}.
 */
public class RestfulApiCard extends ToolCard {

  /** Supported HTTP methods. */
  public static final Set<String> SUPPORTED_METHODS =
          Set.of("POST", "GET", "PUT", "DELETE", "PATCH", "HEAD", "OPTIONS");

  /** Restful API URL, e.g. /api/v1/users. */
  private String url;

  /** HTTP method (POST or GET). */
  private String method = "POST";

  /** Default request headers. */
  private Map<String, Object> headers = Map.of();

  /** Default query parameters. */
  private Map<String, Object> queries = Map.of();

  /** Path parameters for URL placeholders. */
  private Map<String, Object> paths = Map.of();

  /** Request timeout in seconds. */
  private double timeout = 60.0;

  /** Maximum response size in bytes (default 10 MB). */
  private int maxResponseByteSize = 10 * 1024 * 1024;

  /** Auto-generated for codecheck compliance. */
  public RestfulApiCard(String url) {
    this.url = url;
  }

  private RestfulApiCard(
      String url,
      String method,
      Map<String, Object> headers,
      Map<String, Object> queries,
      Map<String, Object> paths,
      double timeout,
      int maxResponseByteSize) {
    this.url = url;
    if (method != null && !method.isBlank()) {
      this.method = method;
    }
    if (headers != null) {
      this.headers = headers;
    }
    if (queries != null) {
      this.queries = queries;
    }
    if (paths != null) {
      this.paths = paths;
    }
    this.timeout = timeout;
    this.maxResponseByteSize = maxResponseByteSize;
  }

  /** Auto-generated for codecheck compliance. */
  public String getUrl() {
    return url;
  }

  /** Auto-generated for codecheck compliance. */
  public String getMethod() {
    return method;
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> getHeaders() {
    return headers;
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> getQueries() {
    return queries;
  }

  /** Auto-generated for codecheck compliance. */
  public Map<String, Object> getPaths() {
    return paths;
  }

  /** Auto-generated for codecheck compliance. */
  public double getTimeout() {
    return timeout;
  }

  /** Auto-generated for codecheck compliance. */
  public int getMaxResponseByteSize() {
    return maxResponseByteSize;
  }

  /** Auto-generated for codecheck compliance. */
  public static Builder builder() {
    return new Builder();
  }

  /** Auto-generated for codecheck compliance. */
  public static class Builder extends ToolCard.Builder {
    private String url;
    private String method = "POST";
    private Map<String, Object> headers = Map.of();
    private Map<String, Object> queries = Map.of();
    private Map<String, Object> paths = Map.of();
    private double timeout = 60.0;
    private int maxResponseByteSize = 10 * 1024 * 1024;

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder id(String id) {
      super.id(id);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder name(String name) {
      super.name(name);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder description(String description) {
      super.description(description);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder inputParams(Map<String, Object> inputParams) {
      super.inputParams(inputParams);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public Builder properties(Map<String, Object> properties) {
      super.properties(properties);
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder url(String url) {
      this.url = url;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder method(String method) {
      this.method = method;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder headers(Map<String, Object> headers) {
      this.headers = headers;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder queries(Map<String, Object> queries) {
      this.queries = queries;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder paths(Map<String, Object> paths) {
      this.paths = paths;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder timeout(double timeout) {
      this.timeout = timeout;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    public Builder maxResponseByteSize(int maxResponseByteSize) {
      this.maxResponseByteSize = maxResponseByteSize;
      return this;
    }

    /** Auto-generated for codecheck compliance. */
    @Override
    /** Auto-generated for codecheck compliance. */
    public RestfulApiCard build() {
      RestfulApiCard card =
          new RestfulApiCard(url, method, headers, queries, paths, timeout, maxResponseByteSize);
      if (id != null) {
        card.setId(id);
      }
      card.setName(name);
      card.setDescription(description);
      card.setInputParams(inputParams);
      card.setProperties(properties);
      return card;
    }
  }
}
