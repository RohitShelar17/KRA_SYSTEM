package com.company.kra.modules.kra.service;

import java.time.LocalDate;

/**
 * What other modules may ask of an assigned KRA.
 *
 * <p>The work log lets an employee attribute a time slot to a result area,
 * which is how a day's hours reach a KRA in Phase 4. It needs exactly two
 * things: is this KRA really theirs and open on that date, and what is it
 * called.</p>
 */
public interface KraLookup {

    record KraRef(Long id, String name) {
    }

    /** Resolves an assigned KRA, refusing one that belongs to someone else or is closed. */
    KraRef requireAssigned(Long employeeKraId, Long employeeId, LocalDate onDate);

    /** Display name, or null when the id no longer resolves. */
    String nameOf(Long employeeKraId);
}
