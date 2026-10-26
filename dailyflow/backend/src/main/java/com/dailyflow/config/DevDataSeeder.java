package com.dailyflow.config;

import com.dailyflow.domain.Priority;
import com.dailyflow.domain.RecurrenceType;
import com.dailyflow.domain.TaskStatus;
import com.dailyflow.dto.AuthDtos.AuthResponse;
import com.dailyflow.dto.AuthDtos.RegisterRequest;
import com.dailyflow.dto.CategoryDtos.CategoryDto;
import com.dailyflow.dto.TaskDtos.TaskRequest;
import com.dailyflow.repository.UserRepository;
import com.dailyflow.service.AuthService;
import com.dailyflow.service.CategoryService;
import com.dailyflow.service.TaskService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development sample data. Only runs with the "dev" Spring profile (SPRING_PROFILES_ACTIVE=dev),
 * so it never executes in production. Creates one demo account with a few tasks.
 */
@Component
@Profile("dev")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final UserRepository userRepository;
    private final AuthService authService;
    private final CategoryService categoryService;
    private final TaskService taskService;
    private final String email;
    private final String password;

    public DevDataSeeder(UserRepository userRepository, AuthService authService, CategoryService categoryService,
                         TaskService taskService,
                         @Value("${dailyflow.seed.email:demo@dailyflow.local}") String email,
                         @Value("${dailyflow.seed.password:Demo12345}") String password) {
        this.userRepository = userRepository;
        this.authService = authService;
        this.categoryService = categoryService;
        this.taskService = taskService;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByEmail(email)) {
            return;
        }
        AuthResponse registered = authService.register(new RegisterRequest("Demo User", email, password));
        Long userId = registered.user().id();
        List<CategoryDto> categories = categoryService.list(userId);

        LocalDateTime today = LocalDate.now().atTime(18, 0);
        create(userId, "Solve 3 graph problems", categories, "GATE 2027", Priority.HIGH, today, null);
        create(userId, "Review Spring Security notes", categories, "Java Interview Preparation", Priority.MEDIUM,
                today.plusDays(1), null);
        create(userId, "Apply to two companies", categories, "Job Applications", Priority.HIGH,
                today.minusDays(1), null);
        create(userId, "Morning walk", categories, "Health and Fitness", Priority.LOW, today.withHour(7),
                RecurrenceType.DAILY);
        log.info("Dev seed data created for {}", email);
    }

    private void create(Long userId, String title, List<CategoryDto> categories, String categoryName,
                        Priority priority, LocalDateTime due, RecurrenceType recurrence) {
        Long categoryId = categories.stream().filter(c -> c.name().equals(categoryName))
                .map(CategoryDto::id).findFirst().orElse(null);
        taskService.create(userId, new TaskRequest(title, null, categoryId, priority, TaskStatus.TODO, null, due,
                recurrence, null, null));
    }
}
