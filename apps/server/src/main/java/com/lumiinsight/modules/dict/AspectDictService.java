package com.lumiinsight.modules.dict;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lumiinsight.common.exception.BizException;
import com.lumiinsight.modules.audit.AuditService;
import com.lumiinsight.modules.dict.entity.AspectDict;
import com.lumiinsight.modules.dict.mapper.AspectDictMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AspectDictService {

    private final AspectDictMapper aspectDictMapper;
    private final AuditService auditService;

    public AspectDictService(AspectDictMapper aspectDictMapper, AuditService auditService) {
        this.aspectDictMapper = aspectDictMapper;
        this.auditService = auditService;
    }

    public List<AspectDict> listAll() {
        return aspectDictMapper.selectList(new LambdaQueryWrapper<AspectDict>().orderByAsc(AspectDict::getSortNo));
    }

    public List<AspectDict> listEnabled() {
        return aspectDictMapper.selectList(
                new LambdaQueryWrapper<AspectDict>()
                        .eq(AspectDict::getEnabled, 1)
                        .orderByAsc(AspectDict::getSortNo)
        );
    }

    public AspectDict save(AspectDict req) {
        if (!StringUtils.hasText(req.getName())) {
            throw BizException.of("BAD_REQUEST", "方面名称不能为空");
        }
        String name = req.getName().trim();
        Long exists = aspectDictMapper.selectCount(
                new LambdaQueryWrapper<AspectDict>()
                        .eq(AspectDict::getName, name)
                        .ne(req.getId() != null, AspectDict::getId, req.getId())
        );
        if (exists != null && exists > 0) {
            throw BizException.of("DUPLICATE", "方面名称已存在");
        }
        AspectDict row = req.getId() == null ? new AspectDict() : aspectDictMapper.selectById(req.getId());
        if (req.getId() != null && row == null) {
            throw BizException.of("NOT_FOUND", "词典项不存在");
        }
        row.setName(name);
        row.setKeywords(req.getKeywords());
        row.setSortNo(req.getSortNo() == null ? 0 : req.getSortNo());
        row.setEnabled(req.getEnabled() == null ? 1 : req.getEnabled());
        if (row.getId() == null) {
            aspectDictMapper.insert(row);
        } else {
            aspectDictMapper.updateById(row);
        }
        auditService.record("dict.save", "aspect_dict", row.getName());
        return row;
    }
}
