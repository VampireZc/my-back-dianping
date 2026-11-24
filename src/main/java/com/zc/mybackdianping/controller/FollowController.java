package com.zc.mybackdianping.controller;

import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.service.IFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowController {
    private final IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(
            @PathVariable("id") Long id,
            @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(id, isFollow);
    }

    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") Long id){
        return followService.isFollow(id);
    }

    @GetMapping("/common/{id}")
    public Result commonFollowees(@PathVariable("id") Long id){
        return followService.commonFollowees(id);
    }
}
