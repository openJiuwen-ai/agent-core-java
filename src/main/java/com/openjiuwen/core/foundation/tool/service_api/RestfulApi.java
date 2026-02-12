package com.openjiuwen.core.foundation.tool.service_api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.common.exception.ErrorBuilder;
import com.openjiuwen.core.common.security.SslUtils;
import com.openjiuwen.core.common.security.UrlUtils;
import com.openjiuwen.core.common.utils.SchemaUtils;
import com.openjiuwen.core.foundation.tool.Tool;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.ssl.SSLConnectionSocketFactory;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * RESTful API工具
 * 
 * <p>封装HTTP请求功能，支持GET/POST方法，支持SSL配置和代理。
 * 
 * @author OpenJiuwen
 * @since 2026-01-30
 */
public class RestfulApi extends Tool<Map<String, Object>, Object> {
    
    private static final String RESTFUL_SSL_VERIFY = "RESTFUL_SSL_VERIFY";
    private static final String RESTFUL_SSL_CERT = "RESTFUL_SSL_CERT";
    
    private final String url;
    private final String method;
    private final float timeout;
    private final int maxResponseByteSize;
    private final ApiParamMapper apiParamMapper;
    private final ObjectMapper objectMapper;
    
    /**
     * 构造RESTful API工具
     * 
     * @param card RESTful API卡片
     */
    public RestfulApi(RestfulApiCard card) {
        super(card);
        this.url = card.getUrl();
        this.method = card.getMethod();
        this.timeout = card.getTimeout();
        this.maxResponseByteSize = card.getMaxResponseByteSize();
        this.apiParamMapper = new ApiParamMapper(
            card.getInputParams(),
            card.getQueries(),
            card.getHeaders(),
            card.getPaths()
        );
        this.objectMapper = new ObjectMapper();
    }
    
    @Override
    public CompletableFuture<Object> invoke(Map<String, Object> inputs, Map<String, Object> kwargs) {
        return CompletableFuture.supplyAsync(() -> {
            float finalTimeout = this.timeout;
            try {
                // 格式化输入参数
                Map<String, Object> formattedInputs = inputs;
                if (card.getInputParams() != null) {
                    boolean skipNoneValue = (boolean) kwargs.getOrDefault("skip_none_value", false);
                    boolean skipValidate = (boolean) kwargs.getOrDefault("skip_inputs_validate", false);
                    
                    Object formatted = SchemaUtils.formatWithSchema(
                        inputs,
                        card.getInputParams(),
                        skipNoneValue,
                        skipValidate
                    );
                    if (formatted instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> formattedMap = (Map<String, Object>) formatted;
                        formattedInputs = formattedMap;
                    }
                }
                
                // 映射参数到各个位置
                Map<ApiParamLocation, Map<String, Object>> mapResults = apiParamMapper.map(
                    formattedInputs,
                    ApiParamLocation.BODY
                );
                
                // 获取自定义timeout
                if (kwargs.containsKey("timeout")) {
                    finalTimeout = ((Number) kwargs.get("timeout")).floatValue();
                }
                
                // 获取自定义响应大小限制
                int finalMaxSize = kwargs.containsKey("max_response_byte_size") ?
                    ((Number) kwargs.get("max_response_byte_size")).intValue() :
                    this.maxResponseByteSize;
                
                // 执行请求
                return executeRequest(mapResults, finalTimeout, finalMaxSize);
                
            } catch (TimeoutException e) {
                Map<String, Object> params = Map.of(
                    "interface", "invoke",
                    "timeout", finalTimeout,
                    "card", card.toString()
                );
                throw ErrorBuilder.build(StatusCode.TOOL_RESTFUL_API_TIMEOUT, null, null, e, params);
            } catch (IOException e) {
                Map<String, Object> params = Map.of(
                    "interface", "invoke",
                    "reason", e.getMessage(),
                    "card", card.toString()
                );
                throw ErrorBuilder.build(StatusCode.TOOL_RESTFUL_API_EXECUTION_ERROR, null, null, e, params);
            } catch (Exception e) {
                if (e instanceof com.openjiuwen.core.common.exception.JiuWenBaseException) {
                    throw (com.openjiuwen.core.common.exception.JiuWenBaseException) e;
                }
                Map<String, Object> params = Map.of(
                    "interface", "invoke",
                    "reason", e.getMessage(),
                    "card", card.toString()
                );
                throw ErrorBuilder.build(StatusCode.TOOL_RESTFUL_API_EXECUTION_ERROR, null, null, e, params);
            }
        });
    }
    
