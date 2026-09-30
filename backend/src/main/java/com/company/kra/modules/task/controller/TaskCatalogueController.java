package com.company.kra.modules.task.controller;

import com.company.kra.common.dto.ApiResponse;
import com.company.kra.modules.task.dto.TaskCategoryRequest;
import com.company.kra.modules.task.dto.TaskCategoryResponse;
import com.company.kra.modules.task.dto.TaskTemplateRequest;
import com.company.kra.modules.task.dto.TaskTemplateResponse;
import com.company.kra.modules.task.service.TaskCatalogueService;
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
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Activity catalogue", description = "Categories and department task templates")
public class TaskCatalogueController {

    private final TaskCatalogueService catalogueService;

    // ------------------------------------------------------------ categories

    @GetMapping("/task-categories")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_READ_TEAM','SETTINGS_MANAGE')")
    @Operation(summary = "All activity categories")
    public ResponseEntity<ApiResponse<List<TaskCategoryResponse>>> categories() {
        return ResponseEntity.ok(ApiResponse.of(catalogueService.categories()));
    }

    @GetMapping("/task-categories/available")
    @Operation(summary = "Categories offered to the caller, filtered by their department")
    public ResponseEntity<ApiResponse<List<TaskCategoryResponse>>> availableCategories() {
        return ResponseEntity.ok(ApiResponse.of(catalogueService.categoriesForCaller()));
    }

    @PostMapping("/task-categories")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<TaskCategoryResponse>> createCategory(
            @Valid @RequestBody TaskCategoryRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(catalogueService.createCategory(request), "Category created"));
    }

    @PutMapping("/task-categories/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    @Operation(summary = "Update a category; changing the productive flag affects future scoring")
    public ResponseEntity<ApiResponse<TaskCategoryResponse>> updateCategory(
            @PathVariable Long id, @Valid @RequestBody TaskCategoryRequest request) {
        return ResponseEntity.ok(ApiResponse.of(catalogueService.updateCategory(id, request),
                "Category updated"));
    }

    @DeleteMapping("/task-categories/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivateCategory(@PathVariable Long id) {
        catalogueService.deactivateCategory(id);
        return ResponseEntity.ok(ApiResponse.message("Category deactivated"));
    }

    // ------------------------------------------------------------- templates

    @GetMapping("/task-templates")
    @PreAuthorize("hasAnyAuthority('TASK_READ_SELF','TASK_READ_TEAM','SETTINGS_MANAGE')")
    @Operation(summary = "Task templates, optionally for one department")
    public ResponseEntity<ApiResponse<List<TaskTemplateResponse>>> templates(
            @RequestParam(required = false) Long departmentId) {
        return ResponseEntity.ok(ApiResponse.of(catalogueService.templates(departmentId)));
    }

    @GetMapping("/task-templates/available")
    @Operation(summary = "Templates offered to the caller, based on their department")
    public ResponseEntity<ApiResponse<List<TaskTemplateResponse>>> availableTemplates() {
        return ResponseEntity.ok(ApiResponse.of(catalogueService.templatesForCaller()));
    }

    @PostMapping("/task-templates")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<TaskTemplateResponse>> createTemplate(
            @Valid @RequestBody TaskTemplateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(catalogueService.createTemplate(request), "Template created"));
    }

    @PutMapping("/task-templates/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<TaskTemplateResponse>> updateTemplate(
            @PathVariable Long id, @Valid @RequestBody TaskTemplateRequest request) {
        return ResponseEntity.ok(ApiResponse.of(catalogueService.updateTemplate(id, request),
                "Template updated"));
    }

    @DeleteMapping("/task-templates/{id}")
    @PreAuthorize("hasAuthority('SETTINGS_MANAGE')")
    public ResponseEntity<ApiResponse<Void>> deactivateTemplate(@PathVariable Long id) {
        catalogueService.deactivateTemplate(id);
        return ResponseEntity.ok(ApiResponse.message("Template deactivated"));
    }
}
