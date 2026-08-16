package com.jozef.tray;

import com.github.kwhat.jnativehook.NativeLibraryLocator;

import java.io.File;
import java.util.Iterator;
import java.util.List;

/**
 * Custom {@link NativeLibraryLocator} that hands JNativeHook a native library
 * we already extracted ourselves.
 *
 * JNativeHook's default locator breaks inside a Spring Boot fat jar (nested jar
 * URLs are not hierarchical). We extract the {@code .dll/.so/.dylib} to disk and
 * point JNativeHook at it via the {@code jnativehook.lib.locator} system property.
 */
public class ExtractedLibraryLocator implements NativeLibraryLocator {

    private static volatile File library;

    public static void setLibrary(File file) {
        library = file;
    }

    @Override
    public Iterator<File> getLibraries() {
        return (library == null ? List.<File>of() : List.of(library)).iterator();
    }
}
