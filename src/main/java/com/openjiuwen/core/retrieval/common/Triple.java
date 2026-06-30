/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.retrieval.common;

import lombok.Getter;
import lombok.Setter;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Knowledge triple.
 */
@Getter
@Setter
public class Triple {

    private String subject;
    private String predicate;
    private String object;
    private Double confidence;
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Auto-generated for codecheck compliance.
     */
    public Triple() {
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Triple(String subject, String predicate, String object) {
        this(subject, predicate, object, null, null);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public Triple(String subject, String predicate, String object, Double confidence, Map<String, Object> metadata) {
        setSubject(subject);
        setPredicate(predicate);
        setObject(object);
        setConfidence(confidence);
        setMetadata(metadata);
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setSubject(String subject) {
        RetrievalValidation.requireNonNull(subject, "Triple.subject");
        this.subject = subject;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setPredicate(String predicate) {
        RetrievalValidation.requireNonNull(predicate, "Triple.predicate");
        this.predicate = predicate;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setObject(String object) {
        RetrievalValidation.requireNonNull(object, "Triple.object");
        this.object = object;
    }

    /**
     * Auto-generated for codecheck compliance.
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
