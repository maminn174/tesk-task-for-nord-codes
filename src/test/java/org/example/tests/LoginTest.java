package org.example.tests;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.qameta.allure.*;
import org.example.utils.TokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.equalTo;

@Epic("Интеграционное тестирование API")
@Feature("Аутентификация")
@WireMockTest(httpPort = 8888)
class LoginTest extends BaseTest {

    @Test
    @DisplayName("Успешный вход при ответе 200 от внешнего сервиса аутентификации")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что пользователь может войти, если внешний сервис аутентификации возвращает HTTP 200.")
    void successfulLogin() {

        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешную аутентификацию
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: аутентифицируем пользователя
        var response = apiClient.sendRequest(token, "LOGIN");

        // Проверка: аутентификация прошла успешно
        response.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Проверка: отправлен запрос во внешний сервис аутентификации
        verify(1, postRequestedFor(urlEqualTo("/auth"))
            .withRequestBody(containing("token=" + token)));
    }

    @Test
    @DisplayName("Отказ во входе при ответе 401 от внешнего сервиса аутентификации")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что вход отклоняется, если внешний сервис аутентификации возвращает HTTP 401.")
    void loginFailsWhenExternalAuthReturns401() {

        String token = TokenGenerator.generate();

        // Подготовка: имитируем отказ внешнего сервиса аутентификации
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse()
                .withStatus(401)));

        // Действие: отправляем запрос на вход
        var response = apiClient.sendRequest(
            token,
            "LOGIN"
        );

        // Проверка: получен наблюдаемый ответ с ошибкой
        response.then()
            .statusCode(500)
            .body(
                "result",
                equalTo("ERROR")
            )
            .body(
                "message",
                equalTo("Internal Server Error")
            );

        // Проверка: отправлен запрос во внешний сервис аутентификации
        verify(
            1,
            postRequestedFor(urlEqualTo("/auth"))
                .withRequestBody(containing("token=" + token))
        );
    }


    @Test
    @DisplayName("Повторный вход с существующим токеном возвращает конфликт")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что повторный вход с аутентифицированным токеном возвращает HTTP 409 без повторного обращения к внешнему сервису аутентификации.")
    void repeatedLoginReturnsConflict() {

        // Подготовка: создаём уникальный токен
        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешную аутентификацию
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: аутентифицируем пользователя
        var firstResponse = apiClient.sendRequest(token, "LOGIN");

        // Проверка: аутентификация прошла успешно
        firstResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Действие: повторно аутентифицируем тот же токен
        var secondResponse = apiClient.sendRequest(token, "LOGIN");

        // Проверка: получен конфликт из-за повторного токена
        secondResponse.then()
            .statusCode(409)
            .body("result", equalTo("ERROR"))
            .body("message", equalTo(
                "Token '" + token + "' already exists"
            ));

        // Проверка: аутентификация запрошена только один раз
        verify(1, postRequestedFor(urlEqualTo("/auth"))
            .withRequestBody(containing("token=" + token)));

        // Проверка: действия не выполнялись
        verify(0, postRequestedFor(urlEqualTo("/doAction")));
    }


}
