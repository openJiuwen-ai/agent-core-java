package com.openjiuwen.core.foundation.llm.schema;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import java.net.http.HttpClient;

public enum ModelHttpVersion {
    HTTP_1_1("HTTP_1_1", HttpClient.Version.HTTP_1_1),
    HTTP_2("HTTP_2", HttpClient.Version.HTTP_2);

    private final String value;
    private final HttpClient.Version jdkVersion;

    ModelHttpVersion(String value, HttpClient.Version jdkVersion) {
        this.value = value;
        this.jdkVersion = jdkVersion;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    public HttpClient.Version toJdkVersion() {
        return jdkVersion;
    }

    @JsonCreator
    public static ModelHttpVersion fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Unsupported http_version: " + value);
        }
        return switch (value.trim().toUpperCase()) {
            case "HTTP_1_1", "HTTP/1.1", "1.1" -> HTTP_1_1;
            case "HTTP_2", "HTTP/2", "2", "2.0" -> HTTP_2;
            default -> throw new IllegalArgumentException("Unsupported http_version: " + value);
        };
    }
}
