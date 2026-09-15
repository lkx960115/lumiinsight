package com.lumiinsight.modules.sys;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.infra.minio.ObjectStorage;
import com.lumiinsight.modules.pipeline.WorkerClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectStorage objectStorage;
    private final WorkerClient workerClient;
    private final String qdrantHost;
    private final int qdrantPort;

    public HealthController(
            JdbcTemplate jdbcTemplate,
            StringRedisTemplate redisTemplate,
            ObjectStorage objectStorage,
            WorkerClient workerClient,
            @Value("${lumiinsight.qdrant.host:127.0.0.1}") String qdrantHost,
            @Value("${lumiinsight.qdrant.port:6333}") int qdrantPort
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectStorage = objectStorage;
        this.workerClient = workerClient;
        this.qdrantHost = qdrantHost;
        this.qdrantPort = qdrantPort;
    }

    @GetMapping
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "up");
        data.put("mysql", pingMysql());
        data.put("redis", pingRedis());
        data.put("qdrant", pingTcp(qdrantHost, qdrantPort));
        data.put("minio", objectStorage.ping() ? "up" : "down");
        data.put("worker", workerClient.ping() ? "up" : "down");
        return ApiResult.ok(data);
    }

    private String pingMysql() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "up";
        } catch (Exception e) {
            return "down";
        }
    }

    private String pingRedis() {
        if (redisTemplate.getConnectionFactory() == null) {
            return "down";
        }
        try (var connection = redisTemplate.getConnectionFactory().getConnection()) {
            String pong = connection.ping();
            return pong != null ? "up" : "down";
        } catch (Exception e) {
            return "down";
        }
    }

    private static String pingTcp(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 800);
            return "up";
        } catch (Exception e) {
            return "down";
        }
    }
}
