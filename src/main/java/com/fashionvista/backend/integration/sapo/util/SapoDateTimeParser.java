package com.fashionvista.backend.integration.sapo.util;

import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public final class SapoDateTimeParser {

    private SapoDateTimeParser() {
    }

    public static LocalDateTime parseTolerant(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime();
        } catch (DateTimeException ex) {
            return LocalDateTime.parse(value);
        }
    }
}
