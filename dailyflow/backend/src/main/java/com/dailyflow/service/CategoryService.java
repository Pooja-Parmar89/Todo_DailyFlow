package com.dailyflow.service;

import com.dailyflow.domain.Category;
import com.dailyflow.dto.CategoryDtos.CategoryDto;
import com.dailyflow.dto.CategoryDtos.CategoryRequest;
import com.dailyflow.exception.ConflictException;
import com.dailyflow.exception.NotFoundException;
import com.dailyflow.mapper.TaskMapper;
import com.dailyflow.repository.CategoryRepository;
import com.dailyflow.repository.TaskRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    static final String DEFAULT_COLOR = "#6366f1";
    static final String DEFAULT_ICON = "tag";

    private record DefaultCategory(String name, String color, String icon) {
    }

    /** Created for every new account. They can be renamed, recolored and deleted like any other category. */
    private static final List<DefaultCategory> DEFAULTS = List.of(
            new DefaultCategory("GATE 2027", "#6366f1", "graduation-cap"),
            new DefaultCategory("INFLIBNET / Office Work", "#0ea5e9", "building"),
            new DefaultCategory("Java Interview Preparation", "#f59e0b", "code"),
            new DefaultCategory("Job Applications", "#10b981", "send"),
            new DefaultCategory("Instagram / YouTube", "#ec4899", "video"),
            new DefaultCategory("Personal Growth", "#8b5cf6", "sprout"),
            new DefaultCategory("Health and Fitness", "#22c55e", "heart-pulse"),
            new DefaultCategory("English Practice", "#14b8a6", "message-circle"),
            new DefaultCategory("Other", "#64748b", "tag"));

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;
    private final TaskMapper mapper;

    public CategoryService(CategoryRepository categoryRepository, TaskRepository taskRepository, TaskMapper mapper) {
        this.categoryRepository = categoryRepository;
        this.taskRepository = taskRepository;
        this.mapper = mapper;
    }

    @Transactional
    public void createDefaults(Long userId) {
        for (DefaultCategory d : DEFAULTS) {
            categoryRepository.save(new Category(userId, d.name(), d.color(), d.icon(), true));
        }
    }

    @Transactional(readOnly = true)
    public List<CategoryDto> list(Long userId) {
        Map<Long, Long> counts = new HashMap<>();
        taskRepository.countByCategory(userId).forEach(row -> counts.put(row.getCategoryId(), row.getTotal()));
        return categoryRepository.findByUserIdOrderByNameAsc(userId).stream()
                .map(c -> mapper.toDto(c, counts.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CategoryDto create(Long userId, CategoryRequest request) {
        String name = request.name().trim();
        if (categoryRepository.existsByUserIdAndNameIgnoreCase(userId, name)) {
            throw new ConflictException("A category named \"" + name + "\" already exists");
        }
        Category saved = categoryRepository.save(new Category(userId, name,
                orDefault(request.color(), DEFAULT_COLOR), orDefault(request.icon(), DEFAULT_ICON), false));
        return mapper.toDto(saved, 0);
    }

    @Transactional
    public CategoryDto update(Long userId, Long id, CategoryRequest request) {
        Category category = load(userId, id);
        String name = request.name().trim();
        if (categoryRepository.existsByUserIdAndNameIgnoreCaseAndIdNot(userId, name, id)) {
            throw new ConflictException("A category named \"" + name + "\" already exists");
        }
        category.setName(name);
        category.setColor(orDefault(request.color(), category.getColor()));
        category.setIcon(orDefault(request.icon(), category.getIcon()));
        return mapper.toDto(category, taskRepository.countByUserIdAndCategoryId(userId, id));
    }

    @Transactional
    public void delete(Long userId, Long id) {
        Category category = load(userId, id);
        long used = taskRepository.countByUserIdAndCategoryId(userId, id);
        if (used > 0) {
            throw new ConflictException("This category is used by " + used
                    + (used == 1 ? " task" : " tasks") + ". Move or delete those tasks first.");
        }
        categoryRepository.delete(category);
    }

    private Category load(Long userId, Long id) {
        return categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new NotFoundException("Category not found"));
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
