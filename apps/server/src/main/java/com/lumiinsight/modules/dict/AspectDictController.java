package com.lumiinsight.modules.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.dict.entity.AspectDict;
import com.lumiinsight.modules.dict.mapper.AspectDictMapper;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dicts")
public class AspectDictController {

    private final AspectDictMapper aspectDictMapper;

    public AspectDictController(AspectDictMapper aspectDictMapper) {
        this.aspectDictMapper = aspectDictMapper;
    }

    @GetMapping("/aspects")
    @PreAuthorize("hasAuthority('admin:dict:view')")
    public ApiResult<List<AspectDict>> list() {
        return ApiResult.ok(aspectDictMapper.selectList(
                new LambdaQueryWrapper<AspectDict>().orderByAsc(AspectDict::getSortNo)));
    }
}
