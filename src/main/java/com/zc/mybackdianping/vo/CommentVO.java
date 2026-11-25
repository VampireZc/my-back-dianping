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
    private boolean isLike;
    private Long replayCount;
    // 前两条回复
    private List<CommentVO> replies;
}
