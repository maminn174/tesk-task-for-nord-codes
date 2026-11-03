package org.example.tests;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.example.client.ApiClient;
import org.example.utils.TokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.equalTo;

@Epic("Интеграционное тестирование API")
@Feature("Безопасность API")
@WireMockTest(httpPort = 8888)
class SecurityTest {

    private final ApiClient apiClient = new ApiClient();

    @Test
    @DisplayName("Запрос отклоняется при отсутствии ключа API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что запросы без ключа API отклоняются с HTTP 401.")
    void requestFailsWithoutApiKey() {

        // Подготовка: создаём допустимый токен
        String token = TokenGenerator.generate();

        // Действие: отправляем запрос без ключа API
        var response = apiClient.sendRequest(token, "LOGIN", null);

        // Проверка: запрос отклонён из-за отсутствия авторизации
        response.then()
            .statusCode(401)
            .body("result", equalTo("ERROR"))
            .body("message", equalTo("Missing or invalid API Key"));

        // Проверка: аутентификация не запрашивалась
        verify(0, postRequestedFor(urlEqualTo("/auth")));
    }

    @Test
    @DisplayName("Запрос отклоняется при неверном ключе API")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что запросы с неверным ключом API отклоняются с HTTP 401.")
    void requestFailsWithInvalidApiKey() {

        // Подготовка: создаём допустимый токен
        String token = TokenGenerator.generate();

        // Действие: отправляем запрос с неверным ключом API
        var response = apiClient.sendRequest(token, "LOGIN", "invalid-key");

        // Проверка: запрос отклонён из-за отсутствия авторизации
        response.then()
            .statusCode(401)
            .body("result", equalTo("ERROR"))
            .body("message", equalTo("Missing or invalid API Key"));

        // Проверка: аутентификация не запрашивалась
        verify(0, postRequestedFor(urlEqualTo("/auth")));
    }
}
