package com.zc.mybackdianping.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zc.mybackdianping.entity.Comments;
import com.zc.mybackdianping.vo.CommentVO;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface CommentMapper extends BaseMapper<Comments> {
}
