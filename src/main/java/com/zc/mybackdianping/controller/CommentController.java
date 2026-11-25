package com.zc.mybackdianping.controller;

import com.zc.mybackdianping.dto.CommentDTO;
import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.service.ICommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/comment")
@RequiredArgsConstructor
public class CommentController {
    private final ICommentService commentService;

    @PostMapping
    public Result addComments(@RequestBody CommentDTO commentDTO){
      return commentService.addComments(commentDTO);
    }

    @GetMapping("/page")
    public Result getCommentPage(
            @RequestParam("blogId") Integer blogId,
            @RequestParam("current") Integer current
    ){
        return commentService.getCommentPage(blogId, current);
    }

    @PutMapping("/like/{id}")
    public Result likeComment(@PathVariable("id") Integer id){
        return commentService.likeComment(id);
    }

    @DeleteMapping("/{id}")
    public Result deleteComment(@PathVariable("id") Integer id){
        return commentService.deleteComment(id);
    }
}
