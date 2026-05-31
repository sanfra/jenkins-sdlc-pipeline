package net.sanfra.framework.smoke;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * Generic smoke suite for tech-profile=spa.
 * Validates: server up, correct HTML shell, JS bundle reachable, React root present.
 * No app-specific business logic — driven by system properties.
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
    void root_serves_react_app_shell() {
        // Verifica che sia la React app (id="root") e non un'altra app
        given()
            .when().get("/")
            .then()
            .statusCode(200)
            .body(containsString("id=\"root\""))
            .body(containsString("/assets/"))   // Vite assets
            .body(not(containsString("app-root"))) // non Angular
            .time(lessThan(RESPONSE_TIME_LIMIT_MS));
    }

    @Test
    void js_bundle_is_reachable() {
        // Estrae il path del bundle JS dall'index.html e verifica che sia scaricabile
        String html = given().when().get("/").then().statusCode(200).extract().body().asString();
        String bundlePath = extractAssetPath(html, ".js");
        if (bundlePath != null) {
            given()
                .when().get(bundlePath)
                .then()
                .statusCode(200)
                .contentType(anyOf(
                    containsString("javascript"),
                    containsString("application/octet-stream")
                ))
                .time(lessThan(RESPONSE_TIME_LIMIT_MS));
        }
    }

    @Test
    void spa_routes_serve_same_html_shell() {
        // Su una SPA tutte le route devono tornare lo stesso index.html (routing client-side)
        for (String path : new String[]{"/about", "/contact", "/privacy"}) {
            given()
                .when().get(path)
                .then()
                .statusCode(200)
                .body(containsString("id=\"root\""))
                .time(lessThan(RESPONSE_TIME_LIMIT_MS));
        }
    }

    private static String extractAssetPath(String html, String extension) {
        int idx = html.indexOf("src=\"/assets/");
        if (idx == -1) return null;
        int start = idx + 5; // skip src="
        int end = html.indexOf("\"", start);
        String path = html.substring(start, end);
        return path.endsWith(extension) ? path : null;
    }
}
