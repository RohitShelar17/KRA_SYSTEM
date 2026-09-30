package com.company.kra.modules.notification.channel;

import com.company.kra.common.enums.NotificationChannelType;
import com.company.kra.modules.notification.entity.Notification;
import org.springframework.stereotype.Component;

/**
 * The in-app feed. Writing the notification row <em>is</em> the delivery, so
 * this channel has nothing left to do but record that the row exists.
 */
@Component
public class InAppChannel implements NotificationChannel {

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.IN_APP;
    }

    @Override
    public Outcome deliver(Notification notification, String recipientEmail) {
        return Outcome.sent("Placed in the recipient's feed");
    }
}
