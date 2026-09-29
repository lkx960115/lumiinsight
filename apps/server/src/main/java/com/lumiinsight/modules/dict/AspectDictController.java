package com.lumiinsight.modules.dict;

import com.lumiinsight.common.api.ApiResult;
import com.lumiinsight.modules.dict.entity.AspectDict;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/dicts")
public class AspectDictController {

    private final AspectDictService aspectDictService;

    public AspectDictController(AspectDictService aspectDictService) {
        this.aspectDictService = aspectDictService;
    }

    @GetMapping("/aspects")
    @PreAuthorize("hasAuthority('admin:dict:view')")
    public ApiResult<List<AspectDict>> list() {
        return ApiResult.ok(aspectDictService.listAll());
    }

    @PostMapping("/aspects")
    @PreAuthorize("hasAuthority('admin:dict:edit')")
    public ApiResult<AspectDict> create(@RequestBody AspectDict req) {
        req.setId(null);
        return ApiResult.ok(aspectDictService.save(req));
    }

    @PutMapping("/aspects/{id}")
    @PreAuthorize("hasAuthority('admin:dict:edit')")
    public ApiResult<AspectDict> update(@PathVariable Long id, @RequestBody AspectDict req) {
        req.setId(id);
        return ApiResult.ok(aspectDictService.save(req));
    }
}
