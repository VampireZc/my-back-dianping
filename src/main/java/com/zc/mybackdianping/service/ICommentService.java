package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.CommentDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Comments;

public interface ICommentService extends IService<Comments> {
    // 新增评论
    Result addComments(CommentDTO commentDTO);

    // 获取分页评论
    Result getCommentPage(Integer blogId, Integer current);

    // 点赞评论
    Result likeComment(Integer id);

    // 删除评论
    Result deleteComment(Integer id);
}
