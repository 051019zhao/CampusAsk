package com.campusask.campusask.controller;

import com.campusask.campusask.utils.OssUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class TestOssController {

    @Autowired
    private OssUtil ossUtil;

    @PostMapping("/test/upload")
    public String testUpload(@RequestParam("file") MultipartFile file) {
        return ossUtil.uploadFile(file, "test/");
    }
}
