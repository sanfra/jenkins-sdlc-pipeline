package net.sanfra.e2e.tests;

import io.github.bonigarcia.wdm.WebDriverManager;
import net.sanfra.e2e.config.TestConfig;
import net.sanfra.e2e.pages.HomePage;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.time.Duration;

public class HomePageTest {

    private WebDriver driver;
    private HomePage homePage;

    @BeforeMethod
    public void setUp() {
        String chromeBinary = System.getProperty("chrome.binary", "");
        if (!chromeBinary.isEmpty()) {
            System.setProperty("webdriver.chrome.driver",
                    System.getProperty("chromedriver.path", "/usr/bin/chromedriver"));
        } else {
            WebDriverManager.chromedriver().setup();
        }

        ChromeOptions options = new ChromeOptions();
        if (!chromeBinary.isEmpty()) {
            options.setBinary(chromeBinary);
        }
        if (TestConfig.get().isHeadless()) {
            options.addArguments("--headless=new", "--no-sandbox",
                    "--disable-dev-shm-usage", "--disable-gpu", "--remote-debugging-port=0");
        }

        driver = new ChromeDriver(options);
        driver.manage().timeouts()
                .pageLoadTimeout(Duration.ofSeconds(TestConfig.get().getPageLoadTimeoutSec()))
                .implicitlyWait(Duration.ofSeconds(TestConfig.get().getImplicitWaitSec()));

        homePage = new HomePage(driver);
    }

    @Test(description = "Home page loads and title is correct")
    public void testPageTitle() {
        homePage.open();
        Assert.assertTrue(homePage.getTitle().contains("Sanfra"),
                "Title should contain 'Sanfra'");
    }

    @Test(description = "Header is visible on home page")
    public void testHeaderVisible() {
        homePage.open();
        Assert.assertTrue(homePage.isHeaderVisible(), "Header should be visible");
    }

    @Test(description = "Logo is visible in header")
    public void testLogoVisible() {
        homePage.open();
        Assert.assertTrue(homePage.isLogoVisible(), "Logo should be visible in header");
    }

    @Test(description = "Footer is visible on home page")
    public void testFooterVisible() {
        homePage.open();
        Assert.assertTrue(homePage.isFooterVisible(), "Footer should be visible");
    }

    @Test(description = "Hamburger menu button is visible on mobile viewport")
    public void testHamburgerVisible() {
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(390, 844));
        homePage.open();
        Assert.assertTrue(homePage.isHamburgerVisible(), "Hamburger should be visible on 390px viewport");
    }

    @AfterMethod
    public void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }
}
