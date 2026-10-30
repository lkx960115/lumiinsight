package com.lumiinsight.modules.datasource;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.common.util.AesEncryptor;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.datasource.dto.ChannelSaveRequest;
import com.lumiinsight.modules.datasource.dto.ChannelView;
import com.lumiinsight.modules.datasource.dto.ProviderSaveRequest;
import com.lumiinsight.modules.datasource.dto.ProviderView;
import com.lumiinsight.modules.datasource.entity.DataSourceChannel;
import com.lumiinsight.modules.datasource.entity.DataSourceProvider;
import com.lumiinsight.modules.datasource.entity.DataSourceUsage;
import com.lumiinsight.modules.datasource.mapper.DataSourceChannelMapper;
import com.lumiinsight.modules.datasource.mapper.DataSourceProviderMapper;
import com.lumiinsight.modules.datasource.mapper.DataSourceUsageMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class DataSourceAdminService {

    private static final ZoneId CST = ZoneId.of("Asia/Shanghai");

    private final DataSourceProviderMapper providerMapper;
    private final DataSourceChannelMapper channelMapper;
    private final DataSourceUsageMapper usageMapper;
    private final AesEncryptor aesEncryptor;
    private final AuditService auditService;

    public DataSourceAdminService(
            DataSourceProviderMapper providerMapper,
            DataSourceChannelMapper channelMapper,
            DataSourceUsageMapper usageMapper,
            AesEncryptor aesEncryptor,
            AuditService auditService
    ) {
        this.providerMapper = providerMapper;
        this.channelMapper = channelMapper;
        this.usageMapper = usageMapper;
        this.aesEncryptor = aesEncryptor;
        this.auditService = auditService;
    }

    public List<ProviderView> providers() {
        List<DataSourceProvider> rows = providerMapper.selectList(
                new LambdaQueryWrapper<DataSourceProvider>().orderByAsc(DataSourceProvider::getId));
        List<ProviderView> out = new ArrayList<>();
        for (DataSourceProvider p : rows) {
            out.add(toView(p));
        }
        return out;
    }

    public void saveProvider(Long id, ProviderSaveRequest req) {
        if (!"HTTP_JSON".equalsIgnoreCase(req.getProtocol())) {
            throw BizException.of("VALIDATION_ERROR", "目前仅支持 HTTP_JSON");
        }
        if (!req.getBaseUrl().startsWith("http://") && !req.getBaseUrl().startsWith("https://")) {
            throw BizException.of("VALIDATION_ERROR", "接口地址必须以 http:// 或 https:// 开头");
        }
        DataSourceProvider p = id == null ? new DataSourceProvider() : providerMapper.selectById(id);
        if (p == null) {
            throw BizException.of("NOT_FOUND", "数据服务不存在");
        }
        p.setName(req.getName().trim());
        p.setProtocol("HTTP_JSON");
        p.setBaseUrl(trimSlash(req.getBaseUrl()));
        p.setPricePerItem(nonNull(req.getPricePerItem()));
        p.setDailyBudgetYuan(nonNull(req.getDailyBudgetYuan()));
        p.setDailyItemLimit(Math.max(0, nonNull(req.getDailyItemLimit())));
        p.setMaxPagesPerRun(Math.max(1, nonNull(req.getMaxPagesPerRun())));
        p.setTimeoutMs(Math.max(1000, nonNull(req.getTimeoutMs())));
        p.setMaxRetry(Math.max(0, nonNull(req.getMaxRetry())));
        p.setEnabled(req.getEnabled() == null ? 0 : req.getEnabled());
        p.setRemark(req.getRemark());
        if (StringUtils.hasText(req.getApiKey())) {
            String key = req.getApiKey().trim();
            p.setApiKeyCipher(aesEncryptor.encrypt(key));
            p.setApiKeySuffix(AesEncryptor.suffix(key));
        }
        if (id == null) {
            providerMapper.insert(p);
        } else {
            providerMapper.updateById(p);
        }
        auditService.record("datasource.provider.save", "datasource", p.getName());
    }

    public List<ChannelView> channels(Long providerId) {
        requireProvider(providerId);
        List<DataSourceChannel> rows = channelMapper.selectList(
                new LambdaQueryWrapper<DataSourceChannel>()
                        .eq(DataSourceChannel::getProviderId, providerId)
                        .orderByAsc(DataSourceChannel::getPlatform));
        List<ChannelView> out = new ArrayList<>();
        for (DataSourceChannel c : rows) {
            out.add(toView(c));
        }
        return out;
    }

    public void saveChannel(Long providerId, ChannelSaveRequest req) {
        requireProvider(providerId);
        String platform = req.getPlatform().trim().toLowerCase();
        DataSourceChannel c = channelMapper.selectOne(new LambdaQueryWrapper<DataSourceChannel>()
                .eq(DataSourceChannel::getProviderId, providerId)
                .eq(DataSourceChannel::getPlatform, platform)
                .last("LIMIT 1"));
        boolean create = c == null;
        if (create) {
            c = new DataSourceChannel();
            c.setProviderId(providerId);
            c.setPlatform(platform);
        }
        c.setEndpointPath(req.getEndpointPath().trim());
        c.setHttpMethod(StringUtils.hasText(req.getHttpMethod()) ? req.getHttpMethod().trim().toUpperCase() : "GET");
        c.setRequestTemplate(req.getRequestTemplate());
        c.setReviewMapping(req.getReviewMapping());
        c.setItemsPath(req.getItemsPath().trim());
        c.setPageSize(Math.max(0, nonNull(req.getPageSize())));
        c.setSupportsCompetitor(req.getSupportsCompetitor() == null ? 1 : req.getSupportsCompetitor());
        c.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        if (create) {
            channelMapper.insert(c);
        } else {
            channelMapper.updateById(c);
        }
        auditService.record("datasource.channel.save", "datasource", platform);
    }

    public DataSourceProvider requireProvider(Long providerId) {
        DataSourceProvider p = providerMapper.selectById(providerId);
        if (p == null) {
            throw BizException.of("NOT_FOUND", "数据服务不存在");
        }
        return p;
    }

    public DataSourceChannel requireChannel(Long providerId, String platform) {
        DataSourceChannel c = channelMapper.selectOne(new LambdaQueryWrapper<DataSourceChannel>()
                .eq(DataSourceChannel::getProviderId, providerId)
                .eq(DataSourceChannel::getPlatform, platform.trim().toLowerCase())
                .eq(DataSourceChannel::getEnabled, 1)
                .last("LIMIT 1"));
        if (c == null) {
            throw BizException.of("NOT_FOUND", "该平台还没配置接口");
        }
        return c;
    }

    public String decryptToken(DataSourceProvider p) {
        if (!StringUtils.hasText(p.getApiKeyCipher())) {
            throw BizException.of("VALIDATION_ERROR", "还没填 Token");
        }
        return aesEncryptor.decrypt(p.getApiKeyCipher());
    }

    /** 今日已用条数与花费，用于预算闸门与页面展示 */
    public UsageSnapshot todayUsage(Long providerId) {
        QueryWrapper<DataSourceUsage> q = new QueryWrapper<>();
        q.select("COALESCE(SUM(item_count),0) AS items", "COALESCE(SUM(cost_yuan),0) AS cost")
                .eq("provider_id", providerId)
                .ge("created_at", LocalDate.now(CST).atStartOfDay());
        List<Map<String, Object>> rows = usageMapper.selectMaps(q);
        long items = 0L;
        BigDecimal cost = BigDecimal.ZERO;
        if (!rows.isEmpty() && rows.get(0) != null) {
            items = (long) readDecimal(rows.get(0), "items").doubleValue();
            cost = readDecimal(rows.get(0), "cost");
        }
        return new UsageSnapshot(items, cost);
    }

    public record UsageSnapshot(long itemCount, BigDecimal costYuan) {
    }

    private ProviderView toView(DataSourceProvider p) {
        boolean hasKey = StringUtils.hasText(p.getApiKeyCipher());
        String masked = hasKey ? "****" + (p.getApiKeySuffix() == null ? "" : p.getApiKeySuffix()) : "未配置";
        UsageSnapshot used = todayUsage(p.getId());
        BigDecimal remaining = null;
        if (p.getDailyBudgetYuan() != null && p.getDailyBudgetYuan().compareTo(BigDecimal.ZERO) > 0) {
            remaining = p.getDailyBudgetYuan().subtract(used.costYuan());
            if (remaining.compareTo(BigDecimal.ZERO) < 0) {
                remaining = BigDecimal.ZERO;
            }
        }
        return ProviderView.builder()
                .id(p.getId())
                .name(p.getName())
                .protocol(p.getProtocol())
                .baseUrl(p.getBaseUrl())
                .apiKeyMasked(masked)
                .hasKey(hasKey)
                .pricePerItem(p.getPricePerItem())
                .dailyBudgetYuan(p.getDailyBudgetYuan())
                .dailyItemLimit(p.getDailyItemLimit())
                .maxPagesPerRun(p.getMaxPagesPerRun())
                .timeoutMs(p.getTimeoutMs())
                .maxRetry(p.getMaxRetry())
                .enabled(p.getEnabled())
                .remark(p.getRemark())
                .todayItemCount(used.itemCount())
                .todayCostYuan(used.costYuan())
                .remainingBudgetYuan(remaining)
                .channels(channels(p.getId()))
                .build();
    }

    private static ChannelView toView(DataSourceChannel c) {
        return ChannelView.builder()
                .id(c.getId())
                .providerId(c.getProviderId())
                .platform(c.getPlatform())
                .endpointPath(c.getEndpointPath())
                .httpMethod(c.getHttpMethod())
                .requestTemplate(c.getRequestTemplate())
                .reviewMapping(c.getReviewMapping())
                .itemsPath(c.getItemsPath())
                .pageSize(c.getPageSize())
                .supportsCompetitor(c.getSupportsCompetitor())
                .enabled(c.getEnabled())
                .build();
    }

    private static BigDecimal readDecimal(Map<String, Object> row, String key) {
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key) && entry.getValue() != null) {
                if (entry.getValue() instanceof BigDecimal value) {
                    return value;
                }
                try {
                    return new BigDecimal(String.valueOf(entry.getValue()));
                } catch (NumberFormatException e) {
                    return BigDecimal.ZERO;
                }
            }
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nonNull(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int nonNull(Integer value) {
        return value == null ? 0 : value;
    }

    private static String trimSlash(String url) {
        String text = url.trim();
        while (text.endsWith("/")) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }
}
