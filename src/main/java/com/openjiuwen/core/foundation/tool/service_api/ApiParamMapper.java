package com.openjiuwen.core.foundation.tool.service_api;

import com.openjiuwen.core.common.utils.SchemaUtils;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * API参数映射器
 * 
 * <p>根据schema定义将输入参数映射到相应的API位置（query、path、body、header）。
 * 该类处理参数分发并提供默认值合并功能。
 * 
 * @author OpenJiuwen
 * @since 2026-01-29
 */
public class ApiParamMapper {
    
    /**
     * Schema中指定位置的键名
     */
    private static final String LOCATION_KEY = "location";
    
    private final Object schema;
    private final Map<ApiParamLocation, Map<String, Object>> defaults;
    
    /**
     * 构造参数映射器
     * 
     * @param schema OpenAPI schema定义（Map或Pydantic类型）
     * @param defaultQueries 默认query参数
     * @param defaultHeaders 默认header参数
     * @param defaultPaths 默认path参数
     */
    public ApiParamMapper(
            Object schema,
            Map<String, Object> defaultQueries,
            Map<String, Object> defaultHeaders,
            Map<String, Object> defaultPaths) {
        this.schema = schema;
        this.defaults = new EnumMap<>(ApiParamLocation.class);
        this.defaults.put(ApiParamLocation.QUERY, defaultQueries != null ? defaultQueries : new HashMap<>());
        this.defaults.put(ApiParamLocation.HEADER, defaultHeaders != null ? defaultHeaders : new HashMap<>());
        this.defaults.put(ApiParamLocation.PATH, defaultPaths != null ? defaultPaths : new HashMap<>());
    }
    
    /**
     * 将输入参数映射到各个API位置
     * 
     * @param inputs 输入参数字典
     * @param defaultLocation 默认位置（用于schema中未明确指定位置的参数）
     * @return 映射结果：APIParamLocation → 参数字典
     */
    public Map<ApiParamLocation, Map<String, Object>> map(
            Map<String, Object> inputs,
            ApiParamLocation defaultLocation) {
        
        Map<ApiParamLocation, Map<String, Object>> result = new EnumMap<>(ApiParamLocation.class);
        
        // 初始化所有位置的Map
        for (ApiParamLocation location : ApiParamLocation.values()) {
            result.put(location, new HashMap<>());
        }
        
        if (schema == null) {
            // 如果没有schema，所有参数放到默认位置
            result.put(defaultLocation, new HashMap<>(inputs));
        } else {
            // 根据schema分发参数
            Map<String, Object> schemaMap = getSchemaMap();
            @SuppressWarnings("unchecked")
            Map<String, Object> properties = (Map<String, Object>) schemaMap.get("properties");
            
            if (properties != null) {
                for (Map.Entry<String, Object> entry : inputs.entrySet()) {
                    String paramName = entry.getKey();
                    Object paramValue = entry.getValue();
                    
                    if (properties.containsKey(paramName)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> paramSchema = (Map<String, Object>) properties.get(paramName);
                        String locationStr = (String) paramSchema.getOrDefault(LOCATION_KEY, defaultLocation.getValue());
                        ApiParamLocation location = ApiParamLocation.fromValue(locationStr);
                        result.get(location).put(paramName, paramValue);
                    } else {
                        // 参数不在schema中，放到默认位置
                        result.get(defaultLocation).put(paramName, paramValue);
                    }
                }
            }
        }
        
        // 合并默认值（输入值优先）
        for (ApiParamLocation location : new ApiParamLocation[]{
            ApiParamLocation.PATH, 
            ApiParamLocation.QUERY, 
            ApiParamLocation.HEADER
        }) {
            Map<String, Object> defaultValues = defaults.get(location);
            Map<String, Object> currentValues = result.get(location);
            
            // 创建新Map，先放默认值，再放当前值（覆盖）
            Map<String, Object> merged = new HashMap<>(defaultValues);
            merged.putAll(currentValues);
            result.put(location, merged);
        }
        
        return result;
    }
    
    /**
     * 获取Schema的Map表示
     * 
     * @return Schema Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> getSchemaMap() {
        if (schema instanceof Map) {
            return (Map<String, Object>) schema;
        }
        // 如果是Pydantic类型（暂不支持，返回空Map）
        return new HashMap<>();
    }
}

