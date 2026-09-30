package com.company.kra.modules.notification.channel;

import com.company.kra.modules.notification.entity.Notification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A delivery row that says SENT has to mean it.
 *
 * <p>The distinction this pins down is between the three ways an email does
 * not go out: switched off on purpose, misconfigured, and refused by the mail
 * server. Collapsing them would leave an operator unable to tell a quiet
 * deployment from a broken one.</p>
 */
class EmailChannelTest {

    private static final String BASE = "https://kra.example.com";

    @Test
    @DisplayName("Switched off: skipped, and no mail server is touched")
    void disabledSkips() {
        JavaMailSender sender = mock(JavaMailSender.class);
        NotificationChannel.Outcome outcome =
                channel(sender, false).deliver(notification(), "priya@company.com");

        assertThat(outcome.delivered()).isFalse();
        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.detail()).contains("switched off");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("No address on file: skipped, not failed — there is nothing to retry")
    void noAddressSkips() {
        NotificationChannel.Outcome outcome =
                channel(mock(JavaMailSender.class), true).deliver(notification(), "  ");

        assertThat(outcome.delivered()).isFalse();
        assertThat(outcome.retryable()).isFalse();
        assertThat(outcome.detail()).contains("No email address");
    }

    @Test
    @DisplayName("Enabled with no mail server: failed, because somebody has to notice")
    void enabledWithoutSenderFails() {
        NotificationChannel.Outcome outcome =
                channel(null, true).deliver(notification(), "priya@company.com");

        assertThat(outcome.delivered()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.detail()).contains("no mail server is configured");
        // Not SKIPPED: switching email on and then not configuring it is a
        // mistake, and a quiet skip is how it would survive to production.
    }

    @Test
    @DisplayName("A blank host reads as unconfigured, even though Spring still built a sender")
    void blankHostIsTreatedAsUnconfigured() {
        // An unset MAIL_HOST leaves spring.mail.host defined but empty, so the
        // auto-configuration supplies a sender pointed at nothing. Asking only
        // whether the bean exists would turn a missing setting into a
        // connection error, and send nobody looking at the right thing.
        JavaMailSender sender = mock(JavaMailSender.class);

        NotificationChannel.Outcome outcome =
                new EmailChannel(provider(sender), true, "  ", "no-reply@company.com", "KRAPulse", BASE)
                        .deliver(notification(), "priya@company.com");

        assertThat(outcome.delivered()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.detail()).contains("no mail server is configured");
        verify(sender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("A sent message carries the subject, the body and an absolute link")
    void sendsTheMessage() {
        JavaMailSender sender = mock(JavaMailSender.class);

        NotificationChannel.Outcome outcome =
                channel(sender, true).deliver(notification(), "priya@company.com");

        assertThat(outcome.delivered()).isTrue();
        assertThat(outcome.detail()).contains("priya@company.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        SimpleMailMessage message = captor.getValue();

        assertThat(message.getTo()).containsExactly("priya@company.com");
        assertThat(message.getSubject()).isEqualTo("Set your password for KRAPulse");
        assertThat(message.getFrom()).contains("no-reply@company.com");
        // The stored link is a path; an inbox is outside the app, so it has to
        // arrive complete.
        assertThat(message.getText()).contains(BASE + "/reset-password?token=abc123");
        assertThat(message.getText()).doesNotContain("\n/reset-password");
    }

    @Test
    @DisplayName("An already absolute link is left alone rather than doubled up")
    void absoluteLinkIsNotPrefixed() {
        JavaMailSender sender = mock(JavaMailSender.class);
        Notification notification = notification();
        notification.setLink("https://elsewhere.example.com/thing");

        channel(sender, true).deliver(notification, "priya@company.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getText())
                .contains("https://elsewhere.example.com/thing")
                .doesNotContain(BASE + "/https");
    }

    @Test
    @DisplayName("With no base URL the link is dropped rather than sent broken")
    void bareLinkIsDroppedWithoutABaseUrl() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new EmailChannel(provider(sender), true, "smtp.example.com", "no-reply@company.com",
                "KRAPulse", "").deliver(notification(), "priya@company.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getText()).doesNotContain("/reset-password");
    }

    @Test
    @DisplayName("A mail server that refuses is failed, carrying what it said")
    void refusedSendFails() {
        JavaMailSender sender = mock(JavaMailSender.class);
        doThrow(new MailSendException("Mailbox full")).when(sender).send(any(SimpleMailMessage.class));

        NotificationChannel.Outcome outcome =
                channel(sender, true).deliver(notification(), "priya@company.com");

        assertThat(outcome.delivered()).isFalse();
        assertThat(outcome.retryable()).isTrue();
        assertThat(outcome.detail()).contains("Mailbox full");
    }

    @Test
    @DisplayName("A trailing slash on the base URL does not double up in the link")
    void trailingSlashIsTrimmed() {
        JavaMailSender sender = mock(JavaMailSender.class);

        new EmailChannel(provider(sender), true, "smtp.example.com", "no-reply@company.com",
                "KRAPulse", BASE + "/").deliver(notification(), "priya@company.com");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender).send(captor.capture());
        assertThat(captor.getValue().getText()).contains(BASE + "/reset-password?token=abc123");
    }

    private EmailChannel channel(JavaMailSender sender, boolean enabled) {
        return new EmailChannel(provider(sender), enabled, "smtp.example.com",
                "no-reply@company.com", "KRAPulse", BASE);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<JavaMailSender> provider(JavaMailSender sender) {
        ObjectProvider<JavaMailSender> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }

    private Notification notification() {
        Notification notification = new Notification();
        notification.setId(42L);
        notification.setTitle("Set your password for KRAPulse");
        notification.setBody("An account has been created for you.");
        notification.setLink("/reset-password?token=abc123");
        return notification;
    }
}
