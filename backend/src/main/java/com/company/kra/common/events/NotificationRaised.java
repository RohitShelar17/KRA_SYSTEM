package com.company.kra.common.events;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * A domain event asking for people to be told something.
 *
 * <p>Recipients are resolved by the module that raises the event, because that
 * is where the domain knowledge lives: the work-log module knows the reviewing
 * manager, the appraisal module knows whose step is next. The notification
 * module only maps those employees to accounts, renders the configured
 * template and fans out to channels — it never reaches back into another
 * module's data to work out who cares.</p>
 *
 * <p>{@code placeholders} fill the {braces} in the template. A placeholder the
 * template does not use is ignored, and a placeholder the template uses but
 * the event did not supply is left as written rather than printed as
 * "null".</p>
 */
public record NotificationRaised(Long companyId,
                                 String eventKey,
                                 Set<Long> recipientEmployeeIds,
                                 String entityType,
                                 Long entityId,
                                 Map<String, String> placeholders) {

    public NotificationRaised {
        recipientEmployeeIds = recipientEmployeeIds == null
                ? Set.of() : new LinkedHashSet<>(recipientEmployeeIds);
        placeholders = placeholders == null ? Map.of() : new LinkedHashMap<>(placeholders);
    }

    public static Builder to(Long companyId, String eventKey) {
        return new Builder(companyId, eventKey);
    }

    /** Small builder so call sites read as a sentence rather than a long argument list. */
    public static final class Builder {
        private final Long companyId;
        private final String eventKey;
        private final Set<Long> recipients = new LinkedHashSet<>();
        private final Map<String, String> placeholders = new LinkedHashMap<>();
        private String entityType;
        private Long entityId;

        private Builder(Long companyId, String eventKey) {
            this.companyId = companyId;
            this.eventKey = eventKey;
        }

        public Builder recipient(Long employeeId) {
            if (employeeId != null) {
                recipients.add(employeeId);
            }
            return this;
        }

        public Builder recipients(Set<Long> employeeIds) {
            if (employeeIds != null) {
                employeeIds.stream().filter(java.util.Objects::nonNull).forEach(recipients::add);
            }
            return this;
        }

        public Builder about(String type, Long id) {
            this.entityType = type;
            this.entityId = id;
            return this;
        }

        /** A null value is stored as an em dash so a rendered line never reads "null". */
        public Builder with(String key, Object value) {
            placeholders.put(key, value == null ? "—" : String.valueOf(value));
            return this;
        }

        public NotificationRaised build() {
            return new NotificationRaised(companyId, eventKey, recipients, entityType, entityId,
                    placeholders);
        }
    }
}
