package com.jozef.core.security;

import com.jozef.core.action.ExecutionStrategy;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.Console;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Interactive confirmation on standard input.
 *
 * SAFE actions run automatically; CONFIRM/DANGEROUS actions prompt the user for
 * a y/n answer. If no real console is attached the answer is treated as "no"
 * (fail-safe), so the LLM never gains implicit authority.
 */
@Slf4j
public class ConsoleConfirmationService implements ConfirmationService {

    @Override
    public boolean requiresConfirmation(ExecutionStrategy strategy) {
        return strategy != ExecutionStrategy.SAFE;
    }

    @Override
    public boolean confirm(String toolName, Map<String, Object> arguments, ExecutionStrategy strategy) {
        if (strategy == ExecutionStrategy.SAFE) {
            return true;
        }

        String prompt = "\n⚠️  Jozef chce wykonać: " + toolName + " " + arguments
                + " [" + strategy + "]\n    Potwierdzasz? (t/n): ";
        String answer = readLine(prompt);

        boolean approved = answer != null && (answer.equalsIgnoreCase("t")
                || answer.equalsIgnoreCase("tak")
                || answer.equalsIgnoreCase("y")
                || answer.equalsIgnoreCase("yes"));

        if (approved) {
            log.info("User approved {} ({}).", toolName, strategy);
        } else {
            log.warn("User declined or confirmation unavailable for {} ({}).", toolName, strategy);
        }
        return approved;
    }

    private String readLine(String prompt) {
        Console console = System.console();
        if (console != null) {
            String line = console.readLine(prompt);
            return line == null ? null : line.trim();
        }

        // Fallback when running inside an IDE or a wrapped process.
        System.out.print(prompt);
        System.out.flush();
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
            String line = reader.readLine();
            return line == null ? null : line.trim();
        } catch (Exception e) {
            log.warn("Cannot read confirmation from console: {}", e.getMessage());
            return null;
        }
    }
}
