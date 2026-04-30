package com.vemo.codereview.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("sys_user_project_rel")
@Getter
@Setter
public class UserProjectRelEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long projectId;
    private Date createdAt;
}
