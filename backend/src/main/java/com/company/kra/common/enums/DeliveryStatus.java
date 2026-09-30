package com.company.kra.common.enums;

/**
 * The outcome of one delivery attempt on one channel. SKIPPED is deliberate:
 * a channel that is switched off or not configured says so, rather than
 * leaving a gap that looks like a failure.
 */
public enum DeliveryStatus {
    PENDING,
    SENT,
    FAILED,
    SKIPPED
}
