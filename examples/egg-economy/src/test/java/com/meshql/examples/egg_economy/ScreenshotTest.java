package com.meshql.examples.egg_economy;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Takes screenshots of the Egg Economy frontend apps for the docs site.
 *
 * Requires the Docker Compose stack to be running (with nginx on port 8088).
 * Run with: mvn test -pl examples/egg-economy -Dtest=ScreenshotTest -Dscreenshot.baseUrl=http://localhost:8088
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScreenshotTest {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotTest.class);

    private static final String BASE_URL = System.getProperty("screenshot.baseUrl", "http://localhost:8088");
    private static final String OUTPUT_DIR = System.getProperty("screenshot.outputDir",
            "../../docs/assets/images/egg-economy");

    private static WebDriver driver;
    private static WebDriverWait wait;

    @BeforeAll
    static void setUp() {
        ChromeOptions options = new ChromeOptions();
        options.addArguments(
                "--headless=new",
                "--no-sandbox",
                "--disable-dev-shm-usage",
                "--window-size=1280,900"
        );

        String chromeBinary = System.getProperty("chrome.binary");
        if (chromeBinary != null) {
            options.setBinary(chromeBinary);
        }

        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));

        new File(OUTPUT_DIR).mkdirs();
        logger.info("Screenshots will be saved to: {}", new File(OUTPUT_DIR).getAbsolutePath());
        logger.info("Base URL: {}", BASE_URL);
    }

    @AfterAll
    static void tearDown() {
        if (driver != null) {
            driver.quit();
        }
    }

    @Test
    @Order(1)
    void dashboardApp_nationalOverview() throws Exception {
        // Alpine.js + CDN scripts have a race condition — retry if needed
        for (int attempt = 0; attempt < 3; attempt++) {
            driver.get(BASE_URL + "/dashboard/");
            try {
                // Wait for stat cards to appear (Alpine hydrated + GraphQL data loaded)
                new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
                    var stats = d.findElements(By.cssSelector(".stat-value"));
                    return stats.size() >= 4;
                });
                break;
            } catch (TimeoutException e) {
                logger.warn("Dashboard attempt {} — Alpine.js didn't hydrate, retrying", attempt + 1);
                if (attempt == 2) throw e;
            }
        }

        // Give Chart.js time to render canvases
        Thread.sleep(2000);

        // Verify stat cards loaded
        var statValues = driver.findElements(By.cssSelector(".stat-value"));
        assertTrue(statValues.size() >= 4, "Expected at least 4 stat cards on dashboard");

        saveScreenshot("dashboard.png");
    }

    @Test
    @Order(2)
    void homesteaderApp_farmSetup() throws Exception {
        // Clear localStorage to force farm picker
        driver.get(BASE_URL + "/homestead/");
        ((JavascriptExecutor) driver).executeScript("localStorage.clear()");
        driver.navigate().refresh();

        // Wait for React to mount — either farm setup (buttons) or tab bar
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("button")));
        Thread.sleep(1000);

        // Check if we're on the farm picker or already logged in
        String pageSource = driver.getPageSource();
        if (pageSource.contains("Select your farm")) {
            // Pick a farm known to have hens — "Riverside Ranch" has the most
            var farmButtons = driver.findElements(By.cssSelector("button.bg-white"));
            boolean found = false;
            for (WebElement btn : farmButtons) {
                if (btn.getText().contains("Riverside Ranch")) {
                    btn.click();
                    found = true;
                    break;
                }
            }
            if (!found && !farmButtons.isEmpty()) {
                farmButtons.get(farmButtons.size() - 1).click();
            }
            Thread.sleep(1000);
        }

        // Now we should be on the main app with tabs
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("nav")));
        Thread.sleep(500);

        saveScreenshot("homesteader-log.png");
    }

    @Test
    @Order(3)
    void homesteaderApp_myHens() throws Exception {
        // Navigate to My Hens tab
        for (WebElement btn : driver.findElements(By.tagName("button"))) {
            if (btn.getText().contains("My Hens")) {
                btn.click();
                break;
            }
        }

        // Wait for hen list to load
        Thread.sleep(1500);

        saveScreenshot("homesteader-hens.png");
    }

    @Test
    @Order(4)
    void corporateApp_dashboard() throws Exception {
        driver.get(BASE_URL + "/corporate/");

        // Wait for React to mount — sidebar and main content
        wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("aside")));
        Thread.sleep(1500);

        // Select a farm with hens (Riverside Ranch)
        try {
            var farmSelect = driver.findElement(By.tagName("select"));
            Select sel = new Select(farmSelect);
            sel.selectByVisibleText("Riverside Ranch");
            Thread.sleep(1500);
        } catch (Exception e) {
            logger.warn("Could not select Riverside Ranch farm: {}", e.getMessage());
        }

        // Verify dashboard KPI cards or "No production data" message loaded
        String pageSource = driver.getPageSource();
        assertTrue(pageSource.contains("Dashboard") || pageSource.contains("Egg Economy"),
                "Expected corporate portal to render");

        saveScreenshot("corporate-dashboard.png");
    }

    @Test
    @Order(5)
    void corporateApp_eventLog() throws Exception {
        // Click Events in sidebar
        for (WebElement btn : driver.findElements(By.tagName("button"))) {
            if (btn.getText().contains("Events")) {
                btn.click();
                break;
            }
        }

        // Wait for table to load
        Thread.sleep(1500);

        saveScreenshot("corporate-events.png");
    }

    private void saveScreenshot(String filename) throws IOException {
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Path target = Path.of(OUTPUT_DIR, filename);
        Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Saved screenshot: {}", target.toAbsolutePath());
    }
}
