package com.jozef.tray;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WindowsAutostartManagerTest {

    @Test
    void buildsQuotedJarCommand() {
        String command = WindowsAutostartManager.buildCommand(
                "C:\\jdk\\bin\\java.exe",
                "C:\\app\\jozef-0.0.1.jar");

        assertThat(command).isEqualTo("\"C:\\jdk\\bin\\java.exe\" -jar \"C:\\app\\jozef-0.0.1.jar\"");
    }

    @Test
    void pinsTheWorkingDirectorySoDataIsFoundWhenWindowsStartsJozef() {
        String command = WindowsAutostartManager.buildCommand(
                "C:\\jdk\\bin\\javaw.exe", "C:\\app\\build\\libs\\jozef.jar", "C:\\app\\");

        assertThat(command).isEqualTo(
                "\"C:\\jdk\\bin\\javaw.exe\" \"-Duser.dir=C:\\app\" -jar \"C:\\app\\build\\libs\\jozef.jar\"");
    }
}
