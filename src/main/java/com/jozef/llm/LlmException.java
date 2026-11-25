package com.jozef.llm;

/**
 * Thrown by an {@link LLMProvider} when the model could not produce an answer
 * (network error, timeout, non-2xx response, unparsable body).
 *
 * Providers must throw instead of returning error text as content — otherwise
 * the error would be read aloud to the user as if it were the answer.
 */
public class LlmException extends RuntimeException {

    private final int statusCode;

    public LlmException(String message) {
        this(message, -1, null);
    }

    public LlmException(String message, Throwable cause) {
        this(message, -1, cause);
    }

    public LlmException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** HTTP status code, or -1 when the failure was not an HTTP response. */
    public int statusCode() {
        return statusCode;
    }

    /**
     * The account cannot be used at all — invalid key (401/403) or no credits /
     * exhausted quota (OpenAI answers 429 with "insufficient_quota"). Retrying soon is pointless.
     */
    public boolean isAccountProblem() {
        if (statusCode == 401 || statusCode == 403 || statusCode == 402) return true;
        String m = getMessage() == null ? "" : getMessage().toLowerCase();
        return statusCode == 429 && (m.contains("insufficient_quota") || m.contains("credit")
                || m.contains("billing") || m.contains("quota"));
    }

    /** A short Polish explanation for the user, or {@code null} when nothing useful can be said. */
    public String userHint() {
        if (statusCode == 401 || statusCode == 403) return "klucz API jest nieprawidłowy albo wygasł";
        if (isAccountProblem()) return "na koncie dostawcy modelu skończyły się środki";
        if (statusCode == 429) return "dostawca modelu ogranicza teraz liczbę zapytań";
        if (statusCode >= 500) return "serwer dostawcy modelu ma awarię";
        String m = getMessage() == null ? "" : getMessage().toLowerCase();
        if (m.contains("timed out")) return "model odpowiada zbyt długo";
        if (m.contains("unreachable")) return "nie ma połączenia z modelem (internet albo lokalna Ollama nie działa)";
        return null;
    }
}
