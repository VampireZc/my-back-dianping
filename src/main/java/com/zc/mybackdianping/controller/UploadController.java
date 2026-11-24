package com.zc.mybackdianping.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.zc.mybackdianping.constants.SystemConstants;
import com.zc.mybackdianping.dto.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;

@Slf4j
@RestController
@RequestMapping("/upload")
public class UploadController {

    @PostMapping("/blog")
    public Result uploadImage(@RequestParam("file")MultipartFile image){
        String filename = image.getOriginalFilename();
        String url = createFileName(filename);
        // 保存文件
        try {
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR + url));
            log.info("上传成功");
            return Result.ok(url);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return Result.fail("上传失败");
    }

    private String createFileName(String filename) {
        // 获取文件后缀
        String afterFix = StrUtil.subAfter(filename, ".", true);
        String name = UUID.randomUUID().toString(true) + "." + afterFix;
        // 生成目录
        int code = name.hashCode();
        int d1 = code & 0xF;
        int d2 = (code >> 4) & 0xF;
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        // 判断目录是否存在
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}", d1, d2, name);
    }
}
