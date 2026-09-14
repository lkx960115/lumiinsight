package com.lumiinsight.modules.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumiinsight.modules.llm.entity.LlmProvider;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface LlmProviderMapper extends BaseMapper<LlmProvider> {
}
