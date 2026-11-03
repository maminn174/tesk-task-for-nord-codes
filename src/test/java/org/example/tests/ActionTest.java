package org.example.tests;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.qameta.allure.*;
import org.example.utils.TokenGenerator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.equalTo;

@Epic("Интеграционное тестирование API")
@Feature("Действия пользователя")
@WireMockTest(httpPort = 8888)
public class ActionTest extends BaseTest {

    @Test
    @DisplayName("Пользователи без аутентификации не могут выполнять действия")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что пользователи без аутентификации не могут выполнять действия и вызывать внешние запросы.")
    void actionFailsWithoutLogin() {

        // Подготовка: создаём токен без аутентификации
        String token = TokenGenerator.generate();

        // Действие: отправляем запрос на выполнение действия без входа
        var response = apiClient.sendRequest(token, "ACTION");

        // Проверка: действие отклонено
        response.then()
            .body("result", equalTo("ERROR"));

        // Проверка: внешний сервис не вызывался
        verify(0, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @Story("Выполнение действий после аутентификации")
    @DisplayName("Успешное выполнение действия после входа")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что пользователь после аутентификации может выполнить действие и вызвать внешний сервис.")
    void actionSucceedsAfterLogin() {

        // Подготовка: создаём уникальный токен
        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешные ответы внешних сервисов
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse()
                .withStatus(200)));

        stubFor(post(urlEqualTo("/doAction"))
            .willReturn(aResponse()
                .withStatus(200)));

        // Действие: аутентифицируем пользователя
        var loginResponse = Allure.step(
            "Аутентифицировать пользователя",
            () -> apiClient.sendRequest(token, "LOGIN")
        );

        // Проверка: аутентификация прошла успешно
        loginResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Действие: выполняем действие
        var actionResponse = Allure.step(
            "Выполнить действие с аутентифицированным токеном",
            () -> apiClient.sendRequest(token, "ACTION")
        );

        // Проверка: действие выполнено успешно
        actionResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Проверка: вызваны оба внешних сервиса
        verify(1, postRequestedFor(urlEqualTo("/auth"))
            .withRequestBody(containing("token=" + token)));

        verify(1, postRequestedFor(urlEqualTo("/doAction"))
            .withRequestBody(containing("token=" + token)));
    }

