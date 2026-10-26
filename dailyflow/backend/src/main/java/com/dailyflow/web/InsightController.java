package com.dailyflow.web;

import com.dailyflow.dto.CommonDtos.ActivityDto;
import com.dailyflow.dto.CommonDtos.PageResponse;
import com.dailyflow.dto.InsightDtos.CalendarItem;
import com.dailyflow.dto.InsightDtos.DashboardDto;
import com.dailyflow.dto.InsightDtos.ProductivityReport;
import com.dailyflow.dto.InsightDtos.ReminderSummary;
import com.dailyflow.security.AppUserDetails;
import com.dailyflow.service.ActivityService;
import com.dailyflow.service.CalendarService;
import com.dailyflow.service.DashboardService;
import com.dailyflow.service.ReminderService;
import com.dailyflow.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Read-only endpoints computed from the caller's persisted data. */
@RestController
@RequestMapping("/api")
@Tag(name = "Dashboard, calendar, reports, reminders and activity")
public class InsightController {

    private final DashboardService dashboardService;
    private final CalendarService calendarService;
    private final ReportService reportService;
    private final ReminderService reminderService;
    private final ActivityService activityService;

    public InsightController(DashboardService dashboardService, CalendarService calendarService,
                             ReportService reportService, ReminderService reminderService,
                             ActivityService activityService) {
        this.dashboardService = dashboardService;
        this.calendarService = calendarService;
        this.reportService = reportService;
        this.reminderService = reminderService;
        this.activityService = activityService;
    }

    @GetMapping("/dashboard")
    public DashboardDto dashboard(@AuthenticationPrincipal AppUserDetails me) {
        return dashboardService.dashboard(me.getId());
    }

    @GetMapping("/calendar")
    @Operation(summary = "Tasks and recurring occurrences due between two dates (inclusive, max 100 days)")
    public List<CalendarItem> calendar(
            @AuthenticationPrincipal AppUserDetails me,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return calendarService.items(me.getId(), from, to);
    }

    @GetMapping("/reports/productivity")
    public ProductivityReport productivity(@AuthenticationPrincipal AppUserDetails me,
                                           @RequestParam(defaultValue = "30") int days) {
        return reportService.report(me.getId(), days);
    }

    @GetMapping("/reminders")
    public ReminderSummary reminders(@AuthenticationPrincipal AppUserDetails me) {
        return reminderService.summary(me.getId());
    }

    @GetMapping("/activity")
    public PageResponse<ActivityDto> activity(@AuthenticationPrincipal AppUserDetails me,
                                              @RequestParam(required = false) Long taskId,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        return activityService.list(me.getId(), taskId, page, size);
    }
}
