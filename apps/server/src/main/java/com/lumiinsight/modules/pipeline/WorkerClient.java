package com.lumiinsight.modules.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class WorkerClient {

    private final RestClient restClient;
    private final RestClient analyzeClient;

    public WorkerClient(@Value("${lumiinsight.worker.base-url}") String baseUrl) {
        this.restClient = build(baseUrl, 60);
        this.analyzeClient = build(baseUrl, 180);
    }

    private static RestClient build(String baseUrl, int readSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(readSeconds));
        factory.setOutputStreaming(false);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> clean(
            Long jobId,
            Long projectId,
            List<Map<String, Object>> reviews,
            List<Map<String, Object>> aspects
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", jobId);
        body.put("projectId", projectId);
        body.put("stage", "CLEAN");
        body.put("reviews", reviews);
        body.put("aspects", aspects);
        return restClient.post()
                .uri("/v1/jobs/clean")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> analyze(
            Long jobId,
            Long projectId,
            List<Map<String, Object>> reviews,
            List<Map<String, Object>> aspects,
            Map<String, Object> llm
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", jobId);
        body.put("projectId", projectId);
        body.put("stage", "ANALYZE");
        body.put("reviews", reviews);
        body.put("aspects", aspects);
        body.put("llm", llm);
        return analyzeClient.post()
                .uri("/v1/jobs/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    public boolean ping() {
        try {
            Map<?, ?> body = restClient.get().uri("/health").retrieve().body(Map.class);
            return body != null && "up".equals(String.valueOf(body.get("status")));
        } catch (Exception e) {
            return false;
        }
    }
}
