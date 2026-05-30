package net.sanfra.e2e.pages;

import net.sanfra.e2e.config.TestConfig;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;

public class HomePage extends BasePage {

    private static final By HEADER   = By.cssSelector(".header");
    private static final By FOOTER   = By.cssSelector(".footer");
    private static final By LOGO_BTN = By.cssSelector(".logo-btn");
    private static final By HAMBURGER = By.cssSelector(".mobile-menu-wrap button");

    public HomePage(WebDriver driver) {
        super(driver, TestConfig.get().getImplicitWaitSec());
    }

    public void open() {
        driver.get(TestConfig.get().getBaseUrl());
        // 1. Wait for DOM ready
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
        // 2. Wait for React to mount
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("#root > *")));
    }

    public boolean isHeaderVisible() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(HEADER)).isDisplayed();
    }

    public boolean isFooterVisible() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(FOOTER)).isDisplayed();
    }

    public boolean isLogoVisible() {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(LOGO_BTN)).isDisplayed();
    }

    public boolean isHamburgerVisible() {
        driver.manage().window().setSize(new org.openqa.selenium.Dimension(390, 844));
        return wait.until(ExpectedConditions.visibilityOfElementLocated(HAMBURGER)).isDisplayed();
    }

    public String getTitle() {
        return driver.getTitle();
    }
}
