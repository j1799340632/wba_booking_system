package edu.unsw.api.controller.general;

import edu.unsw.core.constant.RequestPrefixConstant;
import edu.unsw.core.domain.pojo.ResultEntity;
import edu.unsw.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 29/6/2026 16:02
 * @description File related interfaces
 */
@RestController
@RequestMapping(RequestPrefixConstant.GENERAL_PREFIX + "/file")
@Tag(name = "File related interfaces")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload a file and get url")
    public ResultEntity<String> upload(@RequestPart("file") MultipartFile multipartFile) {
        return ResultEntity.success("Upload successfully", fileService.upload(multipartFile));
    }
}
