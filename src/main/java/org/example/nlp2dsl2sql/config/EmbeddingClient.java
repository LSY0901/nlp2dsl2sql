package org.example.nlp2dsl2sql.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Embedding 客户端：调用外部 OpenAI 兼容的 embedding 服务。
 * 替代 Spring AI 的 EmbeddingModel，用 RestClient 直接调用。
 */
@Slf4j
@Component
public class EmbeddingClient {

    private final RestClient embeddingClient;

    @Value("${embedding.model:bge-m3}")
    private String embeddingModel;

    public EmbeddingClient(
            @Value("${embedding.base-url:http://localhost:8082}") String baseUrl) {
        this.embeddingClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * 将文本向量化，返回 float[]。
     *
     * @param text 输入文本
     * @return 向量
     */
    public float[] embed(String text) {
        @SuppressWarnings("unchecked")
        Map<String, Object> resp = embeddingClient.post()
                .uri("/v1/embeddings")
                .body(Map.of("model", embeddingModel, "input", text))
                .retrieve()
                .body(Map.class);
        if (resp != null && resp.containsKey("data")) {
            List<?> data = (List<?>) resp.get("data");
            if (data != null && !data.isEmpty()) {
                @SuppressWarnings("unchecked")
                List<Number> embedding = (List<Number>) ((Map<String, Object>) data.get(0)).get("embedding");
                float[] result = new float[embedding.size()];
                for (int i = 0; i < embedding.size(); i++) {
                    result[i] = embedding.get(i).floatValue();
                }
                return result;
            }
        }
        throw new RuntimeException("Embedding 服务返回为空");
    }

    /**
     * 将 float[] 转为 pgvector 文本格式: [x,x,x]
     */
    public static String toVectorStr(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vec[i]);
        }
        sb.append(']');
        return sb.toString();
    }
}
