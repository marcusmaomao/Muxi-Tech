package cn.muxicloud.mop.model.controller;

import cn.muxicloud.mop.model.entity.ElementEntity;
import cn.muxicloud.mop.model.service.ModelService;
import cn.muxicloud.mop.security.AuthenticatedUser;
import cn.muxicloud.mop.security.RequiresPermission;
import cn.muxicloud.mop.web.api.ApiResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/branches/{branchId}")
public class FormController {
    private final ModelService modelService;

    public FormController(ModelService modelService) {
        this.modelService = modelService;
    }

    @PostMapping("/terms")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createTerm(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "TERM", payload, authentication);
    }

    @PostMapping("/data-types")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createDataType(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "DATA_TYPE", payload, authentication);
    }

    @PostMapping("/enums")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createEnum(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "ENUM_TYPE", payload, authentication);
    }

    @PostMapping("/enum-values")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createEnumValue(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "ENUM_VALUE", payload, authentication);
    }

    @PostMapping("/cqs")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createCq(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "CQ", payload, authentication);
    }

    @PostMapping("/object-types")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createObject(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "OBJECT_TYPE", payload, authentication);
    }

    @PutMapping("/object-types/{uid}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> updateObject(@PathVariable Long branchId, @PathVariable String uid, @RequestBody JsonNode payload, Authentication authentication) {
        return ApiResponse.ok(modelService.saveElement(branchId, "MODIFY", uid, "OBJECT_TYPE", payload, user(authentication)));
    }

    @PostMapping("/properties")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createProperty(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "PROPERTY", payload, authentication);
    }

    @PostMapping("/link-types")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createLink(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "LINK_TYPE", payload, authentication);
    }

    @PostMapping("/axioms")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createAxiom(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "AXIOM", payload, authentication);
    }

    @PostMapping("/data-rules")
    @RequiresPermission("model:form:edit")
    public ApiResponse<ElementEntity> createRule(@PathVariable Long branchId, @RequestBody JsonNode payload, Authentication authentication) {
        return add(branchId, "DATA_RULE", payload, authentication);
    }

    @DeleteMapping("/elements/{uid}")
    @RequiresPermission("model:form:edit")
    public ApiResponse<Void> delete(@PathVariable Long branchId, @PathVariable String uid, Authentication authentication) {
        modelService.saveElement(branchId, "DELETE", uid, null, null, user(authentication));
        return ApiResponse.ok(null);
    }

    private ApiResponse<ElementEntity> add(Long branchId, String type, JsonNode payload, Authentication authentication) {
        return ApiResponse.ok(modelService.saveElement(branchId, "ADD", null, type, payload, user(authentication)));
    }

    private static AuthenticatedUser user(Authentication authentication) {
        return (AuthenticatedUser) authentication.getPrincipal();
    }
}
