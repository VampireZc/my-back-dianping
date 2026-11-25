package com.zc.mybackdianping.vo;

import com.zc.mybackdianping.entity.Comments;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@EqualsAndHashCode(callSuper = true)
@Data
public class CommentVO extends Comments {
    private String nickName;
    private String icon;
    // 必须用包装类型 Boolean + 名字 isLike：Lombok 生成 getIsLike()，
    private Boolean isLike;
    private Long replyCount;
    // 前两条回复
    private List<CommentVO> replies;
}