    @Test
    @DisplayName("Аутентификация пользователя не разрешает действия другого пользователя")
    @Severity(SeverityLevel.CRITICAL)
    @Description("Проверяет, что аутентификация одного токена не разрешает действия с другим токеном.")
    void authenticatedTokenDoesNotAuthorizeOtherTokens() {

        // Подготовка: создаём два разных токена пользователей
        String tokenA = TokenGenerator.generate();
        String tokenB = TokenGenerator.generate();

        registerTokenForCleanup(tokenA);

        // Подготовка: имитируем успешные ответы внешних сервисов
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        stubFor(post(urlEqualTo("/doAction"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: аутентифицируем пользователя A
        var loginResponse = apiClient.sendRequest(tokenA, "LOGIN");

        // Проверка: вход выполнен успешно
        loginResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Действие: пытаемся выполнить действие с токеном B без аутентификации
        var unauthorizedResponse = apiClient.sendRequest(tokenB, "ACTION");

        // Проверка: пользователь B не может выполнить действие
        unauthorizedResponse.then()
            .body("result", equalTo("ERROR"));

        // Действие: выполняем действие с аутентифицированным токеном A
        var authorizedResponse = apiClient.sendRequest(tokenA, "ACTION");

        // Проверка: пользователь A может выполнить действие
        authorizedResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Проверка: аутентифицирован только пользователь A
        verify(1, postRequestedFor(urlEqualTo("/auth"))
            .withRequestBody(containing("token=" + tokenA)));

        // Проверка: только пользователь A вызвал внешний сервис действий
        verify(1, postRequestedFor(urlEqualTo("/doAction"))
            .withRequestBody(containing("token=" + tokenA)));

        verify(0, postRequestedFor(urlEqualTo("/doAction"))
            .withRequestBody(containing("token=" + tokenB)));
    }

    @Test
    @DisplayName("Действие возвращает ошибку при ответе 500 от внешнего сервиса")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что приложение возвращает ошибку при сбое внешнего сервиса действий.")
    void actionFailsWhenExternalServiceReturns500() {

        // Подготовка: создаём уникальный токен
        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешную аутентификацию
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        // Подготовка: имитируем ошибку внешнего сервиса действий
        stubFor(post(urlEqualTo("/doAction"))
            .willReturn(aResponse().withStatus(500)));

        // Действие: аутентифицируем пользователя
        var loginResponse = apiClient.sendRequest(token, "LOGIN");

        // Проверка: аутентификация прошла успешно
        loginResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Действие: выполняем действие
        var actionResponse = apiClient.sendRequest(token, "ACTION");

        // Проверка: ошибка внешнего сервиса обработана
        actionResponse.then()
            .statusCode(500)
            .body("result", equalTo("ERROR"))
            .body("message", equalTo("Internal Server Error"));

        // Проверка: внешний сервис действий вызван ровно один раз
        verify(1, postRequestedFor(urlEqualTo("/doAction"))
            .withRequestBody(containing("token=" + token)));
    }

    @Test
    @DisplayName("Аутентификация сохраняется после ошибки внешнего сервиса действий")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что аутентификация сохраняется после сбоя внешнего сервиса действий.")
    void userRemainsAuthenticatedAfterActionFailure() {

        // Подготовка: создаём уникальный токен
        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешную аутентификацию
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        // Подготовка: имитируем ошибку внешнего сервиса действий
        stubFor(post(urlEqualTo("/doAction"))
            .willReturn(aResponse().withStatus(500)));


        // Действие: аутентифицируем пользователя
        var loginResponse = apiClient.sendRequest(token, "LOGIN");

        // Проверка: аутентификация прошла успешно
        loginResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Действие: выполняем действие, завершающееся ошибкой
        var failedActionResponse = apiClient.sendRequest(token, "ACTION");

        // Проверка: получена ошибка внешнего сервиса
        failedActionResponse.then()
            .statusCode(500)
            .body("result", equalTo("ERROR"));

        // Подготовка: меняем ответ внешнего сервиса на успешный
        stubFor(post(urlEqualTo("/doAction"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: повторяем действие с тем же токеном
        var retryResponse = apiClient.sendRequest(token, "ACTION");

        // Проверка: повторное действие выполнено успешно
        retryResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Проверка: отправлены оба запроса во внешний сервис действий
        verify(2, postRequestedFor(urlEqualTo("/doAction"))
            .withRequestBody(containing("token=" + token)));

    }

    @Test
    @DisplayName("Пользователь после аутентификации может выполнять несколько действий")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что пользователь после аутентификации может выполнять несколько действий без повторного входа.")
    void authenticatedUserCanPerformMultipleActions() {

        // Подготовка: создаём уникальный токен
        String token = TokenGenerator.generate();
        registerTokenForCleanup(token);

        // Подготовка: имитируем успешные ответы внешних сервисов
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        stubFor(post(urlEqualTo("/doAction"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: аутентифицируем пользователя
        var loginResponse = apiClient.sendRequest(token, "LOGIN");

        // Проверка: вход выполнен успешно
        loginResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Действие: выполняем первое действие
        var firstActionResponse = apiClient.sendRequest(token, "ACTION");

        // Проверка: первое действие выполнено успешно
        firstActionResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Действие: выполняем второе действие
        var secondActionResponse = apiClient.sendRequest(token, "ACTION");

        // Проверка: второе действие выполнено успешно
        secondActionResponse.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Проверка: отправлены оба запроса во внешний сервис действий
        verify(2, postRequestedFor(urlEqualTo("/doAction"))
            .withRequestBody(containing("token=" + token)));
    }



}
