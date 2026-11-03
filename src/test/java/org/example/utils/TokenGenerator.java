package org.example.utils;

import java.util.UUID;

public final class TokenGenerator {

    private TokenGenerator() {
        // Запрещаем создание экземпляров служебного класса
    }

    public static String generate() {
        return UUID.randomUUID()
            .toString()
            .replace("-", "")
            .toUpperCase();
    }
}
