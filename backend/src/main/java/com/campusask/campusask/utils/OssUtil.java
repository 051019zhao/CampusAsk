package com.campusask.campusask.utils;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

/**
 * 阿里云 OSS 文件上传工具类
 */
@Component
public class OssUtil {

    @Value("${aliyun.oss.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.oss.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.oss.bucket-name}")
    private String bucketName;

    @Value("${aliyun.oss.endpoint}")
    private String endpoint;

    @Value("${aliyun.oss.url-prefix}")
    private String urlPrefix;

    /**
     * 上传文件到 OSS
     *
     * @param file 上传的文件
     * @param folder 文件夹路径（例如：avatar/、contribution/）
     * @return 文件的访问 URL
     */
    public String uploadFile(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new RuntimeException("文件不能为空");
        }

        // 创建 OSS 客户端
        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);

        try {
            // 生成唯一文件名
            String originalFilename = file.getOriginalFilename();
            String extension = "";
            if (originalFilename != null && originalFilename.contains(".")) {
                extension = originalFilename.substring(originalFilename.lastIndexOf("."));
            }
            String fileName = folder + UUID.randomUUID().toString().replace("-", "") + extension;

            // 上传文件
            ossClient.putObject(bucketName, fileName, file.getInputStream());

            // 返回访问 URL
            return urlPrefix + fileName;
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败: " + e.getMessage());
        } finally {
            // 关闭 OSS 客户端
            ossClient.shutdown();
        }
    }

    /**
     * 上传头像（快捷方法）
     */
    public String uploadAvatar(MultipartFile file) {
        return uploadFile(file, "avatar/");
    }

    /**
     * 上传投稿附件（快捷方法）
     */
    public String uploadContribution(MultipartFile file) {
        return uploadFile(file, "contribution/");
    }

    /**
     * 删除 OSS 上的文件
     *
     * @param fileUrl 文件完整 URL
     */
    public void deleteFile(String fileUrl) {
        if (fileUrl == null || fileUrl.isEmpty()) {
            return;
        }

        // 从 URL 中提取文件路径
        String fileName = fileUrl.replace(urlPrefix, "");

        OSS ossClient = new OSSClientBuilder().build(endpoint, accessKeyId, accessKeySecret);
        try {
            ossClient.deleteObject(bucketName, fileName);
        } finally {
            ossClient.shutdown();
        }
    }
}
