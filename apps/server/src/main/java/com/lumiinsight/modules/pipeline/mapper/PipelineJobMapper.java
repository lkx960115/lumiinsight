package com.lumiinsight.modules.pipeline.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lumiinsight.modules.pipeline.entity.PipelineJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface PipelineJobMapper extends BaseMapper<PipelineJob> {
}
