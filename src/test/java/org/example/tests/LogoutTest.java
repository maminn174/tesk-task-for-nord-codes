package org.example.tests;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.qameta.allure.*;
import org.example.utils.TokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.equalTo;

@Epic("Интеграционное тестирование API")
@Feature("Управление сессиями")
@WireMockTest(httpPort = 8888)
class LogoutTest extends BaseTest {

    @Test
    @DisplayName("Выход удаляет аутентифицированный токен")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что выход удаляет токен и запрещает последующие действия.")
    void logoutRemovesAuthenticatedToken() {

        // Подготовка: создаём уникальный токен
        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешную аутентификацию
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: аутентифицируем пользователя
        var loginResponse = apiClient.sendRequest(
            token,
            "LOGIN"
        );

        // Проверка: вход выполнен успешно
        loginResponse.then()
            .statusCode(200)
            .body(
                "result",
                equalTo("OK")
            );

        // Действие: выполняем выход пользователя
        var logoutResponse = apiClient.sendRequest(
            token,
            "LOGOUT"
        );

        // Проверка: выход выполнен успешно
        logoutResponse.then()
            .statusCode(200)
            .body(
                "result",
                equalTo("OK")
            );

        unregisterTokenForCleanup(token);

        // Действие: пытаемся выполнить действие после выхода
        var actionResponse = apiClient.sendRequest(
            token,
            "ACTION"
        );

        // Проверка: действие отклонено
        actionResponse.then()
            .body(
                "result",
                equalTo("ERROR")
            );

        // Проверка: сервис аутентификации вызван один раз
        verify(
            1,
            postRequestedFor(urlEqualTo("/auth"))
                .withRequestBody(containing("token=" + token))
        );

        // Проверка: внешний сервис действий не вызывался
        verify(
            0,
            postRequestedFor(urlEqualTo("/doAction"))
        );
    }

    @Test
    @DisplayName("Повторный выход отклоняется, если токен больше не существует")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что повторный выход возвращает HTTP 403, если токен больше не существует.")
    void repeatedLogoutReturnsForbidden() {

        // Подготовка: создаём уникальный токен
        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешную аутентификацию
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: аутентифицируем пользователя
        var loginResponse = apiClient.sendRequest(
            token,
            "LOGIN"
        );

        // Проверка: аутентификация прошла успешно
        loginResponse.then()
            .statusCode(200)
            .body(
                "result",
                equalTo("OK")
            );

        // Действие: выполняем выход пользователя
        var firstLogoutResponse = apiClient.sendRequest(
            token,
            "LOGOUT"
        );

        // Проверка: выход выполнен успешно
        firstLogoutResponse.then()
            .statusCode(200)
            .body(
                "result",
                equalTo("OK")
            );

        unregisterTokenForCleanup(token);

        // Действие: пытаемся выйти повторно
        var secondLogoutResponse = apiClient.sendRequest(
            token,
            "LOGOUT"
        );

        // Проверка: токен больше не существует
        secondLogoutResponse.then()
            .statusCode(403)
            .body(
                "result",
                equalTo("ERROR")
            )
            .body(
                "message",
                equalTo(
                    "Token '" + token + "' not found"
                )
            );

        // Проверка: внешний сервис аутентификации вызван только один раз
        verify(
            1,
            postRequestedFor(urlEqualTo("/auth"))
                .withRequestBody(containing("token=" + token))
        );

        // Проверка: внешний сервис действий не вызывался
        verify(
            0,
            postRequestedFor(urlEqualTo("/doAction"))
        );
    }

    @Test
    @DisplayName("Выход отклоняется для токена без аутентификации")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что выход отклоняется для токена, который не прошёл аутентификацию.")
    void logoutFailsWithoutLogin() {

        // Подготовка: создаём токен без аутентификации
        String token = TokenGenerator.generate();

        // Действие: пытаемся выйти без аутентификации
        var response = apiClient.sendRequest(
            token,
            "LOGOUT"
        );

        // Проверка: токен не существует
        response.then()
            .statusCode(403)
            .body(
                "result",
                equalTo("ERROR")
            )
            .body(
                "message",
                equalTo(
                    "Token '" + token + "' not found"
                )
            );

        // Проверка: внешние сервисы не вызывались
        verify(
            0,
            postRequestedFor(urlEqualTo("/auth"))
        );
        verify(
            0,
            postRequestedFor(urlEqualTo("/doAction"))
        );
    }


}
