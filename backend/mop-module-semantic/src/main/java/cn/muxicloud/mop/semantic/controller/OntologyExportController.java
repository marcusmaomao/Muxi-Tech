package cn.muxicloud.mop.semantic.controller;

import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.semantic.export.OwlDocument;
import cn.muxicloud.mop.semantic.service.OntologyExportService;
import cn.muxicloud.mop.semantic.service.ShaclService;
import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches/{branchId}")
public class OntologyExportController {
    private final OntologyExportService exportService;
    private final ShaclService shaclService;

    public OntologyExportController(OntologyExportService exportService, ShaclService shaclService) {
        this.exportService = exportService;
        this.shaclService = shaclService;
    }

    @GetMapping("/export")
    @RequiresPermission("model:form:edit")
    public ResponseEntity<byte[]> export(@PathVariable Long branchId, @RequestParam(defaultValue = "ttl") String format,
                                         @RequestParam(defaultValue = "false") boolean shapes) {
        if (shapes) {
            byte[] body = shaclService.shapes(branchId).getBytes(StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("text/turtle;charset=UTF-8"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"shapes.ttl\"")
                    .body(body);
        }
        OwlDocument document = exportService.export(branchId, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ontology." + document.extension() + "\"")
                .body(document.body());
    }
}
