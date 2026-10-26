package com.dailyflow.service;

import com.dailyflow.config.AppProperties;
import com.dailyflow.domain.Task;
import com.dailyflow.domain.User;
import com.dailyflow.repository.TaskRepository;
import com.dailyflow.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Optional email reminders. The bean only exists when SPRING_MAIL_HOST (spring.mail.host) is configured.
 * Applies to one-off tasks for users who enabled email reminders; each task is emailed at most once per due date
 * (reminder_sent_at is cleared when the due date changes). Emails are only sent while this server is running.
 */
@Component
@ConditionalOnProperty(name = "spring.mail.host")
public class ReminderEmailScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReminderEmailScheduler.class);

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final JavaMailSender mailSender;
    private final AppProperties props;
    private final Clock clock;

    public ReminderEmailScheduler(UserRepository userRepository, TaskRepository taskRepository,
                                  JavaMailSender mailSender, AppProperties props, Clock clock) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.mailSender = mailSender;
        this.props = props;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${dailyflow.reminders.email-check-interval-ms:60000}")
    @Transactional
    public void sendDueReminders() {
        LocalDateTime now = LocalDateTime.now(clock);
        for (User user : userRepository.findByEmailRemindersEnabledTrue()) {
            List<Task> due = taskRepository.findDueForReminder(user.getId(), now,
                    now.plusMinutes(user.getReminderLeadMinutes()));
            for (Task task : due) {
                try {
                    SimpleMailMessage message = new SimpleMailMessage();
                    message.setFrom(props.mail().from());
                    message.setTo(user.getEmail());
                    message.setSubject("Reminder: " + task.getTitle());
                    message.setText("Hi " + user.getDisplayName() + ",\n\n\"" + task.getTitle()
                            + "\" is due at " + task.getDueAt() + ".\n\n— DailyFlow");
                    mailSender.send(message);
                    task.setReminderSentAt(now);
                } catch (MailException ex) {
                    // Not marked as sent, so it is retried on the next run. Message content is not logged.
                    log.warn("Could not send reminder email for task {}: {}", task.getId(),
                            ex.getClass().getSimpleName());
                }
            }
        }
    }
}
