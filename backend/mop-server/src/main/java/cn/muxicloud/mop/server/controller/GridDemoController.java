package cn.muxicloud.mop.server.controller;

import cn.muxicloud.mop.common.error.ErrorCode;
import cn.muxicloud.mop.common.error.MopException;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.system.seed.DemoSeedProperties;
import cn.muxicloud.mop.web.api.ApiResponse;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/demo/grid")
public class GridDemoController {
    private final DemoSeedProperties demo;
    private final JdbcTemplate jdbc;

    public GridDemoController(DemoSeedProperties demo, JdbcTemplate jdbc) {
        this.demo = demo;
        this.jdbc = jdbc;
    }

    @PostMapping("/inspections")
    @RequiresPermission("runtime:sync")
    public ApiResponse<Map<String, String>> inspection(@RequestBody InspectionRequest request) {
        if (!demo.isGrid()) {
            throw new MopException(ErrorCode.NOT_FOUND);
        }
        if (request.id() == null || !request.id().matches("[A-Za-z0-9_-]{1,32}") || request.note() == null || request.note().isBlank()) {
            throw new MopException(ErrorCode.VALIDATION, "请填写合法的巡检编号和结论", null);
        }
        jdbc.update("INSERT INTO src_inspection (id, transformer_id, note, updated_at) VALUES (?, 'e1', ?, '2026-12-01 00:00:00')", request.id(), request.note().trim());
        return ApiResponse.ok(Map.of("id", request.id(), "note", request.note().trim()));
    }

    public record InspectionRequest(String id, String note) {}
}
