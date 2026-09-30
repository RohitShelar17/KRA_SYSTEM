package com.company.kra.modules.notification.channel;

import com.company.kra.common.enums.NotificationChannelType;
import com.company.kra.modules.notification.entity.Notification;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;

/**
 * Email delivery.
 *
 * <p>Three outcomes, kept distinct rather than collapsed into one: switched
 * off is SKIPPED, switched on with no mail server configured is FAILED (a
 * mistake somebody needs to see), and a mail server that refuses is FAILED
 * carrying what it said. A delivery row that reads "sent" must mean it.</p>
 *
 * <p>The sender arrives as an {@link ObjectProvider} because Spring only
 * creates a {@code JavaMailSender} when {@code spring.mail.host} is set. A
 * deployment without SMTP therefore starts normally and explains itself on
 * every delivery row, instead of failing to boot over a message nobody has
 * asked it to send yet. The host is checked here as well, because an unset
 * MAIL_HOST still defines the property as an empty string and Spring reads
 * that as configured — leaving a sender aimed at nothing, whose failure would
 * otherwise be reported as a connection error rather than as the missing
 * setting it actually is.</p>
 */
@Slf4j
@Component
public class EmailChannel implements NotificationChannel {

    private static final int MAX_DETAIL = 400;

    private final ObjectProvider<JavaMailSender> mailSender;
    private final boolean enabled;
    private final String host;
    private final String from;
    private final String fromName;
    private final String baseUrl;

    public EmailChannel(ObjectProvider<JavaMailSender> mailSender,
                        @Value("${app.notifications.email.enabled:false}") boolean enabled,
                        @Value("${spring.mail.host:}") String host,
                        @Value("${app.notifications.email.from:no-reply@company.com}") String from,
                        @Value("${app.notifications.email.from-name:KRAPulse}") String fromName,
                        @Value("${app.base-url:}") String baseUrl) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.host = host == null ? "" : host.trim();
        this.from = from;
        this.fromName = fromName;
        this.baseUrl = trimTrailingSlash(baseUrl);
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.EMAIL;
    }

    @Override
    public Outcome deliver(Notification notification, String recipientEmail) {
        if (!enabled) {
            return Outcome.skipped("Email delivery is switched off for this deployment");
        }
        if (!StringUtils.hasText(recipientEmail)) {
            return Outcome.skipped("No email address on file for this account");
        }

        JavaMailSender sender = StringUtils.hasText(host) ? mailSender.getIfAvailable() : null;
        if (sender == null) {
            // Retryable, because the fix is configuration rather than the
            // message: set MAIL_HOST and this same row's cause is gone.
            log.warn("Email is enabled but spring.mail.host is not set; notification {} not sent",
                    notification.getId());
            return Outcome.failed("Email is enabled but no mail server is configured");
        }

        try {
            sender.send(compose(notification, recipientEmail));
            return Outcome.sent("Emailed to " + recipientEmail);
        } catch (MailException failure) {
            // Anything the mail server refuses is worth retrying later: a
            // greylist, a timeout and a full mailbox all look like this.
            log.warn("Mail server refused notification {}: {}", notification.getId(),
                    failure.getMessage());
            return Outcome.failed(shorten(failure.getMessage()));
        }
    }

    private SimpleMailMessage compose(Notification notification, String recipientEmail) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender());
        message.setTo(recipientEmail);
        message.setSubject(notification.getTitle());
        message.setText(body(notification));
        return message;
    }

    /**
     * Plain text on purpose. The wording is already editable in the template
     * rows; an HTML layer here would be a second place to edit the same
     * sentence, and plain text renders in every client without one.
     */
    private String body(Notification notification) {
        StringBuilder text = new StringBuilder(notification.getBody());
        String link = absolute(notification.getLink());
        if (link != null) {
            text.append("\n\n").append(link);
        }
        return text.append("\n\n—\n").append(fromName)
                .append("\nThis message was sent automatically. Please do not reply.")
                .toString();
    }

    /**
     * Notification links are stored as paths, because that is what works
     * inside the app. An email is read outside it, so the path has to be
     * completed — and a link that is already absolute is left alone.
     */
    private String absolute(String link) {
        if (!StringUtils.hasText(link)) {
            return null;
        }
        if (link.startsWith("http://") || link.startsWith("https://")) {
            return link;
        }
        if (!StringUtils.hasText(baseUrl)) {
            // Better no link than a broken one: a bare path in a mail client
            // resolves against nothing.
            return null;
        }
        return baseUrl + (link.startsWith("/") ? link : "/" + link);
    }

    private String sender() {
        try {
            return new InternetAddress(from, fromName, StandardCharsets.UTF_8.name()).toString();
        } catch (UnsupportedEncodingException unreachable) {
            // UTF-8 is always present; fall back to the bare address rather
            // than losing a delivery over a display name.
            return from;
        }
    }

    private static String shorten(String message) {
        String text = StringUtils.hasText(message) ? message : "The mail server refused the message";
        return text.length() <= MAX_DETAIL ? text : text.substring(0, MAX_DETAIL - 3) + "...";
    }

    private static String trimTrailingSlash(String url) {
        String trimmed = url == null ? "" : url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
