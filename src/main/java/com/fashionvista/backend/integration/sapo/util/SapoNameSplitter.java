package com.fashionvista.backend.integration.sapo.util;

import lombok.Value;

public final class SapoNameSplitter {

    private SapoNameSplitter() {
    }

    public static Split splitLastName(String fullName) {
        String trimmed = fullName == null ? "" : fullName.trim();
        int lastSpace = trimmed.lastIndexOf(' ');
        if (lastSpace < 0) {
            return new Split(trimmed, "");
        }
        String firstName = trimmed.substring(0, lastSpace);
        String lastName = trimmed.substring(lastSpace + 1);
        return new Split(firstName, lastName);
    }

    @Value
    public static class Split {
        String firstName;
        String lastName;
    }
}
