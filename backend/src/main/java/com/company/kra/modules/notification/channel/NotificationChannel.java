package com.company.kra.modules.notification.channel;

import com.company.kra.common.enums.NotificationChannelType;
import com.company.kra.modules.notification.entity.Notification;

/**
 * One way of reaching somebody.
 *
 * <p>Adding push, WhatsApp or Teams later is a new implementation of this
 * interface and a column on the template — no change to any service that
 * raises a notification.</p>
 */
public interface NotificationChannel {

    NotificationChannelType type();

    /**
     * Deliver, or explain why not. Implementations must not throw for an
     * expected condition such as "no address on file": that is a SKIPPED
     * outcome, not a failure to retry.
     */
    Outcome deliver(Notification notification, String recipientEmail);

    /** The result of one attempt, with a sentence for the delivery record. */
    record Outcome(boolean delivered, boolean retryable, String detail) {

        public static Outcome sent(String detail) {
            return new Outcome(true, false, detail);
        }

        public static Outcome skipped(String reason) {
            return new Outcome(false, false, reason);
        }

        public static Outcome failed(String reason) {
            return new Outcome(false, true, reason);
        }
    }
}
