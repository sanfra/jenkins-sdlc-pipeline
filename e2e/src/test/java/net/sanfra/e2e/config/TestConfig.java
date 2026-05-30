package net.sanfra.e2e.config;

import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

public final class TestConfig {

    private static TestConfig instance;

    private final String baseUrl;
    private final boolean headless;
    private final int implicitWaitSec;
    private final int pageLoadTimeoutSec;

    @SuppressWarnings("unchecked")
    private TestConfig() {
        String env = System.getProperty("ENV", "local");
        String configDir = System.getProperty("config.dir",
                System.getProperty("user.dir") + "/../config");
        String configPath = configDir + "/" + env + ".yaml";

        Yaml yaml = new Yaml();
        try (InputStream input = new FileInputStream(configPath)) {
            Map<String, Object> root = yaml.load(input);
            this.baseUrl = (String) root.get("baseUrl");

            Map<String, Object> sel = (Map<String, Object>) root.get("selenium");
            this.headless = Boolean.TRUE.equals(sel.get("headless"));
            this.implicitWaitSec = (Integer) sel.get("implicitWaitSec");
            this.pageLoadTimeoutSec = (Integer) sel.get("pageLoadTimeoutSec");
        } catch (IOException e) {
            throw new RuntimeException("Cannot load config: " + configPath, e);
        }
    }

    public static TestConfig get() {
        if (instance == null) {
            instance = new TestConfig();
        }
        return instance;
    }

    public String getBaseUrl()          { return baseUrl; }
    public boolean isHeadless()         { return headless; }
    public int getImplicitWaitSec()     { return implicitWaitSec; }
    public int getPageLoadTimeoutSec()  { return pageLoadTimeoutSec; }
}
