package edu.unsw.service.impl;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.service.utils.FileUtils;
import edu.unsw.service.FileService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 29/6/2026 16:06
 * @description File-related services implementation
 */
@Service
@Slf4j
public class FileServiceImpl implements FileService {
    private final Logger logger = LoggerFactory.getLogger(FileServiceImpl.class);

    @Override
    public String upload(MultipartFile multipartFile) {
        String projectRoot = System.getProperty("user.dir");
        String uploadDirPath = projectRoot + File.separator + "file";
        File uploadDir = new File(uploadDirPath);

        if (!uploadDir.isDirectory() && !uploadDir.mkdirs()) {
            log.error("Create directory failed：{}", uploadDir);
            throw new BusinessException(ResponseEnum.FILE_PATH_CREATE_FAILED);
        }

        String safeOriginal = FileUtils.resolveSafeFilename(multipartFile.getOriginalFilename());
        String fileName = UUID.randomUUID().toString().replace("-", "") + "_" + safeOriginal;

        File targetFile = new File(uploadDir, fileName);
        try {
            multipartFile.transferTo(targetFile);
        } catch (IOException e) {
            logger.error("Upload file failed: {}", e.getMessage());
            throw new BusinessException(ResponseEnum.UPLOAD_FILE_FAILED);
        }

        return "/file/" + fileName;
    }
}
