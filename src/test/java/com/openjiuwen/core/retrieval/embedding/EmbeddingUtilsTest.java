package com.openjiuwen.core.retrieval.embedding;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EmbeddingUtilsTest {

    @Test
    void parseBase64EmbeddingAndSslAdapterMatchPythonHelpers() throws Exception {
        ByteBuffer buffer = ByteBuffer.allocate(Float.BYTES * 3).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putFloat(1.0f);
        buffer.putFloat(-2.5f);
        buffer.putFloat(3.25f);

        String encoded = Base64.getEncoder().encodeToString(buffer.array());
        SSLContext sslContext = SSLContext.getDefault();
        SSLContextAdapter adapter = new SSLContextAdapter(sslContext);

        assertThat(EmbeddingUtils.parseBase64Embedding(encoded)).isEqualTo(List.of(1.0f, -2.5f, 3.25f));
        assertThat(adapter.getSslContext()).isSameAs(sslContext);
        assertThat(adapter.apply(java.net.http.HttpClient.newBuilder())).isNotNull();
    }
}
