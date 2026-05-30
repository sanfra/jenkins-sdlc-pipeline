package net.sanfra.e2e.pages;

import net.sanfra.e2e.config.TestConfig;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.FindBy;

public class HomePage extends BasePage {

    @FindBy(css = ".header")
    private WebElement header;

    @FindBy(css = ".footer")
    private WebElement footer;

    @FindBy(css = ".logo-btn")
    private WebElement logoBtn;

    @FindBy(css = ".mobile-menu-wrap button, .hamburger-btn")
    private WebElement hamburgerBtn;

    public HomePage(WebDriver driver) {
        super(driver, TestConfig.get().getImplicitWaitSec());
    }

    public void open() {
        driver.get(TestConfig.get().getBaseUrl());
    }

    public boolean isHeaderVisible()    { return header.isDisplayed(); }
    public boolean isFooterVisible()    { return footer.isDisplayed(); }
    public boolean isLogoVisible()      { return logoBtn.isDisplayed(); }
    public boolean isHamburgerVisible() { return hamburgerBtn.isDisplayed(); }
    public String getTitle()            { return driver.getTitle(); }
}
