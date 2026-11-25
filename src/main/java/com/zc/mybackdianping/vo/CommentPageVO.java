package com.zc.mybackdianping.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentPageVO {
    private List<CommentVO> records;
    private long total;
    private long current;
    private long size;
}
