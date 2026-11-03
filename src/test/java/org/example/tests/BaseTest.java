package org.example.tests;

import org.example.client.ApiClient;
import org.junit.jupiter.api.AfterEach;

import java.util.HashSet;
import java.util.Set;

public abstract class BaseTest {

    protected final ApiClient apiClient = new ApiClient();

    private final Set<String> tokensToCleanup = new HashSet<>();

    protected void registerTokenForCleanup(String token) {
        tokensToCleanup.add(token);
    }

    @AfterEach
    void cleanupTokens() {

        for (String token : tokensToCleanup) {
            try {
                apiClient.sendRequest(token, "LOGOUT");
            } catch (Exception e) {
                System.err.println(
                    "Не удалось удалить токен: " + token
                );
            }
        }

        tokensToCleanup.clear();
    }
}
