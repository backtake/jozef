package com.jozef.planning;

import java.util.List;
import java.util.Optional;

/**
 * Storage for {@link Plan} objects.
 *
 * Implementations can be in-memory (session-scoped) or file-backed so plans
 * survive restarts.
 */
public interface PlanStore {

    /** Store (or replace) a plan. */
    void put(Plan plan);

    /** Find a plan by id. */
    Optional<Plan> get(String id);

    /** All stored plans. */
    List<Plan> all();
}
