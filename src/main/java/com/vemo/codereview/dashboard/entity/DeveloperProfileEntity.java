package com.vemo.codereview.dashboard.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

@TableName("developer_profile")
@Getter
@Setter
public class DeveloperProfileEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String developerKey;
    private String developerName;
    private String email;
    private Boolean active;
    private Date createdAt;
    private Date updatedAt;

}
