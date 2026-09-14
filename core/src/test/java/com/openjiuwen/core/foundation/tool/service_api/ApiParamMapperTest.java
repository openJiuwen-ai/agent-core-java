/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2026-2026. All rights reserved.
 */

package com.openjiuwen.core.foundation.tool.service_api;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiParamMapperTest {

    @Test
    void mapRoutesExplicitStringLocationsIntoSeparateBuckets() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "q", Map.of("location", "query"),
                        "id", Map.of("location", "path"),
                        "payload", Map.of("location", "body"),
                        "token", Map.of("location", "header")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of(
                "q", "hello",
                "id", "42",
                "payload", Map.of("x", 1),
                "token", "abc"
        ));

        assertThat(mapped.get(ApiParamLocation.QUERY)).containsEntry("q", "hello");
        assertThat(mapped.get(ApiParamLocation.PATH)).containsEntry("id", "42");
        assertThat(mapped.get(ApiParamLocation.BODY)).containsEntry("payload", Map.of("x", 1));
        assertThat(mapped.get(ApiParamLocation.HEADER)).containsEntry("token", "abc");
    }

    @Test
    void mapWrapsTruthyFormValuesWithHandlerMetadata() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "upload", Map.of("location", "form", "form_handler_type", "file"),
                        "ignored", Map.of("location", "form")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of(
                "upload", "bytes",
                "ignored", ""
        ));

        assertThat(mapped.get(ApiParamLocation.FORM)).containsEntry(
                "upload",
                Map.of("form_handler_type", "file", "value", "bytes")
        );
        assertThat(mapped.get(ApiParamLocation.FORM)).doesNotContainKey("ignored");
    }

    @Test
    void mapSupportsSingleFormParameterMapping() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "file", Map.of("location", "form", "form_handler_type", "file")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(
                Map.of("file", "http://example.com/document.pdf")
        );

        assertThat(mapped.get(ApiParamLocation.FORM)).containsEntry(
                "file",
                Map.of("form_handler_type", "file", "value", "http://example.com/document.pdf")
        );
    }

    @Test
    void mapSupportsMultipleFormParametersAndBodyTogether() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "file", Map.of("location", "form", "form_handler_type", "file"),
                        "image", Map.of("location", "form", "form_handler_type", "file"),
                        "name", Map.of("location", "body")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of(
                "file", "http://example.com/document.pdf",
                "image", "http://example.com/image.png",
                "name", "test_document"
        ));

        assertThat(mapped.get(ApiParamLocation.FORM)).isEqualTo(Map.of(
                "file", Map.of("form_handler_type", "file", "value", "http://example.com/document.pdf"),
                "image", Map.of("form_handler_type", "file", "value", "http://example.com/image.png")
        ));
        assertThat(mapped.get(ApiParamLocation.BODY)).containsEntry("name", "test_document");
    }

    @Test
    void mapSupportsMixedFormAndRegularParameters() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "document", Map.of("location", "form", "form_handler_type", "file"),
                        "title", Map.of("location", "body"),
                        "userId", Map.of("location", "query"),
                        "authToken", Map.of("location", "header"),
                        "version", Map.of("location", "path")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of(
                "document", "http://example.com/doc.pdf",
                "title", "My Document",
                "userId", 123,
                "authToken", "token123",
                "version", "v1"
        ));

        assertThat(mapped.get(ApiParamLocation.FORM)).containsEntry(
                "document",
                Map.of("form_handler_type", "file", "value", "http://example.com/doc.pdf")
        );
        assertThat(mapped.get(ApiParamLocation.BODY)).containsEntry("title", "My Document");
        assertThat(mapped.get(ApiParamLocation.QUERY)).containsEntry("userId", 123);
        assertThat(mapped.get(ApiParamLocation.HEADER)).containsEntry("authToken", "token123");
        assertThat(mapped.get(ApiParamLocation.PATH)).containsEntry("version", "v1");
    }

    @Test
    void mapUsesDefaultFormHandlerTypeWhenSchemaOmitsIt() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "file", Map.of("location", "form")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(
                Map.of("file", "http://example.com/file.pdf")
        );

        assertThat(mapped.get(ApiParamLocation.FORM)).containsEntry(
                "file",
                Map.of("form_handler_type", "default", "value", "http://example.com/file.pdf")
        );
    }

    @Test
    void mapPreservesCustomFormHandlerType() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "data", Map.of("location", "form", "form_handler_type", "custom")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of("data", "custom_value"));

        assertThat(mapped.get(ApiParamLocation.FORM)).containsEntry(
                "data",
                Map.of("form_handler_type", "custom", "value", "custom_value")
        );
    }

    @Test
    void mapPreservesEmptyFormHandlerTypeWhenSchemaExplicitlySetsIt() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "file", Map.of("location", "form", "form_handler_type", "")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(
                Map.of("file", "http://example.com/file.pdf")
        );

        assertThat(mapped.get(ApiParamLocation.FORM)).containsEntry(
                "file",
                Map.of("form_handler_type", "", "value", "http://example.com/file.pdf")
        );
    }

    @Test
    void mapDropsFormParamWhenValueIsNull() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "formField", Map.of("location", "form", "form_handler_type", "default")
                )
        ));
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("formField", null);

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(inputs);

        assertThat(mapped.get(ApiParamLocation.FORM)).isEmpty();
    }

    @Test
    void mapDropsFormParamWhenValueIsEmptyString() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "formField", Map.of("location", "form", "form_handler_type", "default")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of("formField", ""));

        assertThat(mapped.get(ApiParamLocation.FORM)).isEmpty();
    }

    @Test
    void mapKeepsFormParamWhenValueIsPresent() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "formField", Map.of("location", "form", "form_handler_type", "default")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of("formField", "test_value"));

        assertThat(mapped.get(ApiParamLocation.FORM)).containsEntry(
                "formField",
                Map.of("form_handler_type", "default", "value", "test_value")
        );
    }

    @Test
    void mapLeavesFormBucketEmptyWhenInputsDoNotContainFormParameter() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "file", Map.of("location", "form", "form_handler_type", "file"),
                        "name", Map.of("location", "body")
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(Map.of("name", "test"));

        assertThat(mapped.get(ApiParamLocation.FORM)).isEmpty();
        assertThat(mapped.get(ApiParamLocation.BODY)).containsEntry("name", "test");
    }

    @Test
    void mapDefaultsUnknownLocationsToBodyWhenBodyIsRequestedDefault() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "bodyOnly", Map.of()
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(
                Map.of("bodyOnly", 7),
                ApiParamLocation.BODY
        );

        assertThat(mapped.get(ApiParamLocation.BODY)).containsEntry("bodyOnly", 7);
        assertThat(mapped.get(ApiParamLocation.QUERY)).isEmpty();
    }

    @Test
    void mapDefaultsUnknownLocationsToQueryWhenNonBodyDefaultIsRequested() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "fallback", Map.of()
                )
        ));

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(
                Map.of("fallback", "value"),
                ApiParamLocation.HEADER
        );

        assertThat(mapped.get(ApiParamLocation.QUERY)).containsEntry("fallback", "value");
        assertThat(mapped.get(ApiParamLocation.HEADER)).isEmpty();
    }

    @Test
    void mapMergesDefaultsButKeepsDefaultsWhenInputIsNullOrEmptyString() {
        ApiParamMapper mapper = new ApiParamMapper(
                Map.of(
                        "properties", Map.of(
                                "pathId", Map.of("location", "path"),
                                "headerAuth", Map.of("location", "header"),
                                "queryPage", Map.of("location", "query")
                        )
                ),
                Map.of("queryPage", 5),
                Map.of("headerAuth", "default-token"),
                Map.of("pathId", "default-id")
        );
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("pathId", "");
        inputs.put("headerAuth", null);
        inputs.put("queryPage", 9);

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(inputs);

        assertThat(mapped.get(ApiParamLocation.PATH)).containsEntry("pathId", "default-id");
        assertThat(mapped.get(ApiParamLocation.HEADER)).containsEntry("headerAuth", "default-token");
        assertThat(mapped.get(ApiParamLocation.QUERY)).containsEntry("queryPage", 9);
    }

    @Test
    void mapPlacesEverythingIntoDefaultBucketWhenSchemaIsNull() {
        ApiParamMapper mapper = new ApiParamMapper((Map<String, Object>) null);

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(
                Map.of("value", 1),
                ApiParamLocation.HEADER
        );

        assertThat(mapped.get(ApiParamLocation.HEADER)).containsEntry("value", 1);
        assertThat(mapped.get(ApiParamLocation.BODY)).isEmpty();
    }

    @Test
    void classSchemaConstructorUsesSchemaUtilsGeneratedProperties() {
        ApiParamMapper mapper = new ApiParamMapper(DemoParams.class);

        Map<ApiParamLocation, Map<String, Object>> mapped = mapper.map(
                Map.of("query", "v", "count", 2),
                ApiParamLocation.BODY
        );

        assertThat(mapped.get(ApiParamLocation.BODY)).containsEntry("query", "v");
        assertThat(mapped.get(ApiParamLocation.BODY)).containsEntry("count", 2);
    }

    @Test
    void invalidExplicitLocationFailsFast() {
        ApiParamMapper mapper = new ApiParamMapper(Map.of(
                "properties", Map.of(
                        "value", Map.of("location", "invalid")
                )
        ));

        assertThatThrownBy(() -> mapper.map(Map.of("value", "x")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid");
    }

    private static final class DemoParams {
        private String query;
        private int count;
        private List<String> tags;
    }
}
