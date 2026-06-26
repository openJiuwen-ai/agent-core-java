/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.utils.SchemaUtils;

import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mirrors Python's {@code APIParamMapper} in
 * {@code openjiuwen/core/foundation/tool/service_api/api_param_mapper.py}.
 */
public class ApiParamMapper {

    private static final String LOCATION_KEY = "location";
    private static final String FORM_HANDLER_TYPE_KEY = "form_handler_type";
    private static final String DEFAULT_FORM_HANDLER_TYPE = "default";

    private final Map<String, Object> schema;
    private final Map<ApiParamLocation, Map<String, Object>> defaults;

    public ApiParamMapper(Map<String, Object> schema) {
        this(schema, null, null, null);
    }

    public ApiParamMapper(Class<?> schemaClass) {
        this(schemaClass, null, null, null);
    }

    public ApiParamMapper(
            Class<?> schemaClass,
            Map<String, Object> defaultQueries,
            Map<String, Object> defaultHeaders,
            Map<String, Object> defaultPaths
    ) {
        this(SchemaUtils.getSchemaDict(schemaClass), defaultQueries, defaultHeaders, defaultPaths);
    }

    public ApiParamMapper(
            Map<String, Object> schema,
            Map<String, Object> defaultQueries,
            Map<String, Object> defaultHeaders,
            Map<String, Object> defaultPaths
    ) {
        this.schema = schema;
        this.defaults = new EnumMap<>(ApiParamLocation.class);
        this.defaults.put(ApiParamLocation.QUERY, copyMap(defaultQueries));
        this.defaults.put(ApiParamLocation.HEADER, copyMap(defaultHeaders));
        this.defaults.put(ApiParamLocation.PATH, copyMap(defaultPaths));
    }

    public Map<ApiParamLocation, Map<String, Object>> map(Map<String, Object> inputs) {
        return map(inputs, ApiParamLocation.BODY);
    }

    @SuppressWarnings("unchecked")
    public Map<ApiParamLocation, Map<String, Object>> map(
            Map<String, Object> inputs,
            ApiParamLocation defaultLocation
    ) {
        Map<ApiParamLocation, Map<String, Object>> result = emptyBuckets();
        Map<String, Object> normalizedInputs = inputs == null ? Map.of() : inputs;

        if (schema == null) {
            result.get(defaultLocation).putAll(normalizedInputs);
        } else {
            Object propertiesObject = schema.getOrDefault("properties", Map.of());
            Map<String, Object> schemaProps = propertiesObject instanceof Map
                    ? (Map<String, Object>) propertiesObject
                    : Map.of();

            for (Map.Entry<String, Object> entry : schemaProps.entrySet()) {
                String paramName = entry.getKey();
                if (!normalizedInputs.containsKey(paramName)) {
                    continue;
                }

                Object paramSchemaObject = entry.getValue();
                if (!(paramSchemaObject instanceof Map<?, ?> paramSchema)) {
                    continue;
                }

                Object value = normalizedInputs.get(paramName);
                Object locationRaw = paramSchema.get(LOCATION_KEY);
                ApiParamLocation location = resolveLocation(locationRaw, defaultLocation);

                if (location == ApiParamLocation.FORM) {
                    if (isTruthy(value)) {
                        Object formHandlerValue = paramSchema.containsKey(FORM_HANDLER_TYPE_KEY)
                                ? paramSchema.get(FORM_HANDLER_TYPE_KEY)
                                : DEFAULT_FORM_HANDLER_TYPE;
                        String formHandlerType = String.valueOf(formHandlerValue);
                        result.get(ApiParamLocation.FORM).put(paramName, Map.of(
                                FORM_HANDLER_TYPE_KEY, formHandlerType,
                                "value", value
                        ));
                    }
                    continue;
                }

                result.get(location).put(paramName, value);
            }
        }

        for (ApiParamLocation location : new ApiParamLocation[]{
                ApiParamLocation.PATH, ApiParamLocation.QUERY, ApiParamLocation.HEADER}) {
            Map<String, Object> merged = copyMap(defaults.get(location));
            Map<String, Object> mapped = result.get(location);
            for (Map.Entry<String, Object> entry : mapped.entrySet()) {
                Object value = entry.getValue();
                if (value != null && !(value instanceof String text && text.isEmpty())) {
                    merged.put(entry.getKey(), value);
                }
            }
            result.put(location, merged);
        }

        return result;
    }

    private static ApiParamLocation resolveLocation(Object locationRaw, ApiParamLocation defaultLocation) {
        if (locationRaw instanceof String locationName) {
            return ApiParamLocation.fromString(locationName.toLowerCase());
        }
        if (locationRaw instanceof ApiParamLocation location) {
            return location;
        }
        return defaultLocation == ApiParamLocation.BODY ? ApiParamLocation.BODY : ApiParamLocation.QUERY;
    }

    private static Map<ApiParamLocation, Map<String, Object>> emptyBuckets() {
        Map<ApiParamLocation, Map<String, Object>> result = new EnumMap<>(ApiParamLocation.class);
        for (ApiParamLocation location : ApiParamLocation.values()) {
            result.put(location, new LinkedHashMap<>());
        }
        return result;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? new LinkedHashMap<>() : new LinkedHashMap<>(source);
    }

    private static boolean isTruthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return !text.isEmpty();
        }
        if (value instanceof Number number) {
            return number.doubleValue() != 0.0d;
        }
        if (value instanceof Collection<?> collection) {
            return !collection.isEmpty();
        }
        if (value instanceof Map<?, ?> map) {
            return !map.isEmpty();
        }
        return true;
    }
}
