package com.openjiuwen.core.controller;

import com.openjiuwen.core.common.concurrent.OpenJiuwenExecutors;
import com.openjiuwen.core.controller.schema.DataFrame;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControllerSchemaTest {
    @Test
    void controllerConfigValidationMatchesPythonConstraints() {
        ControllerConfig config = new ControllerConfig();

        assertThatThrownBy(() -> config.setScheduleInterval(0.09))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> config.setTaskTimeout(599.0))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> config.setTaskTimeout(0.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setEventQueueSize(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setEventTimeout(99.0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.setIntentConfidenceThreshold(1.2))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> config.getDefaultResponse().setType("xml"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dataframeVariantsExposeExpectedTypeNames() {
        DataFrame.TextDataFrame text = new DataFrame.TextDataFrame("hello");
        DataFrame.FileDataFrame file = new DataFrame.FileDataFrame("demo.txt", "text/plain", null, "file:///demo.txt");
        DataFrame.JsonDataFrame json = new DataFrame.JsonDataFrame(Map.of("ok", true));

        assertThat(text.getType()).isEqualTo("text");
        assertThat(text.text()).isEqualTo("hello");
        assertThat(file.getType()).isEqualTo("file");
        assertThat(file.name()).isEqualTo("demo.txt");
        assertThat(file.mimeType()).isEqualTo("text/plain");
        assertThat(file.uri()).isEqualTo("file:///demo.txt");
        assertThat(json.getType()).isEqualTo("json");
        assertThat(json.data()).containsEntry("ok", true);
    }
}
