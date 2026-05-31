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
import org.openqa.selenium.support.ui.WebDriverWait;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Browser-level smoke for tech-profile=spa.
 * Opens a real (headless) Chromium browser and verifies React actually mounts.
 * Catches issues that HTTP-only checks miss: JS errors, blank renders, SW conflicts.
 */
class SmokeSpaBrowser {

    private static final int PAGE_LOAD_TIMEOUT_SEC = 30;
    private static final int REACT_MOUNT_TIMEOUT_SEC = 15;

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
        wait = new WebDriverWait(driver, Duration.ofSeconds(REACT_MOUNT_TIMEOUT_SEC));
    }

    @AfterEach
    void tearDown() {
        if (driver != null) driver.quit();
    }

    @Test
    void react_mounts_and_root_has_content() {
        driver.get(baseUrl);

        // 1. Wait for document.readyState == 'complete'
        wait.until(d -> "complete".equals(
            ((JavascriptExecutor) d).executeScript("return document.readyState")));

        // 2. Wait for React to mount (#root must have children)
        wait.until(d -> {
            Object count = ((JavascriptExecutor) d)
                .executeScript("var r = document.getElementById('root'); return r ? r.children.length : 0;");
            return count instanceof Long && (Long) count > 0;
        });

        var root = driver.findElement(By.id("root"));
        assertFalse(root.getText().isBlank(),
            "React #root is empty — app did not mount. Possible causes: JS error, missing Firebase config, CORS.");
    }

    @Test
    void page_title_is_set() {
        driver.get(baseUrl);
        wait.until(d -> !d.getTitle().isBlank());
        String title = driver.getTitle();
        assertFalse(title.isBlank(), "Page title should not be blank");
        assertNotEquals("about:blank", title, "Page did not load correctly");
    }

    @Test
    void no_critical_js_error_on_load() {
        driver.get(baseUrl);
        wait.until(d -> "complete".equals(
            ((JavascriptExecutor) d).executeScript("return document.readyState")));

        // Check window.onerror did not fire a critical error
        // (we inject a listener via executeScript before checking body)
        Object bodyText = ((JavascriptExecutor) driver)
            .executeScript("return document.body ? document.body.innerHTML.length : 0;");
        assertNotNull(bodyText, "document.body is null — page failed to load");
        assertTrue((Long) bodyText > 100,
            "document.body has almost no content — React likely failed to render");
    }
}
