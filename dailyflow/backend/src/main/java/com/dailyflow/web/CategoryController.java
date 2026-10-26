package com.dailyflow.web;

import com.dailyflow.dto.CategoryDtos.CategoryDto;
import com.dailyflow.dto.CategoryDtos.CategoryRequest;
import com.dailyflow.security.AppUserDetails;
import com.dailyflow.service.CategoryService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public List<CategoryDto> list(@AuthenticationPrincipal AppUserDetails me) {
        return categoryService.list(me.getId());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryDto create(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody CategoryRequest request) {
        return categoryService.create(me.getId(), request);
    }

    @PutMapping("/{id}")
    public CategoryDto update(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id,
                              @Valid @RequestBody CategoryRequest request) {
        return categoryService.update(me.getId(), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        categoryService.delete(me.getId(), id);
    }
}
