package com.lumiinsight.modules.sys;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.infra.minio.ObjectStorage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectStorage objectStorage;

    public HealthController(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate, ObjectStorage objectStorage) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.objectStorage = objectStorage;
    }

    @GetMapping
    public ApiResult<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "up");
        data.put("mysql", pingMysql());
        data.put("redis", pingRedis());
        data.put("minio", objectStorage.ping() ? "up" : "down");
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
        try {
            String pong = redisTemplate.getConnectionFactory() == null
                    ? null
                    : redisTemplate.getConnectionFactory().getConnection().ping();
            return pong != null ? "up" : "down";
        } catch (Exception e) {
            return "down";
        }
    }
}
