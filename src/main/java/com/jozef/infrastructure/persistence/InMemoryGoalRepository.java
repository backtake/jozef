package com.jozef.infrastructure.persistence;

import com.jozef.domain.goal.Goal;
import com.jozef.domain.goal.GoalRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryGoalRepository implements GoalRepository {

    private final ConcurrentHashMap<UUID, Goal> store = new ConcurrentHashMap<>();

    @Override
    public Goal save(Goal goal) {
        store.put(goal.id(), goal);
        return goal;
    }

    @Override
    public Optional<Goal> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Goal> findAll() {
        return new ArrayList<>(store.values());
    }
}