    @Override
    public Stream<Object> stream(Map<String, Object> inputs, Map<String, Object> kwargs) {
        Map<String, Object> params = Map.of("card", card.toString());
        throw ErrorBuilder.build(StatusCode.TOOL_STREAM_NOT_SUPPORTED, null, null, null, params);
    }
    
    /**
     * 执行HTTP请求
     */
    private Object executeRequest(
            Map<ApiParamLocation, Map<String, Object>> mapResults,
            float timeout,
            int maxResponseSize) throws IOException, TimeoutException {
        
        // 构建URL（包含path和query参数）
        String finalUrl = buildUrl(mapResults);
        
        // 创建HTTP客户端
        CloseableHttpClient httpClient = createHttpClient(timeout);
        
        try {
            // 创建请求
            HttpUriRequestBase request = createRequest(finalUrl, mapResults);
            
            // 执行请求
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                int statusCode = response.getCode();
                
                // 检查HTTP状态码
                if (statusCode >= 400) {
                    Map<String, Object> params = Map.of(
                        "interface", "invoke",
                        "code", statusCode,
                        "reason", response.getReasonPhrase(),
                        "card", card.toString()
                    );
                    throw ErrorBuilder.build(StatusCode.TOOL_RESTFUL_API_RESPONSE_ERROR, null, null, null, params);
                }
                
                // 读取响应
                return parseResponse(response, maxResponseSize);
            }
        } finally {
            httpClient.close();
        }
    }
    
    /**
     * 构建完整URL（包含path参数替换和query参数）
     */
    private String buildUrl(Map<ApiParamLocation, Map<String, Object>> mapResults) {
        String finalUrl = this.url;
        
        // 替换path参数（支持<>或{}作为占位符）
        Map<String, Object> pathParams = mapResults.get(ApiParamLocation.PATH);
        if (pathParams != null && !pathParams.isEmpty()) {
            for (Map.Entry<String, Object> entry : pathParams.entrySet()) {
                String value = String.valueOf(entry.getValue());
                // 先尝试<>格式（避免URI解析器拒绝{}）
                String placeholder1 = "<" + entry.getKey() + ">";
                if (finalUrl.contains(placeholder1)) {
                    finalUrl = finalUrl.replace(placeholder1, value);
                } else {
                    // 回退到{}格式
                    String placeholder2 = "{" + entry.getKey() + "}";
                    finalUrl = finalUrl.replace(placeholder2, value);
                }
            }
        }
        
        // 添加query参数
        Map<String, Object> queryParams = mapResults.get(ApiParamLocation.QUERY);
        if (queryParams != null && !queryParams.isEmpty()) {
            StringBuilder queryString = new StringBuilder();
            for (Map.Entry<String, Object> entry : queryParams.entrySet()) {
                if (queryString.length() > 0) {
                    queryString.append("&");
                }
                queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8));
                queryString.append("=");
                queryString.append(URLEncoder.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8));
            }
            finalUrl = finalUrl + "?" + queryString;
        }
        
        return finalUrl;
    }
    
    /**
     * 创建HTTP客户端
     */
    private CloseableHttpClient createHttpClient(float timeout) {
        try {
            // SSL配置
            SslUtils.SslConfig sslConfig = SslUtils.getSslConfig(
                RESTFUL_SSL_VERIFY,
                RESTFUL_SSL_CERT,
                java.util.List.of("false"),
                this.url.startsWith("https")
            );
            
            // 创建连接管理器
            var connectionManagerBuilder = PoolingHttpClientConnectionManagerBuilder.create();
            
            if (sslConfig.isSslVerify()) {
                SSLContext sslContext = SslUtils.createStrictSslContext(sslConfig.getSslCert());
                SSLConnectionSocketFactory sslSocketFactory = new SSLConnectionSocketFactory(sslContext);
                connectionManagerBuilder.setSSLSocketFactory(sslSocketFactory);
            }
            
            // 请求配置（超时）
            RequestConfig requestConfig = RequestConfig.custom()
                .setResponseTimeout(Timeout.ofSeconds((long) timeout))
                .setConnectionRequestTimeout(Timeout.ofSeconds((long) timeout))
                .build();
            
            // 创建HTTP客户端Builder
            var httpClientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManagerBuilder.build())
                .setDefaultRequestConfig(requestConfig);
            
            // 代理配置
            String proxyUrl = UrlUtils.getGlobalProxyUrl(this.url);
            if (proxyUrl != null && !proxyUrl.isEmpty()) {
                try {
                    org.apache.hc.core5.http.HttpHost proxy = org.apache.hc.core5.http.HttpHost.create(proxyUrl);
                    httpClientBuilder.setProxy(proxy);
                } catch (Exception e) {
                    // 如果代理配置失败，记录警告但继续执行
                    System.err.println("Failed to configure proxy: " + e.getMessage());
                }
            }
            
            return httpClientBuilder.build();
                
        } catch (Exception e) {
            throw new RuntimeException("Failed to create HTTP client", e);
        }
    }
    
    /**
     * 创建HTTP请求
     */
    private HttpUriRequestBase createRequest(
            String url,
            Map<ApiParamLocation, Map<String, Object>> mapResults) throws IOException {
        
        HttpUriRequestBase request;
        Map<String, Object> bodyParams = mapResults.get(ApiParamLocation.BODY);
        
        if ("GET".equals(this.method)) {
            request = new HttpGet(URI.create(url));
            // GET请求的body参数作为query参数（已在buildUrl中处理）
        } else {
            HttpPost postRequest = new HttpPost(URI.create(url));
            // POST请求的body参数作为JSON body
            if (bodyParams != null && !bodyParams.isEmpty()) {
                String jsonBody = objectMapper.writeValueAsString(bodyParams);
                postRequest.setEntity(new StringEntity(jsonBody, org.apache.hc.core5.http.ContentType.APPLICATION_JSON));
            }
            request = postRequest;
        }
        
        // 设置headers
        Map<String, Object> headers = mapResults.get(ApiParamLocation.HEADER);
        if (headers != null && !headers.isEmpty()) {
            for (Map.Entry<String, Object> entry : headers.entrySet()) {
                request.setHeader(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        
        return request;
    }
    
    /**
     * 解析响应
     */
    private Object parseResponse(CloseableHttpResponse response, int maxSize) throws IOException {
        byte[] content = EntityUtils.toByteArray(response.getEntity());
        
        // 检查响应大小
        if (content.length > maxSize) {
            Map<String, Object> params = Map.of(
                "interface", "invoke",
                "max_length", maxSize,
                "actual_length", content.length,
                "card", card.toString()
            );
            throw ErrorBuilder.build(StatusCode.TOOL_RESTFUL_API_RESPONSE_SIZE_EXCEED_LIMIT, null, null, null, params);
        }
        
        // 解析JSON响应
        String responseText = new String(content, StandardCharsets.UTF_8);
        try {
            return objectMapper.readValue(responseText, Object.class);
        } catch (Exception e) {
            // 如果不是JSON，返回原始字符串
            return responseText;
        }
    }
}

