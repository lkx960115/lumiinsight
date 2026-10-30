package com.lumiinsight.modules.datasource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.util.JsonPathLite;
import com.lumiinsight.modules.datasource.dto.TestResult;
import com.lumiinsight.modules.datasource.entity.DataSourceChannel;
import com.lumiinsight.modules.datasource.entity.DataSourceProvider;
import com.lumiinsight.modules.datasource.entity.DataSourceUsage;
import com.lumiinsight.modules.datasource.mapper.DataSourceUsageMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataSourceTestService {

    private static final ZoneId CST = ZoneId.of("Asia/Shanghai");
    private static final int RAW_SAMPLE_LIMIT = 800;
    private static final List<String> REQUIRED_FIELDS = List.of("reviewId", "content");

    private final DataSourceAdminService adminService;
    private final DataSourceUsageMapper usageMapper;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public DataSourceTestService(
            DataSourceAdminService adminService,
            DataSourceUsageMapper usageMapper,
            ObjectMapper objectMapper
    ) {
        this.adminService = adminService;
        this.usageMapper = usageMapper;
        this.objectMapper = objectMapper;
    }

    /** 取第 1 页试拉一次，验证 Token 与字段映射；这次调用会计费，页面需提示 */
    public TestResult test(Long providerId, String platform, String productId) {
        if (!StringUtils.hasText(productId)) {
            throw BizException.of("VALIDATION_ERROR", "请先填一个商品ID再测");
        }
        DataSourceProvider provider = adminService.requireProvider(providerId);
        DataSourceChannel channel = adminService.requireChannel(providerId, platform);
        if (!"GET".equalsIgnoreCase(channel.getHttpMethod())) {
            throw BizException.of("VALIDATION_ERROR", "目前只支持 GET 接口");
        }
        String token = adminService.decryptToken(provider);
        String url = buildUrl(provider, channel, token, productId.trim(), 1);

        int httpStatus = 0;
        String body = null;
        String failure = null;
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(provider.getTimeoutMs() == null ? 120000 : provider.getTimeoutMs()))
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            httpStatus = response.statusCode();
            body = response.body();
        } catch (IllegalArgumentException e) {
            failure = "接口地址不合法";
        } catch (Exception e) {
            failure = "请求没发出去：" + mask(e.getMessage(), token);
        }

        JsonNode root = null;
        Integer code = null;
        if (StringUtils.hasText(body)) {
            try {
                root = objectMapper.readTree(body);
                if (root != null && root.has("code")) {
                    code = root.path("code").asInt();
                }
            } catch (Exception e) {
                failure = failure == null ? "返回的不是 JSON" : failure;
            }
        }

        JsonNode items = JsonPathLite.read(root, channel.getItemsPath());
        int itemCount = items != null && items.isArray() ? items.size() : 0;
        JsonNode firstItem = itemCount > 0 ? items.get(0) : null;

        Map<String, String> mapping = readMapping(channel.getReviewMapping());
        List<TestResult.FieldProbe> probes = new ArrayList<>();
        boolean requiredOk = true;
        for (Map.Entry<String, String> entry : mapping.entrySet()) {
            String sample = firstItem == null ? null : JsonPathLite.text(firstItem, entry.getValue());
            boolean ok = StringUtils.hasText(sample);
            if (!ok && REQUIRED_FIELDS.contains(entry.getKey())) {
                requiredOk = false;
            }
            probes.add(TestResult.FieldProbe.builder()
                    .name(entry.getKey())
                    .ok(ok)
                    .sample(truncate(sample, 60))
                    .build());
        }

        boolean ok = failure == null && code != null && code == 0 && itemCount > 0 && requiredOk;
        BigDecimal cost = costOf(provider, itemCount);
        String message = failure != null
                ? failure
                : ok
                ? "通了，第 1 页拿到 " + itemCount + " 条"
                : describeFailure(code, itemCount, requiredOk);

        saveUsage(provider, channel, itemCount, cost, ok, message, platform);

        return TestResult.builder()
                .ok(ok)
                .code(code)
                .message(message)
                .httpStatus(httpStatus)
                .itemCount(itemCount)
                .costYuan(cost)
                .fields(probes)
                .rawSample(truncate(mask(body, token), RAW_SAMPLE_LIMIT))
                .build();
    }

    private void saveUsage(DataSourceProvider provider, DataSourceChannel channel, int itemCount,
                           BigDecimal cost, boolean ok, String detail, String platform) {
        DataSourceUsage usage = new DataSourceUsage();
        usage.setProviderId(provider.getId());
        usage.setChannelId(channel.getId());
        usage.setPlatform(platform);
        usage.setRequestCount(1);
        usage.setItemCount(ok ? itemCount : 0);
        usage.setCostYuan(ok ? cost : BigDecimal.ZERO);
        usage.setSuccess(ok ? 1 : 0);
        usage.setDetail(truncate(detail, 255));
        usage.setCreatedAt(LocalDateTime.now(CST));
        usageMapper.insert(usage);
    }

    private static BigDecimal costOf(DataSourceProvider provider, int itemCount) {
        if (provider.getPricePerItem() == null || itemCount <= 0) {
            return BigDecimal.ZERO;
        }
        return provider.getPricePerItem().multiply(BigDecimal.valueOf(itemCount))
                .setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private static String describeFailure(Integer code, int itemCount, boolean requiredOk) {
        if (code != null && code != 0) {
            return switch (code) {
                case 100 -> "Token 无效或已失效";
                case 301 -> "供应商没采到，稍后重试";
                case 302 -> "供应商限流了，稍后再试";
                case 303 -> "超出每日配额";
                case 400 -> "参数不对，检查商品ID和模板";
                case 600 -> "该接口没有权限";
                case 601 -> "供应商账户余额不足";
                case 602 -> "该 Token 已达消费上限";
                default -> "供应商返回 code=" + code;
            };
        }
        if (itemCount <= 0) {
            return "返回里没找到列表，检查列表路径";
        }
        if (!requiredOk) {
            return "评价ID或原文没取到，检查字段映射";
        }
        return "没通过";
    }

    private Map<String, String> readMapping(String json) {
        if (!StringUtils.hasText(json)) {
            throw BizException.of("VALIDATION_ERROR", "字段映射不能为空");
        }
        try {
            Map<String, String> raw = objectMapper.readValue(json, new TypeReference<LinkedHashMap<String, String>>() {
            });
            return raw == null ? Map.of() : raw;
        } catch (Exception e) {
            throw BizException.of("VALIDATION_ERROR", "字段映射不是合法 JSON");
        }
    }

    private static String buildUrl(DataSourceProvider provider, DataSourceChannel channel,
                                   String token, String productId, int page) {
        StringBuilder sb = new StringBuilder(provider.getBaseUrl()).append(channel.getEndpointPath());
        String template = channel.getRequestTemplate();
        String extra = "";
        if (StringUtils.hasText(template)) {
            extra = template
                    .replace("${productId}", encode(productId))
                    .replace("${page}", String.valueOf(page))
                    .replace("${pageSize}", String.valueOf(channel.getPageSize() == null ? 0 : channel.getPageSize()))
                    .trim();
            while (extra.startsWith("?") || extra.startsWith("&")) {
                extra = extra.substring(1);
            }
        }
        boolean hasQuery = sb.indexOf("?") >= 0;
        if (!extra.isEmpty()) {
            sb.append(hasQuery ? '&' : '?').append(extra);
            hasQuery = true;
        }
        sb.append(hasQuery ? '&' : '?').append("token=").append(encode(token));
        return sb.toString();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    private static String mask(String text, String token) {
        if (text == null) {
            return null;
        }
        if (StringUtils.hasText(token)) {
            return text.replace(token, "****");
        }
        return text;
    }

    private static String truncate(String text, int limit) {
        if (text == null) {
            return null;
        }
        return text.length() <= limit ? text : text.substring(0, limit) + "…";
    }
}
