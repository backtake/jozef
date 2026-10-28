package com.jozef.automation.windows;

/**
 * Helpers that keep OS commands safe from shell injection.
 *
 * Some desktop actions build {@code cmd.exe} / PowerShell command lines from
 * values that ultimately come from the LLM. These helpers make sure those
 * values cannot smuggle in additional commands.
 */
public final class ShellArgs {

    /** Characters that can alter command parsing in {@code cmd /c}. */
    private static final String FORBIDDEN = "&|;<>^\"%!";

    private ShellArgs() {
    }

    /**
     * Validates a value that will be embedded in a shell command. Returns the
     * value unchanged when safe; throws {@link IllegalArgumentException} when it
     * contains characters that could break out of the intended argument.
     */
    public static String requireSafeShellArgument(String value, String what) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(what + " cannot be empty");
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (FORBIDDEN.indexOf(c) >= 0 || c == '\n' || c == '\r') {
                throw new IllegalArgumentException(
                        what + " contains an unsafe character: '" + c + "'");
            }
        }
        return value;
    }

    /**
     * Escapes a value for safe use inside a single-quoted PowerShell string
     * (PowerShell escapes a literal single quote by doubling it).
     */
    public static String escapePowerShellSingleQuoted(String value) {
        return value == null ? "" : value.replace("'", "''");
    }
}
