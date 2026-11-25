package com.zc.mybackdianping.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("tb_blog_comments")
public class Comments {
    private Long id;
    private Long userId;
    private Long blogId;
    private Integer parentId;
    private Integer answerId;
    private String content;
    private Integer liked;
    private Integer status;
    private String createTime;
    private String updateTime;
}
