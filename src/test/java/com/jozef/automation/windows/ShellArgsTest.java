package com.jozef.automation.windows;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShellArgsTest {

    @Test
    void acceptsPlainValues() {
        assertThat(ShellArgs.requireSafeShellArgument("notepad", "App")).isEqualTo("notepad");
        assertThat(ShellArgs.requireSafeShellArgument("C:\\Program Files\\App\\app.exe", "App"))
                .isEqualTo("C:\\Program Files\\App\\app.exe");
        assertThat(ShellArgs.requireSafeShellArgument("Muzyka - Lista", "App")).isEqualTo("Muzyka - Lista");
    }

    @Test
    void rejectsShellMetacharacters() {
        for (String dangerous : new String[]{"x&y", "x|y", "x;y", "x<y", "x>y", "x^y", "x\"y", "x%y", "x!y"}) {
            assertThatThrownBy(() -> ShellArgs.requireSafeShellArgument(dangerous, "App"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("unsafe character");
        }
    }

    @Test
    void rejectsEmptyOrBlank() {
        assertThatThrownBy(() -> ShellArgs.requireSafeShellArgument(null, "App"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ShellArgs.requireSafeShellArgument("  ", "App"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void escapesPowerShellSingleQuotes() {
        assertThat(ShellArgs.escapePowerShellSingleQuoted("O'Brien"))
                .isEqualTo("O''Brien");
        assertThat(ShellArgs.escapePowerShellSingleQuoted("plain")).isEqualTo("plain");
        assertThat(ShellArgs.escapePowerShellSingleQuoted(null)).isEmpty();
    }
}
