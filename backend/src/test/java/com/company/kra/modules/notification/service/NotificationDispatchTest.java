package com.company.kra.modules.notification.service;

import com.company.kra.common.enums.DeliveryStatus;
import com.company.kra.common.enums.NotificationChannelType;
import com.company.kra.common.enums.NotificationSeverity;
import com.company.kra.common.events.NotificationRaised;
import com.company.kra.modules.employee.service.EmployeeDirectory;
import com.company.kra.modules.notification.channel.EmailChannel;
import org.springframework.beans.factory.ObjectProvider;
import com.company.kra.modules.notification.channel.InAppChannel;
import com.company.kra.modules.notification.entity.Notification;
import com.company.kra.modules.notification.entity.NotificationDelivery;
import com.company.kra.modules.notification.entity.NotificationTemplate;
import com.company.kra.modules.notification.repository.NotificationDeliveryRepository;
import com.company.kra.modules.notification.repository.NotificationRepository;
import com.company.kra.modules.notification.repository.NotificationTemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What happens between an event being raised and somebody actually being told.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationDispatchTest {

    private static final Long COMPANY = 1L;

    @Mock private NotificationRepository notificationRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationDeliveryRepository deliveryRepository;
    @Mock private EmployeeDirectory employeeDirectory;

    private NotificationService service;

    @BeforeEach
    void setUp() {
        service = new NotificationService(notificationRepository, templateRepository,
                deliveryRepository, employeeDirectory, new TemplateRenderer(),
                List.of(new InAppChannel(), emailChannelSwitchedOff()));

        when(notificationRepository.save(any())).thenAnswer(call -> {
            Notification row = call.getArgument(0);
            row.setId(7L);
            return row;
        });
        when(deliveryRepository.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    /** Off, as in this deployment: the delivery row should say so rather than claim a send. */
    private EmailChannel emailChannelSwitchedOff() {
        return new EmailChannel(new SimpleObjectProvider<>(null), false, "",
                "no-reply@company.com", "KRAPulse", "https://kra.example.com");
    }

    /** The provider Spring would supply when no mail sender bean exists. */
    private record SimpleObjectProvider<T>(T value) implements ObjectProvider<T> {
        @Override
        public T getObject() {
            return value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }
    }

    @Test
    @DisplayName("The rendered message is stored per recipient, with the template's severity")
    void rendersAndStores() {
        configure(template("WORKLOG_SUBMITTED", "{employeeName} submitted {workDate}",
                "{employeeName} filed {loggedHours} hours.", true, false));
        accounts(3L);

        int told = service.dispatch(NotificationRaised.to(COMPANY, "WORKLOG_SUBMITTED")
                .recipient(3L)
                .about("WorkLogDay", 99L)
                .with("employeeName", "Priya Nair")
                .with("workDate", "2026-08-12")
                .with("loggedHours", "8.0")
                .build());

        assertThat(told).isEqualTo(1);

        ArgumentCaptor<Notification> saved = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(saved.capture());

        Notification notification = saved.getValue();
        assertThat(notification.getTitle()).isEqualTo("Priya Nair submitted 2026-08-12");
        assertThat(notification.getBody()).isEqualTo("Priya Nair filed 8.0 hours.");
        assertThat(notification.getRecipientUserId()).isEqualTo(30L);
        assertThat(notification.getEntityType()).isEqualTo("WorkLogDay");
        assertThat(notification.getSeverity()).isEqualTo(NotificationSeverity.INFO);
        assertThat(notification.isRead()).isFalse();
    }

    @Test
    @DisplayName("With no template configured nobody is told, and nothing is written")
    void missingTemplateIsNotAFailure() {
        when(templateRepository.findByCompanyIdAndEventKeyAndActiveTrue(anyLong(), anyString()))
                .thenReturn(Optional.empty());

        int told = service.dispatch(NotificationRaised.to(COMPANY, "NOT_CONFIGURED")
                .recipient(3L)
                .build());

        assertThat(told).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("An employee with no user account is skipped rather than half-notified")
    void accountlessEmployeeSkipped() {
        configure(template("TASK_ASSIGNED", "Task", "Body", true, false));
        when(employeeDirectory.accountsOf(any())).thenReturn(Map.of());

        int told = service.dispatch(NotificationRaised.to(COMPANY, "TASK_ASSIGNED")
                .recipient(4L)
                .build());

        assertThat(told).isZero();
        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("Only the channels the template asks for are attempted")
    void inAppOnlyWhenEmailIsOff() {
        configure(template("SCORE_PUBLISHED", "Score", "Body", true, false));
        accounts(3L);

        service.dispatch(NotificationRaised.to(COMPANY, "SCORE_PUBLISHED").recipient(3L).build());

        ArgumentCaptor<NotificationDelivery> saved =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(saved.capture());

        assertThat(saved.getValue().getChannel()).isEqualTo(NotificationChannelType.IN_APP);
        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.SENT);
    }

    @Test
    @DisplayName("An email channel that is switched off records SKIPPED with the reason")
    void switchedOffEmailIsRecorded() {
        configure(template("CYCLE_OPENED", "Cycle", "Body", true, true));
        accounts(3L);

        service.dispatch(NotificationRaised.to(COMPANY, "CYCLE_OPENED").recipient(3L).build());

        ArgumentCaptor<NotificationDelivery> saved =
                ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, org.mockito.Mockito.times(2)).save(saved.capture());

        NotificationDelivery email = saved.getAllValues().stream()
                .filter(row -> row.getChannel() == NotificationChannelType.EMAIL)
                .findFirst()
                .orElseThrow();

        assertThat(email.getStatus()).isEqualTo(DeliveryStatus.SKIPPED);
        assertThat(email.getDetail()).contains("switched off");
        assertThat(email.getSentAt()).isNull();
    }

    @Test
    @DisplayName("An event with no recipients is a no-op")
    void noRecipients() {
        configure(template("EXPORT_READY", "Ready", "Body", true, false));

        assertThat(service.dispatch(NotificationRaised.to(COMPANY, "EXPORT_READY").build())).isZero();
        verify(notificationRepository, never()).save(any());
    }

    // ------------------------------------------------------------- fixtures

    private void configure(NotificationTemplate template) {
        when(templateRepository.findByCompanyIdAndEventKeyAndActiveTrue(anyLong(), anyString()))
                .thenReturn(Optional.of(template));
    }

    private void accounts(Long employeeId) {
        when(employeeDirectory.accountsOf(Set.of(employeeId))).thenReturn(Map.of(employeeId,
                new EmployeeDirectory.Account(employeeId, employeeId * 10, "Someone",
                        "someone@company.com")));
    }

    private NotificationTemplate template(String eventKey, String title, String body,
                                          boolean inApp, boolean email) {
        NotificationTemplate template = new NotificationTemplate();
        template.setCompanyId(COMPANY);
        template.setEventKey(eventKey);
        template.setName(eventKey);
        template.setTitleTemplate(title);
        template.setBodyTemplate(body);
        template.setLinkTemplate("/somewhere");
        template.setSeverity(NotificationSeverity.INFO);
        template.setInApp(inApp);
        template.setEmail(email);
        return template;
    }
}
