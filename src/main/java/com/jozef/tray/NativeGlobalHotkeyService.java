package com.jozef.tray;

import lombok.extern.slf4j.Slf4j;
import com.github.kwhat.jnativehook.GlobalScreen;
import com.github.kwhat.jnativehook.NativeHookException;
import com.github.kwhat.jnativehook.keyboard.NativeKeyEvent;
import com.github.kwhat.jnativehook.keyboard.NativeKeyListener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

/**
 * Global hotkey backed by JNativeHook. The native hook is registered lazily and
 * failure is non-fatal (the agent keeps running without the hotkey).
 */
@Slf4j
public class NativeGlobalHotkeyService implements GlobalHotkeyService {

    private final HotkeySpec spec;
    private final Runnable action;

    private NativeKeyListener listener;
    private volatile boolean registered = false;

    public NativeGlobalHotkeyService(HotkeySpec spec, Runnable action) {
        this.spec = spec;
        this.action = action;
    }

    @Override
    public void register() {
        if (registered) {
            return;
        }
        try {
            prepareNativeLibrary();
            GlobalScreen.registerNativeHook();
            listener = new NativeKeyListener() {
                @Override
                public void nativeKeyPressed(NativeKeyEvent e) {
                    if ((e.getModifiers() & spec.modifiers()) == spec.modifiers()
                            && e.getKeyCode() == spec.keyCode()) {
                        action.run();
                    }
                }

                @Override
                public void nativeKeyReleased(NativeKeyEvent e) {
                    // not used
                }

                @Override
                public void nativeKeyTyped(NativeKeyEvent e) {
                    // not used
                }
            };
            GlobalScreen.addNativeKeyListener(listener);
            registered = true;
            log.info("Global hotkey registered (modifiers={}, key={}).", spec.modifiers(), spec.keyCode());
        } catch (NativeHookException | LinkageError | RuntimeException e) {
            log.warn("Could not register global hotkey: {}", e.getMessage());
        }
    }

    @Override
    public void stop() {
        if (!registered) {
            return;
        }
        if (listener != null) {
            try {
                GlobalScreen.removeNativeKeyListener(listener);
            } catch (Exception ignored) {
                // already removed
            }
            listener = null;
        }
        try {
            GlobalScreen.unregisterNativeHook();
        } catch (Exception ignored) {
            // already unregistered
        }
        registered = false;
    }

    private static volatile boolean nativeLibraryPrepared = false;

