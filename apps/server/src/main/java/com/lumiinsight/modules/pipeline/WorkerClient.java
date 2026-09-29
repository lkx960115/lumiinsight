package com.lumiinsight.modules.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
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
        this.analyzeClient = build(baseUrl, 90);
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
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> embed(
            Long jobId,
            Long projectId,
            List<Map<String, Object>> reviews,
            Map<String, Object> llm
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("jobId", jobId);
        body.put("projectId", projectId);
        body.put("reviews", reviews);
        body.put("llm", llm);
        return restClient.post()
                .uri("/v1/jobs/embed")
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String raw = new String(response.getBody().readAllBytes(), StandardCharsets.UTF_8);
                    throw new IllegalStateException(workerErrorMessage(raw, response.getStatusCode().value()));
                })
                .body(Map.class);
    }

    private static String workerErrorMessage(String raw, int status) {
        String text = raw == null ? "" : raw.trim();
        if (text.startsWith("{")) {
            int messageAt = text.indexOf("\"message\"");
            if (messageAt >= 0) {
                int colon = text.indexOf(':', messageAt);
                int firstQuote = text.indexOf('"', colon + 1);
                int secondQuote = firstQuote >= 0 ? text.indexOf('"', firstQuote + 1) : -1;
                if (firstQuote >= 0 && secondQuote > firstQuote) {
                    return text.substring(firstQuote + 1, secondQuote);
                }
            }
        }
        if (!text.isBlank()) {
            return text.length() > 240 ? text.substring(0, 240) : text;
        }
        return "分析服务返回 " + status;
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
