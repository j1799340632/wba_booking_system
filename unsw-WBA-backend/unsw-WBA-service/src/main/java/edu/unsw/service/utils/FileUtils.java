package edu.unsw.service.utils;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.enums.ResponseEnum;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * @author 徐一杰
 * @date 2026/06/29 16:34
 * @description File operation tool class
 */
public class FileUtils {
    private FileUtils() {

    }

    private static final Logger log = LoggerFactory.getLogger(FileUtils.class);

    public static String resolveSafeFilename(String fileName) {
        if (!StringUtils.hasLength(fileName)) {
            fileName = "unknown.pdf";
        }
        // Removing the path from the file name string
        return Path.of(fileName).getFileName().toString().replaceAll("[\\\\/]", "");
    }

    public static Path resolveSafePath(String foldPath, String fileName) {
        fileName = resolveSafeFilename(fileName);
        // Create path, avoid path traversal attacks
        Path basePath = Path.of(foldPath).toAbsolutePath().normalize();
        Path resolvedPath = basePath.resolve(fileName).normalize();

        // Validate that the resolved path is still within the base path
        if (!resolvedPath.startsWith(basePath)) {
            log.error("Invalid file path detected, possible path traversal attack: {}", resolvedPath);
            throw new BusinessException(ResponseEnum.FILE_PATH_CREATE_FAILED);
        }
        return resolvedPath;
    }

    public static File saveMultipartFile(String foldPath, String fileName, MultipartFile multipartFile) {
        Path resolvedPath = resolveSafePath(foldPath, fileName);

        File file = resolvedPath.toFile();

        // If the parent directory does not exist, create it
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs()) {
            log.error("Create directory failed: {}", resolvedPath);
            throw new BusinessException(ResponseEnum.FILE_PATH_CREATE_FAILED);
        }
        try {
            multipartFile.transferTo(file);
            return file;
        } catch (IOException e) {
            log.error("MultipartFile transferTo File failed", e);
            throw new BusinessException(ResponseEnum.FILE_NOT_EXIST);
        }
    }

    public static void downloadFile(String foldPath, String fileName, HttpServletResponse response) {
        Path resolvedPath = resolveSafePath(foldPath, fileName);
        // File path
        File file = resolvedPath.toFile();
        // Set the response character encoding to UTF-8
        response.setCharacterEncoding("UTF-8");
        // Set response header, text/plain、application/vnd.ms-excel...
        response.setHeader("Content-Type", "application/octet-stream;charset=UTF-8");
        // If you don't set the response header size, you might get the error: "Excel has completed file-level validation and repair. Some parts of this workbook may have been repaired or discarded."
        response.setHeader("Content-Length", String.valueOf(file.length()));
        // The purpose of Content-Disposition: tells the browser how to display the returned file—whether to open it in the browser or download it as an attachment and save it locally
        // attachment indicates downloading as an attachment; inline indicates opening online. "Content-Disposition: inline; filename=filename.mp3"
        // filename represents the default filename. Because network transmission only supports URL-encoded data, the filename needs to be URL-encoded before transmission. The frontend needs to deencode it to obtain the actual filename
        response.setHeader("Content-Disposition", "attachment;filename=" + URLEncoder.encode(fileName.trim(), StandardCharsets.UTF_8));
        // Initialize file stream byte buffer
        byte[] buff = new byte[1024];
        try (OutputStream os = response.getOutputStream(); BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file))) {
            // Write complete, file stream created
            // bis.read(data): Reads characters into an array. This method blocks until some input becomes available, an I/O error occurs, or the end of the stream is reached
            // Returns the number of characters read; if the end of the stream has been reached, return -1
            int i = bis.read(buff);
            while (i != -1) {
                os.write(buff, 0, buff.length);
                os.flush();
                i = bis.read(buff);
            }
            log.info("Download completed：{}", fileName);
        } catch (Exception e) {
            log.info("Download failed：{}", fileName, e);
        }
    }

}
