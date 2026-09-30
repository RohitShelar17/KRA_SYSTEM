package com.company.kra.modules.kra.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.common.dto.OptionDto;
import com.company.kra.modules.kra.dto.KraTemplateRequest;
import com.company.kra.modules.kra.dto.KraTemplateResponse;
import com.company.kra.modules.kra.service.KraTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/kra-templates")
@RequiredArgsConstructor
@Tag(name = "KRA templates", description = "What each role is measured on")
public class KraTemplateController {

    private final KraTemplateService templateService;

    @GetMapping
    @PreAuthorize("hasAnyAuthority('KRA_TEMPLATE_MANAGE','KRA_ASSIGN','KRA_READ_TEAM')")
    public ResponseEntity<ApiResponse<List<KraTemplateResponse>>> list() {
        return ResponseEntity.ok(ApiResponse.of(templateService.list()));
    }

    @GetMapping("/assignable")
    @PreAuthorize("hasAuthority('KRA_ASSIGN')")
    @Operation(summary = "Published templates suitable for a designation")
    public ResponseEntity<ApiResponse<List<OptionDto>>> assignable(
            @RequestParam(required = false) Long designationId) {
        return ResponseEntity.ok(ApiResponse.of(templateService.assignableFor(designationId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyAuthority('KRA_TEMPLATE_MANAGE','KRA_ASSIGN','KRA_READ_TEAM')")
    @Operation(summary = "One template with its KRAs and KPIs")
    public ResponseEntity<ApiResponse<KraTemplateResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.of(templateService.get(id)));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('KRA_TEMPLATE_MANAGE')")
    @Operation(summary = "Create a template; KRA and KPI weightages must each total 100%")
    public ResponseEntity<ApiResponse<KraTemplateResponse>> create(
            @Valid @RequestBody KraTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(templateService.create(request), "Template created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('KRA_TEMPLATE_MANAGE')")
    public ResponseEntity<ApiResponse<KraTemplateResponse>> update(
            @PathVariable Long id, @Valid @RequestBody KraTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(templateService.update(id, request), "Template updated"));
    }

    @PostMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('KRA_TEMPLATE_MANAGE')")
    @Operation(summary = "Publish or unpublish; only a published template can be assigned")
    public ResponseEntity<ApiResponse<KraTemplateResponse>> publish(
            @PathVariable Long id, @RequestParam(defaultValue = "true") boolean published) {
        return ResponseEntity.ok(ApiResponse.of(templateService.setPublished(id, published),
                published ? "Template published" : "Template unpublished"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('KRA_TEMPLATE_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivate(@PathVariable Long id) {
        templateService.deactivate(id);
        return ResponseEntity.ok(ApiResponse.message("Template deactivated"));
    }
}
