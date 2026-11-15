package cn.muxicloud.mop.semantic.controller;

import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.semantic.service.ShaclService;
import cn.muxicloud.mop.semantic.service.ShaclService.Report;
import cn.muxicloud.mop.web.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShaclController {
    private final ShaclService shacl;

    public ShaclController(ShaclService shacl) {
        this.shacl = shacl;
    }

    @GetMapping("/api/v1/branches/{branchId}/shapes")
    @RequiresPermission("model:form:edit")
    public ResponseEntity<byte[]> shapes(@PathVariable Long branchId) {
        byte[] body = shacl.shapes(branchId).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/turtle;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shapes.ttl\"")
                .body(body);
    }

    @PostMapping("/api/v1/branches/{branchId}/shacl/validate")
    @RequiresPermission("model:form:edit")
    public ApiResponse<Report> validate(@PathVariable Long branchId, @RequestBody ValidateRequest request, Authentication authentication) {
        return ApiResponse.ok(shacl.validate(branchId, request.data(), user(authentication)));
    }

    @GetMapping("/api/v1/sem-runs/{id}/report")
    @RequiresPermission("model:form:edit")
    public ApiResponse<Report> report(@PathVariable Long id) {
        return ApiResponse.ok(shacl.report(id));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record ValidateRequest(@NotBlank String data) {}
}
