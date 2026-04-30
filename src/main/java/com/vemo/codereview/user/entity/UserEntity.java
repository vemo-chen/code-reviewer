package com.vemo.codereview.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Getter;
import lombok.Setter;

@TableName("sys_user")
@Getter
@Setter
public class UserEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String username;
    private String passwordHash;
    private String displayName;
    private String gitlabUsername;
    private String role;
    private String status;
    private Date createdAt;
    private Date updatedAt;
}
