package com.lumiinsight.modules.pipeline;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Map;

@Component
public class WorkerClient {

    private final RestClient restClient;

    public WorkerClient(@Value("${lumiinsight.worker.base-url}") String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(1));
        factory.setReadTimeout(Duration.ofSeconds(30));
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> clean(Long jobId, Long projectId) {
        return restClient.post()
                .uri("/v1/jobs/clean")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("jobId", jobId, "projectId", projectId, "stage", "CLEAN"))
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
