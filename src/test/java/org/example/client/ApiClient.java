package org.example.client;

import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;

import static io.restassured.RestAssured.given;

public class ApiClient {

    private static final String BASE_URI =
        System.getProperty("api.baseUrl", "http://localhost:8080");

    private static final String API_KEY =
        System.getProperty("api.key", "qazWSXedc");

    public Response sendRequest(String token, String action) {
        return sendRequest(token, action, API_KEY);
    }

    public Response sendRequest(
        String token,
        String action,
        String apiKey
    ) {
        RequestSpecification request = given()
            .baseUri(BASE_URI)
            .filter(new AllureRestAssured())
            .contentType("application/x-www-form-urlencoded")
            .accept("application/json");

        if (apiKey != null) {
            request.header("X-Api-Key", apiKey);
        }

        if (token != null) {
            request.formParam("token", token);
        }

        if (action != null) {
            request.formParam("action", action);
        }

        return request.when().post("/endpoint");
    }
}
