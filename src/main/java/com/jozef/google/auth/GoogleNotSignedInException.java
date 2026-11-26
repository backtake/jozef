package com.jozef.google.auth;

/** No Google tokens yet — the user has to sign in (an explicit action, never a background one). */
public class GoogleNotSignedInException extends IllegalStateException {
    public GoogleNotSignedInException() {
        super("Google is not signed in yet");
    }
}
