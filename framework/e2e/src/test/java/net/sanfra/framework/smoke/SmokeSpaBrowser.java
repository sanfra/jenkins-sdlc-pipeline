package net.sanfra.framework.smoke;

import io.github.bonigarcia.wdm.WebDriverManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Browser-level smoke for tech-profile=spa.
 *
 * Verifies that the app actually renders visible UI in a real headless browser.
 * Catches failures that HTTP-only checks miss: JS errors, crashed async init,
 * missing env config (Firebase, etc.), CORS failures.
 *
 * The key insight: checks happen AFTER async initialization (Firebase, auth state,
 * worker connections) has had time to complete — not just at initial DOM mount.
 */
class SmokeSpaBrowser {

    private static final int PAGE_LOAD_TIMEOUT_SEC  = 30;
    private static final int VISIBLE_UI_TIMEOUT_SEC = 15;

    private WebDriver driver;
    private WebDriverWait wait;
    private String baseUrl;

    @BeforeEach
    void setup() {
        baseUrl = System.getProperty("app.baseUrl", "http://localhost:4173");
        String chromeBinary     = System.getProperty("chrome.binary", "");
        String chromedriverPath = System.getProperty("chromedriver.path", "");

        if (!chromedriverPath.isEmpty()) {
            System.setProperty("webdriver.chrome.driver", chromedriverPath);
        } else {
            WebDriverManager.chromedriver().setup();
        }

        ChromeOptions options = new ChromeOptions();
        if (!chromeBinary.isEmpty()) options.setBinary(chromeBinary);
        options.addArguments(
            "--headless=new",
            "--no-sandbox",
            "--disable-dev-shm-usage",
            "--disable-gpu",
            "--window-size=1920,1080",
            "--remote-allow-origins=*",
            "--disable-extensions"
        );

        driver = new ChromeDriver(options);
        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(PAGE_LOAD_TIMEOUT_SEC));
        wait = new WebDriverWait(driver, Duration.ofSeconds(VISIBLE_UI_TIMEOUT_SEC));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.quit();
    }

    @Test
    void header_is_visible_after_async_init() {
        // Header uses useAuth() — requires Firebase to have initialized without crashing.
        // If Firebase config is missing or throws, React unmounts and header never appears.
        driver.get(baseUrl);

        wait.until(d -> "complete".equals(
            ((JavascriptExecutor) d).executeScript("return document.readyState")));

        // Wait for <header> to be VISIBLE — this requires full async app init
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("header")));
        assertTrue(driver.findElement(By.tagName("header")).isDisplayed(),
            "Header not visible — app likely crashed during async initialization " +
            "(check for Firebase config errors, missing env vars, CORS failures)");
    }

    @Test
    void footer_is_visible() {
        driver.get(baseUrl);
        wait.until(d -> "complete".equals(
            ((JavascriptExecutor) d).executeScript("return document.readyState")));
        wait.until(ExpectedConditions.visibilityOfElementLocated(By.tagName("footer")));
        assertTrue(driver.findElement(By.tagName("footer")).isDisplayed(),
            "Footer not visible — app did not render completely");
    }

    @Test
    void react_root_stable_after_async_init() {
        // Checks #root still has children AFTER async init (not just at initial mount).
        // A crashed app loses its DOM children after the error propagates.
        driver.get(baseUrl);

        wait.until(d -> "complete".equals(
            ((JavascriptExecutor) d).executeScript("return document.readyState")));

        // Initial mount
        wait.until(d -> {
            Object c = ((JavascriptExecutor) d)
                .executeScript("var r=document.getElementById('root');return r?r.children.length:0;");
            return c instanceof Long && (Long) c > 0;
        });

        // Let async initialization run (Firebase, auth state, worker)
        try { Thread.sleep(4000); } catch (InterruptedException ignored) {}

        // Re-check: if the app crashed after async init, root will be empty
        Object count = ((JavascriptExecutor) driver)
            .executeScript("var r=document.getElementById('root');return r?r.children.length:0;");
        assertTrue(
            count instanceof Long && (Long) count > 0,
            "React #root lost its content after async initialization — " +
            "app crashed post-mount (Firebase missing config, CORS error, unhandled promise rejection)"
        );
    }
}
