package cn.muxicloud.mop.semantic.controller;

import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.semantic.export.OwlDocument;
import cn.muxicloud.mop.semantic.service.OntologyExportService;
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

    public OntologyExportController(OntologyExportService exportService) {
        this.exportService = exportService;
    }

    @GetMapping("/export")
    @RequiresPermission("model:form:edit")
    public ResponseEntity<byte[]> export(@PathVariable Long branchId, @RequestParam(defaultValue = "ttl") String format) {
        OwlDocument document = exportService.export(branchId, format);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(document.mediaType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"ontology." + document.extension() + "\"")
                .body(document.body());
    }
}
