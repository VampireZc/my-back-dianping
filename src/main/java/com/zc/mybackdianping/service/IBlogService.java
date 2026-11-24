package com.zc.mybackdianping.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Blog;

public interface IBlogService extends IService<Blog> {

    // 保存博客
    Result saveBlog(Blog blog);

    // 获取热门博客
    Result getHotBlogs(Integer current);

    // 点赞博客
    Result likeBlog(Long id);

    // 根据id获取博客
    Result getBlogById(Long id);

    // 查询博客点赞数前五
    Result queryBlogLikesTop5(Long id);

    // 达人主页笔记列表
    Result getBlogsByUser(Long id, Integer current);

    // 个人主页笔记列表
    Result getBlogsByMe();

    // 查看关注的博客列表
    Result getBlogsByFollow(Long max, Integer offset);
}
