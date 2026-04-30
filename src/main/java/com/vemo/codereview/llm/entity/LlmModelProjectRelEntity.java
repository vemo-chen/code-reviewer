package com.vemo.codereview.llm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("llm_model_project_rel")
@Getter
@Setter
public class LlmModelProjectRelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long llmModelId;
    private Long projectId;
    private Date createdAt;
}
