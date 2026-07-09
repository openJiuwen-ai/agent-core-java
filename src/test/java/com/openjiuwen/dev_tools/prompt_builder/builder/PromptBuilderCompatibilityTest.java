
package com.openjiuwen.dev_tools.prompt_builder.builder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.openjiuwen.core.common.exception.BaseError;
import com.openjiuwen.core.common.exception.StatusCode;
import com.openjiuwen.core.foundation.llm.schema.ModelClientConfig;
import com.openjiuwen.core.foundation.llm.schema.ModelRequestConfig;
import com.openjiuwen.core.foundation.prompt.PromptTemplate;

import org.junit.jupiter.api.Test;

import java.util.List;

class PromptBuilderCompatibilityTest {
    @Test
    void promptTemplateUtilitiesRetainLegacyAccessPatterns() {
        Object selectedTemplate = PromptTemplateUtils.selectTemplate(null);
        PromptTemplate template = PromptTemplate.builder().content("hello world").build();

        assertThat(selectedTemplate).isEqualTo(PromptTemplatesZh.class);
        assertThat(PromptTemplateUtils.getTemplateMap()).containsKeys("zh-CN", "en-US");
        assertThat(PromptTemplateUtils.getTemplate(selectedTemplate, "PROMPT_FEEDBACK_GENERAL_TEMPLATE")).isNotNull();
        assertThat(PromptTemplateUtils.getStringPrompt(template)).isEqualTo("hello world");
        assertThatThrownBy(() -> PromptTemplateUtils.getStringPrompt(123)).isInstanceOf(BaseError.class)
                .hasMessageContaining("Prompt type <class 'int'> is not supported");
    }

    @Test
    void metaTemplateBuilderLegacyRegistrationHelpersStillWork() {
        MetaTemplateBuilder builder = new MetaTemplateBuilder(modelConfig(), clientConfig());
        PromptTemplate customTemplate = PromptTemplate.builder().content("custom {{instruction}}").build();

        builder.registerMetaTemplate("demo", customTemplate);

        assertThat(builder.getMetaTemplate("META_TEMPLATE_demo")).isInstanceOf(PromptTemplate.class);
        assertThat(builder.popMetaTemplate("META_TEMPLATE_demo")).isInstanceOf(PromptTemplate.class);
        assertThat(builder.getMetaTemplate("META_TEMPLATE_demo")).isNull();
        assertThatThrownBy(() -> builder.registerMetaTemplate("invalid", 1)).isInstanceOf(BaseError.class)
                .satisfies(error -> assertThat(((BaseError) error).getCode())
                        .isEqualTo(StatusCode.TOOLCHAIN_META_TEMPLATE_EXECUTION_ERROR.code()));
    }

    @Test
    void feedbackAndBadCaseBuildersFailBeforeNetworkWhenInputsAreInvalid() {
        FeedbackPromptBuilder feedbackBuilder = new FeedbackPromptBuilder(modelConfig(), clientConfig());
        BadCasePromptBuilder badCaseBuilder = new BadCasePromptBuilder(modelConfig(), clientConfig());

        Throwable feedbackError = catchThrowable(() -> feedbackBuilder.build(null, "feedback").join());
        Throwable badCaseError = catchThrowable(() -> badCaseBuilder.build("prompt", List.of()).join());

        Throwable feedbackRootCause = rootCause(feedbackError);
        Throwable badCaseRootCause = rootCause(badCaseError);

        assertThat(feedbackRootCause).isInstanceOf(BaseError.class)
                .hasMessageContaining("prompt or feedback cannot be None");
        assertThat(((BaseError) feedbackRootCause).getCode())
                .isEqualTo(StatusCode.TOOLCHAIN_FEEDBACK_TEMPLATE_EXECUTION_ERROR.code());
        assertThat(badCaseRootCause).isInstanceOf(BaseError.class).hasMessageContaining("The cases cannot be empty");
        assertThat(((BaseError) badCaseRootCause).getCode())
                .isEqualTo(StatusCode.TOOLCHAIN_BAD_CASE_TEMPLATE_EXECUTION_ERROR.code());
    }

    private static ModelRequestConfig modelConfig() {
        return ModelRequestConfig.builder().modelName("test-model").build();
    }

    private static ModelClientConfig clientConfig() {
        return ModelClientConfig.builder().clientProvider("OpenAI").apiKey("test-key").apiBase("https://example.com/v1")
                .verifySsl(false).build();
    }

    private static Throwable rootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }
}