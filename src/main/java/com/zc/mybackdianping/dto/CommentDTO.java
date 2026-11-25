package com.zc.mybackdianping.dto;

import lombok.Data;

@Data
public class CommentDTO {
    private Long blogId;
    private String content;
    private Integer answerId;
}
