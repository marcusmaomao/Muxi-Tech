package cn.muxicloud.mop.basic.controller;

import cn.muxicloud.mop.basic.datasource.JdbcProbe;
import cn.muxicloud.mop.basic.service.DatasourceService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/datasources")
public class DatasourceController {
    private final DatasourceService datasourceService;

    public DatasourceController(DatasourceService datasourceService) {
        this.datasourceService = datasourceService;
    }

    @GetMapping
    @RequiresPermission("basic:datasource:list")
    public ApiResponse<List<DatasourceService.DatasourceView>> list() {
        return ApiResponse.ok(datasourceService.list());
    }

    @PostMapping
    @RequiresPermission("basic:datasource:create")
    public ApiResponse<DatasourceService.DatasourceView> create(@RequestBody DatasourceRequest request, Authentication authentication) {
        return ApiResponse.ok(datasourceService.create(request.toInput(), user(authentication)));
    }

    @PutMapping("/{id}")
    @RequiresPermission("basic:datasource:update")
    public ApiResponse<DatasourceService.DatasourceView> update(@PathVariable Long id, @RequestBody DatasourceRequest request, Authentication authentication) {
        return ApiResponse.ok(datasourceService.update(id, request.toInput(), user(authentication)));
    }

    @DeleteMapping("/{id}")
    @RequiresPermission("basic:datasource:delete")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        datasourceService.delete(id);
        return ApiResponse.ok(null);
    }

    @PostMapping("/{id}/test")
    @RequiresPermission("basic:datasource:test")
    public ApiResponse<JdbcProbe.ProbeResult> test(@PathVariable Long id) {
        return ApiResponse.ok(datasourceService.test(id));
    }

    @GetMapping("/{id}/tables")
    @RequiresPermission("basic:datasource:list")
    public ApiResponse<List<DatasourceService.TableView>> tables(@PathVariable Long id) {
        return ApiResponse.ok(datasourceService.tables(id));
    }

    @GetMapping("/{id}/tables/{table}/columns")
    @RequiresPermission("basic:datasource:list")
    public ApiResponse<List<DatasourceService.ColumnView>> columns(@PathVariable Long id, @PathVariable String table) {
        return ApiResponse.ok(datasourceService.columns(id, table));
    }

    @GetMapping("/{id}/tables/{table}/foreign-keys")
    @RequiresPermission("basic:datasource:list")
    public ApiResponse<List<cn.muxicloud.mop.basic.api.SourceReader.ForeignKey>> foreignKeys(@PathVariable Long id, @PathVariable String table) {
        return ApiResponse.ok(datasourceService.foreignKeys(id, table));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }

    public record DatasourceRequest(
            Long projectId,
            @NotBlank String name,
            @NotBlank String dbType,
            @NotBlank String host,
            Integer port,
            @NotBlank String databaseName,
            @NotBlank String username,
            String password) {
        DatasourceService.DatasourceInput toInput() {
            return new DatasourceService.DatasourceInput(projectId, name, dbType, host, port, databaseName, username, password);
        }
    }
}
