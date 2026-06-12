/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.memory.config.EpisodeType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Input validation for graph memory add and search operations.
 *
 * <p>Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.validate_input} in
 * {@code openjiuwen/core/memory/graph/graph_memory/validate_input.py}.</p>
 */
public final class GraphMemoryInputValidator {

    public static final String PYTHON_MODULE = "openjiuwen/core/memory/graph/graph_memory/validate_input.py";
    public static final List<String> EXPORTED_SYMBOLS = List.of(
            "validate_add_memory_input",
            "validate_search_input"
    );

    private static final String STORE_TYPE = "graph mem store";

    private GraphMemoryInputValidator() {
    }

    public static void validateAddMemoryInput(int userIdMaxLength, EpisodeType srcType, String userId) {
        validateAddMemoryInput(userIdMaxLength, srcType, userId, null);
    }

    public static void validateAddMemoryInput(int userIdMaxLength,
                                              EpisodeType srcType,
                                              String userId,
                                              Map<?, ?> contentFmtKwargs) {
        if (contentFmtKwargs != null) {
            if (contentFmtKwargs.isEmpty()) {
                throw validationError("When supplied, content_fmt_kwargs must be of type dict[str, str] and not empty");
            }
            for (Map.Entry<?, ?> entry : contentFmtKwargs.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || !(entry.getValue() instanceof String value)
                        || key.isEmpty()
                        || value.isEmpty()) {
                    throw validationError("content_fmt_kwargs must have non-empty keys and values of string type");
                }
            }
        }
        if (srcType == null) {
            throw validationError("src_type must be one of [EpisodeType.CONVERSATION, EpisodeType.DOCUMENT, "
                    + "EpisodeType.JSON]");
        }
        if (userId == null || userId.trim().isEmpty() || userId.trim().length() > userIdMaxLength) {
            throw validationError("user_id must be a string of length <= " + userIdMaxLength + " (preferably UUID4)");
        }
    }

    public static List<String> validateSearchInput(String query, Object userId, List<?> settings) {
        if (query == null || query.trim().isEmpty()) {
            throw validationError("query must be a non-empty string value");
        }
        List<?> rawUserIds;
        if (userId instanceof List<?> list) {
            rawUserIds = list;
        } else {
            List<Object> singleUserId = new ArrayList<>(1);
            singleUserId.add(userId);
            rawUserIds = singleUserId;
        }
        List<String> normalizedUserIds = new ArrayList<>(rawUserIds.size());
        for (Object rawUserId : rawUserIds) {
            if (!(rawUserId instanceof String uid) || uid.trim().isEmpty() || uid.length() > 32) {
                throw validationError("user_id must be a non-empty string of length <= 32 or a list of such strings");
            }
            normalizedUserIds.add(uid);
        }
        for (Object setting : settings) {
            if (!(setting instanceof Boolean)) {
                throw validationError("entity, relation, episode must be boolean values True or False");
            }
        }
        return List.copyOf(normalizedUserIds);
    }

    private static BaseError validationError(String message) {
        return ErrorHelper.buildError(
                StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                "store_type", STORE_TYPE,
                "error_msg", message);
    }
}
