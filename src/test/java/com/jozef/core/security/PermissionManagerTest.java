package com.jozef.core.security;

import com.jozef.core.action.Action;
import com.jozef.core.action.ActionContext;
import com.jozef.core.action.ActionResult;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionManagerTest {

    private static Action actionWith(Set<String> permissions) {
        return new Action() {
            @Override public String name() { return "test"; }
            @Override public String description() { return "test action"; }
            @Override public Set<String> requiredPermissions() { return permissions; }
            @Override public ActionResult execute(ActionContext context) { return ActionResult.ok("ok"); }
        };
    }

    /**
     * Documents the current MVP policy: the PermissionManager is a pass-through
     * (no per-permission grants yet). If this behavior is ever tightened, this
     * test must be updated — it exists to make the change deliberate.
     */
    @Test
    void permissionManagerCurrentlyAllowsEverything() {
        PermissionManager manager = new PermissionManager();

        assertThat(manager.isAllowed(actionWith(Set.of()))).isTrue();
        assertThat(manager.isAllowed(actionWith(Set.of("PC_AUTOMATION")))).isTrue();
        assertThat(manager.isAllowed(actionWith(Set.of("GOOGLE_GMAIL")))).isTrue();
    }
}
