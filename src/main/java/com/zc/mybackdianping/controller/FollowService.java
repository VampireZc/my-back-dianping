package com.zc.mybackdianping.controller;

import com.zc.mybackdianping.dto.Result;
import com.zc.mybackdianping.service.IFollowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/follow")
@RequiredArgsConstructor
public class FollowService {
    private final IFollowService followService;

    @PutMapping("/{id}/{isFollow}")
    public Result follow(
            @PathVariable("id") Integer id,
            @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(id, isFollow);
    }
}