    /**
     * Extracts the JNativeHook native library and points JNativeHook at it.
     * The default locator cannot read nested (fat-jar) URLs, so we extract to
     * {@code data/native/} ourselves.
     */
    private static synchronized void prepareNativeLibrary() {
        if (nativeLibraryPrepared) {
            return;
        }
        String resource = nativeResourcePath();
        if (resource == null) {
            return;
        }
        Path target = Path.of(System.getProperty("user.dir"), "data", "native", nativeLibraryFileName());
        try (InputStream in = NativeGlobalHotkeyService.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                log.warn("JNativeHook native resource not found: {}", resource);
                return;
            }
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            ExtractedLibraryLocator.setLibrary(target.toFile());
            System.setProperty("jnativehook.lib.locator", ExtractedLibraryLocator.class.getName());
            nativeLibraryPrepared = true;
            log.info("Extracted JNativeHook native library to {}", target);
        } catch (IOException e) {
            log.warn("Could not extract JNativeHook native library: {}", e.getMessage());
        }
    }

    private static String nativeResourcePath() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("win")) {
            String winArch = arch.contains("64") ? "x86_64" : arch.contains("aarch64") ? "arm" : "x86";
            return "com/github/kwhat/jnativehook/lib/windows/" + winArch + "/JNativeHook.dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            String macArch = (arch.contains("aarch64") || arch.contains("arm")) ? "arm64" : "x86_64";
            return "com/github/kwhat/jnativehook/lib/darwin/" + macArch + "/libJNativeHook.dylib";
        }
        if (os.contains("linux")) {
            String linuxArch = arch.contains("aarch64") ? "arm64" : arch.contains("64") ? "x86_64" : "x86";
            return "com/github/kwhat/jnativehook/lib/linux/" + linuxArch + "/libJNativeHook.so";
        }
        return null;
    }

    private static String nativeLibraryFileName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("win")) {
            return "JNativeHook.dll";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "libJNativeHook.dylib";
        }
        return "libJNativeHook.so";
    }

    @Override
    public boolean isRegistered() {
        return registered;
    }

    /**
     * Parses a hotkey spec like {@code "ctrl alt J"} into modifiers + key code.
     * Supported modifiers: ctrl/control, alt, shift, meta/win/cmd.
     * Supported keys: a-z, 0-9, F1-F24, space, enter, escape, tab.
     */
    static HotkeySpec parse(String spec) {
        if (spec == null || spec.isBlank()) {
            throw new IllegalArgumentException("Hotkey spec is empty");
        }
        String[] tokens = spec.trim().toLowerCase().split("\\s+");
        int modifiers = 0;
        for (int i = 0; i < tokens.length - 1; i++) {
            modifiers |= modifierMask(tokens[i]);
        }
        int keyCode = keyCode(tokens[tokens.length - 1]);
        return new HotkeySpec(modifiers, keyCode);
    }

    private static int modifierMask(String token) {
        return switch (token) {
            case "ctrl", "control" -> NativeKeyEvent.CTRL_MASK;
            case "alt" -> NativeKeyEvent.ALT_MASK;
            case "shift" -> NativeKeyEvent.SHIFT_MASK;
            case "meta", "win", "cmd", "command" -> NativeKeyEvent.META_MASK;
            default -> throw new IllegalArgumentException("Unknown modifier: " + token);
        };
    }

    private static final int[] FUNCTION_KEYS = {
            NativeKeyEvent.VC_F1, NativeKeyEvent.VC_F2, NativeKeyEvent.VC_F3, NativeKeyEvent.VC_F4,
            NativeKeyEvent.VC_F5, NativeKeyEvent.VC_F6, NativeKeyEvent.VC_F7, NativeKeyEvent.VC_F8,
            NativeKeyEvent.VC_F9, NativeKeyEvent.VC_F10, NativeKeyEvent.VC_F11, NativeKeyEvent.VC_F12,
            NativeKeyEvent.VC_F13, NativeKeyEvent.VC_F14, NativeKeyEvent.VC_F15, NativeKeyEvent.VC_F16,
            NativeKeyEvent.VC_F17, NativeKeyEvent.VC_F18, NativeKeyEvent.VC_F19, NativeKeyEvent.VC_F20,
            NativeKeyEvent.VC_F21, NativeKeyEvent.VC_F22, NativeKeyEvent.VC_F23, NativeKeyEvent.VC_F24
    };

    // Key codes are platform scan codes and are NOT sequential, so map explicitly.
    private static final Map<Character, Integer> SINGLE_CHAR_KEYS = Map.ofEntries(
            Map.entry('A', NativeKeyEvent.VC_A), Map.entry('B', NativeKeyEvent.VC_B),
            Map.entry('C', NativeKeyEvent.VC_C), Map.entry('D', NativeKeyEvent.VC_D),
            Map.entry('E', NativeKeyEvent.VC_E), Map.entry('F', NativeKeyEvent.VC_F),
            Map.entry('G', NativeKeyEvent.VC_G), Map.entry('H', NativeKeyEvent.VC_H),
            Map.entry('I', NativeKeyEvent.VC_I), Map.entry('J', NativeKeyEvent.VC_J),
            Map.entry('K', NativeKeyEvent.VC_K), Map.entry('L', NativeKeyEvent.VC_L),
            Map.entry('M', NativeKeyEvent.VC_M), Map.entry('N', NativeKeyEvent.VC_N),
            Map.entry('O', NativeKeyEvent.VC_O), Map.entry('P', NativeKeyEvent.VC_P),
            Map.entry('Q', NativeKeyEvent.VC_Q), Map.entry('R', NativeKeyEvent.VC_R),
            Map.entry('S', NativeKeyEvent.VC_S), Map.entry('T', NativeKeyEvent.VC_T),
            Map.entry('U', NativeKeyEvent.VC_U), Map.entry('V', NativeKeyEvent.VC_V),
            Map.entry('W', NativeKeyEvent.VC_W), Map.entry('X', NativeKeyEvent.VC_X),
            Map.entry('Y', NativeKeyEvent.VC_Y), Map.entry('Z', NativeKeyEvent.VC_Z),
            Map.entry('0', NativeKeyEvent.VC_0), Map.entry('1', NativeKeyEvent.VC_1),
            Map.entry('2', NativeKeyEvent.VC_2), Map.entry('3', NativeKeyEvent.VC_3),
            Map.entry('4', NativeKeyEvent.VC_4), Map.entry('5', NativeKeyEvent.VC_5),
            Map.entry('6', NativeKeyEvent.VC_6), Map.entry('7', NativeKeyEvent.VC_7),
            Map.entry('8', NativeKeyEvent.VC_8), Map.entry('9', NativeKeyEvent.VC_9)
    );

    private static int keyCode(String token) {
        if (token.length() == 1) {
            Integer code = SINGLE_CHAR_KEYS.get(Character.toUpperCase(token.charAt(0)));
            if (code != null) {
                return code;
            }
        }
        if (token.startsWith("f") && token.length() > 1) {
            try {
                int n = Integer.parseInt(token.substring(1));
                if (n >= 1 && n <= FUNCTION_KEYS.length) {
                    return FUNCTION_KEYS[n - 1];
                }
            } catch (NumberFormatException ignored) {
                // fall through to named keys
            }
        }
        return switch (token) {
            case "space" -> NativeKeyEvent.VC_SPACE;
            case "enter", "return" -> NativeKeyEvent.VC_ENTER;
            case "escape", "esc" -> NativeKeyEvent.VC_ESCAPE;
            case "tab" -> NativeKeyEvent.VC_TAB;
            default -> throw new IllegalArgumentException("Unknown key: " + token);
        };
    }
}
