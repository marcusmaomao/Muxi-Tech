package cn.muxicloud.mop.semantic.service;

import cn.muxicloud.mop.model.api.BranchCatalog;
import cn.muxicloud.mop.semantic.export.OwlDocument;
import cn.muxicloud.mop.semantic.export.OwlExporter;
import org.springframework.stereotype.Service;

@Service
public class OntologyExportService {
    private final BranchCatalog catalog;
    private final OwlExporter exporter = new OwlExporter();

    public OntologyExportService(BranchCatalog catalog) {
        this.catalog = catalog;
    }

    public OwlDocument export(long branchId, String format) {
        return exporter.export(catalog.snapshot(branchId), format);
    }
}
