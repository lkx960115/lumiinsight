package com.lumiinsight.modules.channel;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Configuration
public class ChannelAdapterConfig {

    @Bean
    public ChannelAdapter xiaohongshuAdapter() {
        return new UnimplementedChannelAdapter("xiaohongshu");
    }

    @Bean
    public ChannelAdapter jdAdapter() {
        return new UnimplementedChannelAdapter("jd");
    }

    @Bean
    public ChannelAdapter taobaoAdapter() {
        return new UnimplementedChannelAdapter("taobao");
    }

    @Bean
    public ChannelAdapter douyinAdapter() {
        return new UnimplementedChannelAdapter("douyin");
    }

    @Bean
    public ChannelAdapterRegistry channelAdapterRegistry(List<ChannelAdapter> adapters) {
        Map<String, ChannelAdapter> map = adapters.stream()
                .collect(Collectors.toMap(ChannelAdapter::platform, Function.identity()));
        return new ChannelAdapterRegistry(map);
    }
}
