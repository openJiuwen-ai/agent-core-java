/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.memory.graph.graph_memory;

import com.openjiuwen.core.common.exception.ErrorHelper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.memory.config.EpisodeType;

import java.util.*;

/**
 * Input validation for graph memory add and search operations.
 * <p>
 * Mirrors Python's {@code openjiuwen.core.memory.graph.graph_memory.validate_input}.
 */
public final class ValidateInput {

    private static final String STORE_TYPE = "graph mem store";

    private ValidateInput() {
    }

    /**
     * Validate input for add memory operation.
     *
     * @param userIdMaxLength  maximum allowed length for user_id
     * @param srcType          source type (must be EpisodeType enum)
     * @param userId           user identifier string
     * @param contentFmtKwargs optional content format kwargs (must be Map&lt;String, String&gt;)
     * @throws BaseError if validation fails
     */
    public static void validateAddMemoryInput(
            int userIdMaxLength,
            EpisodeType srcType,
            String userId,
            Map<String, String> contentFmtKwargs) {

        // Validate contentFmtKwargs
        if (contentFmtKwargs == null) {
            contentFmtKwargs = Collections.emptyMap();
        } else {
            if (contentFmtKwargs.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "store_type", STORE_TYPE,
                        "error_msg", "When supplied, content_fmt_kwargs must be of type dict[str, str] and not empty");
            }
        }

        // Validate all keys and values are non-empty strings
        for (Map.Entry<?, ?> entry : ((Map<?, ?>) contentFmtKwargs).entrySet()) {
            Object rawKey = entry.getKey();
            Object rawValue = entry.getValue();
            if (!(rawKey instanceof String k) || k.isEmpty()
                    || !(rawValue instanceof String v) || v.isEmpty()) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "store_type", STORE_TYPE,
                        "error_msg", "content_fmt_kwargs must have non-empty keys and values of string type");
            }
        }

        // Validate src_type
        if (srcType == null) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type", STORE_TYPE,
                    "error_msg", "src_type must be one of [EpisodeType.CONVERSATION, EpisodeType.DOCUMENT, EpisodeType.JSON]");
        }

        // Validate user_id
        if (userId == null || userId.strip().isEmpty() || userId.strip().length() > userIdMaxLength) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type", STORE_TYPE,
                    "error_msg", "user_id must be a string of length <= " + userIdMaxLength + " (preferably UUID4)");
        }
    }

    /**
     * Validate input for search operation.
     *
     * @param query    search query string (must be non-empty)
     * @param userId   user identifier (String or List of Strings)
     * @param settings list of three boolean flags (entity, relation, episode)
     * @return list of validated user_ids
     * @throws BaseError if validation fails
     */
    public static List<String> validateSearchInput(String query, Object userId, List<Boolean> settings) {
        // Validate query
        if (query == null || query.strip().isEmpty()) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type", STORE_TYPE,
                    "error_msg", "query must be a non-empty string value");
        }

        // Normalize userId to list
        List<String> userIdList = new ArrayList<>();
        if (userId instanceof String) {
            userIdList.add((String) userId);
        } else if (userId instanceof List) {
            for (Object uid : (List<?>) userId) {
                if (uid instanceof String) {
                    userIdList.add((String) uid);
                } else {
                    throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                            "store_type", STORE_TYPE,
                            "error_msg", "user_id must be a non-empty string of length <= 32 or a list of such strings");
                }
            }
        } else {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type", STORE_TYPE,
                    "error_msg", "user_id must be a non-empty string of length <= 32 or a list of such strings");
        }

        // Validate each user_id
        for (String uid : userIdList) {
            if (uid == null || uid.strip().isEmpty() || uid.strip().length() > 32) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "store_type", STORE_TYPE,
                        "error_msg", "user_id must be a non-empty string of length <= 32 or a list of such strings");
            }
        }

        // Validate settings
        if (settings == null || settings.size() != 3) {
            throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                    "store_type", STORE_TYPE,
                    "error_msg", "entity, relation, episode must be boolean values True or False");
        }
        for (Object rawSetting : settings) {
            if (!(rawSetting instanceof Boolean)) {
                throw ErrorHelper.buildError(StatusCode.MEMORY_STORE_VALIDATION_INVALID,
                        "store_type", STORE_TYPE,
                        "error_msg", "entity, relation, episode must be boolean values True or False");
            }
        }

        return userIdList;
    }
}
