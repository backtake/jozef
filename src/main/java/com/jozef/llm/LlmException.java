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
}
