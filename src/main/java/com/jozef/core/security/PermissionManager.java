package com.jozef.core.security;

import com.jozef.core.action.Action;

/**
 * Checks whether the runtime is allowed to execute an action.
 *
 * Phase 1 implementation grants all declared permissions by default because
 * there is no user consent UI yet. Later this will consult the permission
 * configuration and user grants.
 */
public class PermissionManager {

    /**
     * Returns true when the action's declared permissions are satisfied.
     */
    public boolean isAllowed(Action action) {
        // MVP: no per-permission grants yet — everything the user configured is allowed.
        // Future: check action.requiredPermissions() against granted permissions.
        return true;
    }
}
