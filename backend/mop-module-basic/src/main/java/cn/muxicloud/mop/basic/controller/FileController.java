package cn.muxicloud.mop.basic.controller;

import cn.muxicloud.mop.basic.entity.StoredFileEntity;
import cn.muxicloud.mop.basic.service.FileService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {
    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    @RequiresPermission("basic:file:list")
    public ApiResponse<List<FileService.FileView>> list() {
        return ApiResponse.ok(fileService.list());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @RequiresPermission("basic:file:upload")
    public ApiResponse<FileService.FileView> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) String category,
            Authentication authentication) {
        return ApiResponse.ok(fileService.upload(file, projectId, category, (AuthenticatedUser) authentication.getPrincipal()));
    }

    @GetMapping("/{id}/preview")
    @RequiresPermission("basic:file:list")
    public void preview(@PathVariable Long id, HttpServletResponse response) throws Exception {
        StoredFileEntity entity = fileService.required(id);
        response.setContentType(entity.getContentType());
        response.setHeader("Content-Disposition", ContentDisposition.inline()
                .filename(entity.getOriginalName(), StandardCharsets.UTF_8)
                .build()
                .toString());
        fileService.preview(id, response.getOutputStream());
    }

    @GetMapping("/{id}/text")
    @RequiresPermission("basic:file:list")
    public ApiResponse<FileService.TextView> text(@PathVariable Long id) {
        return ApiResponse.ok(fileService.text(id));
    }
}
