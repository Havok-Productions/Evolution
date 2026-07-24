package com.example.foliafunfacts;

import java.util.Locale;

enum FactOrder {
    RANDOM,
    SEQUENTIAL;

    static FactOrder parse(String value) {
        if (value == null) {
            return RANDOM;
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return RANDOM;
        }
    }
}
