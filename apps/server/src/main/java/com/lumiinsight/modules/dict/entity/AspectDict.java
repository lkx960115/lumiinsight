package com.lumiinsight.modules.dict.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("aspect_dict")
public class AspectDict {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Integer sortNo;
    private Integer enabled;
    @TableLogic
    private Integer deleted;
}
