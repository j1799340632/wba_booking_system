package edu.unsw.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 29/6/2026 16:04
 * @description File related services
 */
public interface FileService {
    /**
     * Upload file
     * @param multipartFile multipartFile
     * @return url
     */
    String upload(MultipartFile multipartFile);
}
