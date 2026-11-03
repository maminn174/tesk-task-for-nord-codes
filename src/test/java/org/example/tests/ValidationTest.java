package org.example.tests;

import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import io.qameta.allure.Description;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import org.example.client.ApiClient;
import org.example.utils.TokenGenerator;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.hamcrest.Matchers.equalTo;

@Epic("Интеграционное тестирование API")
@Feature("Валидация входных данных")
@WireMockTest(httpPort = 8888)
class ValidationTest {

    private final ApiClient apiClient = new ApiClient();

    @ParameterizedTest(name = "Длина токена: {0}")
    @DisplayName("Вход отклоняет токены недопустимой длины")
    @CsvSource({
        "КОРОТКИЙ, ABC123",
        "ПУСТОЙ, ''",
        "СЛИШКОМ_ДЛИННЫЙ, 0123456789ABCDEF0123456789ABCDEF0"
    })
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что токены недопустимой длины отклоняются до обращения к внешнему сервису аутентификации.")
    void loginRejectsInvalidTokenLength(String scenario, String token) {

        // Действие: отправляем запрос на вход с недопустимым токеном
        var response = apiClient.sendRequest(token, "LOGIN");

        // Проверка: токен отклонён
        response.then()
            .statusCode(400)
            .body("result", equalTo("ERROR"));

        // Проверка: аутентификация не запрашивалась
        verify(0, postRequestedFor(urlEqualTo("/auth")));
    }


    @ParameterizedTest(name = "Недопустимые символы токена: {0}")
    @DisplayName("Вход отклоняет токены с недопустимыми символами")
    @CsvSource({
        "СТРОЧНЫЕ_БУКВЫ, 0123456789abcdef0123456789abcdef",
        "СПЕЦСИМВОЛ, 0123456789ABCDE@0123456789ABCDEF",
        "ПРОБЕЛ, '0123456789ABCDEF 123456789ABCDEF'"
    })
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что токены со строчными буквами, специальными символами или пробельными символами отклоняются.")
    void loginRejectsInvalidTokenCharacters(
        String scenario, String token) {

        // Действие: отправляем запрос на вход с недопустимыми символами
        var response = apiClient.sendRequest(token, "LOGIN");

        // Проверка: получена ошибка валидации
        response.then()
            .statusCode(400)
            .body("result", equalTo("ERROR"));

        // Проверка: внешний сервис аутентификации не вызывался
        verify(0, postRequestedFor(urlEqualTo("/auth")));
    }

    @Disabled("BUG-001: Приложение отклоняет допустимые токены из символов A-Z и 0-9")
    @Test
    @DisplayName("Вход принимает токены с буквами от G до Z")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что токены с буквами G-Z принимаются согласно спецификации API.")
    void loginAcceptsTokensWithLettersGtoZ() {

        // Подготовка: используем токен, допустимый по спецификации API
        String token = "GHIJKLMNOPQRSTUVWXYZ0123456789AB";

        // Подготовка: имитируем успешную аутентификацию
        stubFor(post(urlEqualTo("/auth"))
            .willReturn(aResponse().withStatus(200)));

        // Действие: отправляем запрос на вход
        var response = apiClient.sendRequest(token, "LOGIN");

        // Проверка: поведение соответствует спецификации
        response.then()
            .statusCode(200)
            .body("result", equalTo("OK"));

        // Проверка: внешний сервис не вызывался
        // при отклонении токена приложением
        verify(1, postRequestedFor(urlEqualTo("/auth"))
            .withRequestBody(containing("token=" + token)));
    }


    @Test
    @DisplayName("Вход отклоняется при отсутствии токена")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что вход без токена возвращает ошибку валидации.")
    void loginFailsWhenTokenIsMissing() {

        // Действие: отправляем запрос на вход без токена
        var response = apiClient.sendRequest(null, "LOGIN");

        // Проверка: получена ошибка валидации
        response.then()
            .statusCode(400)
            .body("result", equalTo("ERROR"));

        // Проверка: аутентификация не запрашивалась
        verify(0, postRequestedFor(urlEqualTo("/auth")));
    }

    @Test
    @DisplayName("Запрос отклоняется при отсутствии действия")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что запросы без параметра действия отклоняются.")
    void requestFailsWhenActionIsMissing() {

        // Подготовка: создаём допустимый токен
        String token = TokenGenerator.generate();

        // Действие: отправляем запрос без параметра действия
        var response = apiClient.sendRequest(token, null);

        // Проверка: получена ошибка валидации
        response.then()
            .statusCode(400)
            .body("result", equalTo("ERROR"));

        // Проверка: ни один внешний сервис не вызывался
        verify(0, postRequestedFor(urlEqualTo("/auth")));
        verify(0, postRequestedFor(urlEqualTo("/doAction")));
    }

    @Test
    @DisplayName("Запрос отклоняется при неизвестном действии")
    @Severity(SeverityLevel.NORMAL)
    @Description("Проверяет, что неподдерживаемые действия отклоняются без обращения к внешним сервисам.")
    void requestFailsWhenActionIsUnknown() {

        // Подготовка: создаём допустимый токен
        String token = TokenGenerator.generate();

        // Действие: отправляем неподдерживаемое действие
        var response = apiClient.sendRequest(token, "UNKNOWN");

        // Проверка: получена ошибка валидации
        response.then()
            .statusCode(400)
            .body("result", equalTo("ERROR"));

        // Проверка: ни один внешний сервис не вызывался
        verify(0, postRequestedFor(urlEqualTo("/auth")));
        verify(0, postRequestedFor(urlEqualTo("/doAction")));
    }



}
