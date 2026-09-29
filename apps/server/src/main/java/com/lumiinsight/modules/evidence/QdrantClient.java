package com.lumiinsight.modules.evidence;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class QdrantClient {

    private static final Logger log = LoggerFactory.getLogger(QdrantClient.class);
    private static final String COLLECTION = "lumi_reviews";

    private final RestClient restClient;

    public QdrantClient(
            @Value("${lumiinsight.qdrant.host:127.0.0.1}") String host,
            @Value("${lumiinsight.qdrant.port:6333}") int port
    ) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.restClient = RestClient.builder()
                .baseUrl("http://" + host + ":" + port)
                .requestFactory(factory)
                .build();
    }

    public boolean upsert(int dim, List<Map<String, Object>> points) {
        if (points == null || points.isEmpty()) {
            return true;
        }
        try {
            if (!ensureCollection(dim)) {
                return false;
            }
            restClient.put()
                    .uri("/collections/{name}/points?wait=true", COLLECTION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("points", points))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("写入向量库失败：{}", e.getMessage());
            return false;
        }
    }

    private boolean ensureCollection(int dim) {
        try {
            Map<?, ?> existing = restClient.get()
                    .uri("/collections/{name}", COLLECTION)
                    .retrieve()
                    .body(Map.class);
            if (existing != null) {
                return true;
            }
        } catch (Exception ignored) {
            // 集合不存在时创建
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("vectors", Map.of("size", dim, "distance", "Cosine"));
            restClient.put()
                    .uri("/collections/{name}", COLLECTION)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.warn("创建向量集合失败：{}", e.getMessage());
            return false;
        }
    }

    public static Map<String, Object> point(long reviewId, List<Double> vector, Long projectId, List<String> aspects) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("projectId", projectId);
        payload.put("aspects", aspects == null ? List.of() : new ArrayList<>(aspects));
        Map<String, Object> point = new HashMap<>();
        point.put("id", reviewId);
        point.put("vector", vector);
        point.put("payload", payload);
        return point;
    }
}
