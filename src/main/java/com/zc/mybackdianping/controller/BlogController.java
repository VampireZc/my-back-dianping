package com.zc.mybackdianping.controller;

import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.entity.Blog;
import com.zc.mybackdianping.service.IBlogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController()
@RequestMapping("/blog")
@RequiredArgsConstructor
public class BlogController {

    private final IBlogService blogService;

    @PostMapping
    public Result createBlog(@RequestBody Blog blog) {
        return  blogService.saveBlog(blog);
    }

    @GetMapping("/hot")
    public Result getHotBlogs(@RequestParam(value = "current", defaultValue = "1")  Integer current) {
        return blogService.getHotBlogs(current);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        return blogService.likeBlog(id);
    }

    @GetMapping("/{id}")
    public Result getBlog(@PathVariable("id") Long id) {
        return blogService.getBlogById(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {
        return blogService.queryBlogLikesTop5(id);
    }

    @GetMapping("/of/user")
    public Result getBlogsByUser(
            @RequestParam("id") Long id,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        return blogService.getBlogsByUser(id, current);
    }

    @GetMapping("/of/me")
    public Result getBlogsByMe() {
        return blogService.getBlogsByMe();
    }

    @GetMapping("/of/follow")
    public Result getBlogsByFollow(
            @RequestParam("lastId") Long max,
            @RequestParam(value = "offset", defaultValue = "0") Integer offset
    ) {
        return blogService.getBlogsByFollow(max, offset);
    }

}
