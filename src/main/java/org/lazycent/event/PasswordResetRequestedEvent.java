package org.lazycent.event;

import java.util.Locale;

public record PasswordResetRequestedEvent(String email, String token, Locale locale) {}