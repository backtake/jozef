package com.jozef.planning;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store of plans (session-scoped; no database).
 */
public class PlanStore {

    private final Map<String, Plan> plans = new ConcurrentHashMap<>();

    public void put(Plan plan) {
        plans.put(plan.getId(), plan);
    }

    public Optional<Plan> get(String id) {
        return Optional.ofNullable(plans.get(id));
    }

    public List<Plan> all() {
        return List.copyOf(plans.values());
    }
}
