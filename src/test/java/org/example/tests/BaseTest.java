
package org.example.tests;

import io.restassured.response.Response;
import org.example.client.ApiClient;
import org.junit.jupiter.api.AfterEach;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.fail;

public abstract class BaseTest {

    protected final ApiClient apiClient = new ApiClient();

    private final Set<String> tokensToCleanup = new HashSet<>();

    protected void registerTokenForCleanup(String token) {
        tokensToCleanup.add(token);
    }

    protected void unregisterTokenForCleanup(String token) {
        tokensToCleanup.remove(token);
    }

    @AfterEach
    void cleanupTokens() {
        StringBuilder errors = new StringBuilder();

        for (String token : tokensToCleanup) {
            try {
                Response response = apiClient.sendRequest(token, "LOGOUT");

                if (response.statusCode() != 200 ||
                    !"OK".equals(response.jsonPath().getString("result"))) {

                    errors.append(
                        "Failed to remove token: " + token +
                            ", status: " + response.statusCode() +
                            System.lineSeparator()
                    );
                }
            } catch (Exception e) {
                errors.append(
                    "Failed to remove token: " + token +
                        ", error: " + e.getMessage() +
                        System.lineSeparator()
                );
            }
        }

        tokensToCleanup.clear();

        if (!errors.isEmpty()) {
            fail("Token cleanup failed:\n" + errors);
        }
    }
}
