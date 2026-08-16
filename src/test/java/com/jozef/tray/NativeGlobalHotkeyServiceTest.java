package com.jozef.tray;

import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NativeGlobalHotkeyServiceTest {

    @Test
    void parsesModifiersAndKey() {
        HotkeySpec spec = NativeGlobalHotkeyService.parse("ctrl alt J");

        assertThat(spec.modifiers()).isEqualTo(NativeKeyEvent.CTRL_MASK | NativeKeyEvent.ALT_MASK);
        assertThat(spec.keyCode()).isEqualTo(NativeKeyEvent.VC_J);
    }

    @Test
    void parsesFunctionAndNamedKeys() {
        assertThat(NativeGlobalHotkeyService.parse("F5").keyCode()).isEqualTo(NativeKeyEvent.VC_F5);
        assertThat(NativeGlobalHotkeyService.parse("ctrl space").keyCode()).isEqualTo(NativeKeyEvent.VC_SPACE);
        assertThat(NativeGlobalHotkeyService.parse("meta enter").keyCode()).isEqualTo(NativeKeyEvent.VC_ENTER);
    }

    @Test
    void rejectsUnknownKey() {
        assertThatThrownBy(() -> NativeGlobalHotkeyService.parse("ctrl bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown key");
    }
}
