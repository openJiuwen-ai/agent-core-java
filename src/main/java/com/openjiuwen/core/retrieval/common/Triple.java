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
 * 
 * @since 0.1.7
 */
@Getter
@Setter
public class Triple {
    private String subject;
    private String predicate;
    private String object;
    private Double confidence;

    /**
     * LinkedHashMap<>.
     * 
     * @since 0.1.7
     */
    private Map<String, Object> metadata = new LinkedHashMap<>();

    /**
     * Triple.
     * 
     * @since 0.1.7
     */
    public Triple() {
    }

    /**
     * Triple.
     * 
     * @param subject subject
     * @param predicate predicate
     * @param object object
     * @since 0.1.7
     */
    public Triple(String subject, String predicate, String object) {
        this(subject, predicate, object, null, null);
    }

    /**
     * Triple.
     * 
     * @param subject subject
     * @param predicate predicate
     * @param object object
     * @param confidence confidence
     * @param metadata metadata
     * @since 0.1.7
     */
    public Triple(String subject, String predicate, String object, Double confidence, Map<String, Object> metadata) {
        setSubject(subject);
        setPredicate(predicate);
        setObject(object);
        setConfidence(confidence);
        setMetadata(metadata);
    }

    /**
     * setSubject.
     * 
     * @param subject subject
     * @since 0.1.7
     */
    public void setSubject(String subject) {
        RetrievalValidation.requireNonNull(subject, "Triple.subject");
        this.subject = subject;
    }

    /**
     * setPredicate.
     * 
     * @param predicate predicate
     * @since 0.1.7
     */
    public void setPredicate(String predicate) {
        RetrievalValidation.requireNonNull(predicate, "Triple.predicate");
        this.predicate = predicate;
    }

    /**
     * setObject.
     * 
     * @param object object
     * @since 0.1.7
     */
    public void setObject(String object) {
        RetrievalValidation.requireNonNull(object, "Triple.object");
        this.object = object;
    }

    /**
     * setMetadata.
     * 
     * @param metadata metadata
     * @since 0.1.7
     */
    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}
