/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Maps input parameters to their corresponding API locations (query, path, body, header).
 * <p>
 * Handles parameter distribution based on schema definitions and provides
 * default value merging for query, path, and header parameters.
 * <p>
 * Mirrors Python's {@code APIParamMapper}.
 */
public class ApiParamMapper {

    private static final String LOCATION_KEY = "location";
    private static final String PROPERTIES_KEY = "properties";

    private final Map<String, Object> schema;
    private final Map<ApiParamLocation, Map<String, Object>> defaults;

    /**
     * Construct a new API parameter mapper.
     *
     * @param schema         schema defining parameter locations and properties
     * @param defaultQueries default query parameters (merged with inputs)
     * @param defaultHeaders default header parameters (merged with inputs)
     * @param defaultPaths   default path parameters (merged with inputs)
     */
    public ApiParamMapper(Map<String, Object> schema,
                          Map<String, Object> defaultQueries,
                          Map<String, Object> defaultHeaders,
                          Map<String, Object> defaultPaths) {
        this.schema = schema;
        this.defaults = new EnumMap<>(ApiParamLocation.class);
        this.defaults.put(ApiParamLocation.QUERY, defaultQueries != null ? defaultQueries : Map.of());
        this.defaults.put(ApiParamLocation.HEADER, defaultHeaders != null ? defaultHeaders : Map.of());
        this.defaults.put(ApiParamLocation.PATH, defaultPaths != null ? defaultPaths : Map.of());
    }

    /**
     * Map input parameters to their respective API locations.
     *
     * @param inputs          input parameters to be mapped
     * @param defaultLocation default location for parameters without explicit location in schema
     * @return map from {@link ApiParamLocation} to parameters for that location
     */
    @SuppressWarnings("unchecked")
    public Map<ApiParamLocation, Map<String, Object>> map(Map<String, Object> inputs,
                                                          ApiParamLocation defaultLocation) {
        Map<ApiParamLocation, Map<String, Object>> result;

        if (schema == null) {
            result = new EnumMap<>(ApiParamLocation.class);
            result.put(defaultLocation, inputs != null ? inputs : Map.of());
        } else {
            result = new EnumMap<>(ApiParamLocation.class);
            for (ApiParamLocation loc : ApiParamLocation.values()) {
                result.put(loc, new LinkedHashMap<>());
            }

            Map<String, Object> properties = (Map<String, Object>) schema.get(PROPERTIES_KEY);
            if (properties != null && inputs != null) {
                for (var entry : properties.entrySet()) {
                    String paramName = entry.getKey();
                    if (!inputs.containsKey(paramName)) {
                        continue;
                    }
                    Map<String, Object> paramSchema = (Map<String, Object>) entry.getValue();
                    Object locationRaw = paramSchema.get(LOCATION_KEY);
                    ApiParamLocation location;
                    if (locationRaw instanceof String locStr) {
                        location = ApiParamLocation.fromString(locStr);
                    } else if (locationRaw instanceof ApiParamLocation loc) {
                        location = loc;
                    } else {
                        location = defaultLocation;
                    }
                    result.computeIfAbsent(location, k -> new LinkedHashMap<>())
                            .put(paramName, inputs.get(paramName));
                }
            }
        }

        // Merge defaults: input values override default values
        for (ApiParamLocation loc : new ApiParamLocation[]{
                ApiParamLocation.PATH, ApiParamLocation.QUERY, ApiParamLocation.HEADER}) {
            Map<String, Object> merged = new LinkedHashMap<>(defaults.getOrDefault(loc, Map.of()));
            merged.putAll(result.getOrDefault(loc, Map.of()));
            result.put(loc, merged);
        }

        return Collections.unmodifiableMap(result);
    }
}
