package org.example.nlp2dsl2sql.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rerank 服务客户端。
 */
@Slf4j
@Component
public class RerankClient {
    private final RestClient rerankClient;

    public RerankClient(
            @org.springframework.beans.factory.annotation.Value(
                    "${rerank.base-url:http://localhost:8083}") String baseUrl) {
        this.rerankClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    @SuppressWarnings("unchecked")
    public List<Double> rerank(String query, List<String> documents) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("query", query);
            body.put("documents", documents);
            Map<String, Object> resp = rerankClient.post()
                    .uri("/rerank")
                    .body(body)
                    .retrieve()
                    .body(Map.class);
            if (resp != null && resp.containsKey("scores")) {
                return ((List<Number>) resp.get("scores")).stream()
                        .map(Number::doubleValue)
                        .collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.warn("Rerank 调用失败，跳过精排: {}", e.getMessage());
        }
        return documents.stream().map(d -> 0.0).collect(Collectors.toList());
    }
}
