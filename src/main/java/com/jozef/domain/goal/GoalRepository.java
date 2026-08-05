package com.jozef.domain.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GoalRepository {

    Goal save(Goal goal);

    Optional<Goal> findById(UUID id);

    List<Goal> findAll();
}
