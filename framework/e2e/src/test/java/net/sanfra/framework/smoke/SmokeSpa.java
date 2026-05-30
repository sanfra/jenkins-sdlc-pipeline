package net.sanfra.framework.smoke;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Generic smoke suite for tech-profile=spa.
 * Validates the SPA server is up, responds with HTML, and stays within time bounds.
 * No app-specific knowledge — driven entirely by system properties.
 */
class SmokeSpa {

    private static final long RESPONSE_TIME_LIMIT_MS = 5000L;
    private static String healthPath;

    @BeforeAll
    static void setup() {
        RestAssured.baseURI = System.getProperty("app.baseUrl", "http://localhost:4173");
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        healthPath = System.getProperty("app.healthPath", "/");
    }

    @Test
    void health_check_returns_200_with_html() {
        given()
            .when().get(healthPath)
            .then()
            .statusCode(200)
            .contentType(containsString("text/html"))
            .time(lessThan(RESPONSE_TIME_LIMIT_MS));
    }

    @Test
    void root_path_serves_html_document() {
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("<html"))
            .time(lessThan(RESPONSE_TIME_LIMIT_MS));
    }

    @Test
    void spa_routes_respond_within_time_limit() {
        for (String path : new String[]{"/about", "/contact", "/privacy", "/legal"}) {
            given()
                .when().get(path)
                .then()
                .statusCode(anyOf(is(200), is(404)))
                .time(lessThan(RESPONSE_TIME_LIMIT_MS));
        }
    }
}
