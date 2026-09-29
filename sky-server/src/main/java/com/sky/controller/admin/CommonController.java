package com.sky.controller.admin;

import com.sky.constant.MessageConstant;
import com.sky.result.Result;
import com.sky.utils.AliOssUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 通用接口
 */
@RestController
@RequestMapping("/admin/common")
@Api(tags = "通用接口")
@Slf4j
public class CommonController {

    @Autowired
    private AliOssUtil aliOssUtil;

    @PostMapping("/upload")
    @ApiOperation("文件上传")
    public Result<String> upload(MultipartFile file) {
        log.info("文件上传：{}", file);
        String newFileName = null;
/*
        try {

//        原始文件名
            String originalFilename = file.getOriginalFilename();

//        截取原始文件名的后缀
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));

//        构造新文件名称
            String objectName = UUID.randomUUID().toString() + extension;

//        文件的请求路径
            String filePath = aliOssUtil.upload(file.getBytes(), objectName);
            return Result.success(filePath);
        } catch (IOException e) {
            log.error("文件删除失败：{}", e);
        }
*/
        try {
            String basePath = "D:/sky-take-out/images/";
// 2. 确保目录存在
            File dir = new File(basePath);
            if (!dir.exists()) {
                dir.mkdirs();
            }
// 3. 生成唯一文件名，防止重名覆盖
            String originalFilename = file.getOriginalFilename();
            String extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            newFileName = UUID.randomUUID().toString() + extension;
// 4. 保存文件到本地
            File dest = new File(basePath + newFileName);
            file.transferTo(dest);
            return Result.success("/images/" + newFileName);
        } catch (IOException e) {
            log.error("文件删除失败：{}", e);
        }
        return Result.error(MessageConstant.UPLOAD_FAILED);
    }

}
