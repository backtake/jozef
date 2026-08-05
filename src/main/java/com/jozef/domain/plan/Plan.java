package com.jozef.domain.plan;

import com.jozef.domain.goal.Goal;
import com.jozef.domain.task.Task;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Plan {

    private final Goal goal;
    private final List<Task> tasks;

    Plan(Goal goal, List<Task> tasks) {
        this.goal = goal;
        this.tasks = new ArrayList<>(tasks);
    }

    public Goal goal() {
        return goal;
    }

    public List<Task> tasks() {
        return Collections.unmodifiableList(tasks);
    }

    public void addTask(Task task) {
        tasks.add(task);
    }
}
