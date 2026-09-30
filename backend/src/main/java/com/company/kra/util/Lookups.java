package com.company.kra.util;

import java.util.Map;

/**
 * Name resolution for nullable foreign keys.
 *
 * <p>Most of the maps in this codebase are built from a set of ids that had the
 * nulls filtered out, then read back with a key that may itself be null — an
 * unassigned department head, a project with no manager. {@code Map.of()}
 * throws on a null key, so the lookup is done through here instead of leaving
 * a null-check to be remembered at every call site.</p>
 */
public final class Lookups {

    private Lookups() {
    }

    public static <K, V> V get(Map<K, V> map, K key) {
        if (map == null || key == null) {
            return null;
        }
        return map.get(key);
    }
}
