package com.vemo.codereview.notify.entity;

import lombok.Getter;
import lombok.Setter;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;

@TableName("notify_channel_config")
@Getter
@Setter
public class NotifyChannelConfigEntity {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String channelType;
    private String channelName;
    private String webhookUrl;
    private String secret;
    private Boolean enabled;
    private Date createdAt;
    private Date updatedAt;

}
