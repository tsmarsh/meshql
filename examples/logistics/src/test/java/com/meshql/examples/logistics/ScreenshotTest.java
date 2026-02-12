package com.meshql.examples.logistics;

import org.junit.jupiter.api.*;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
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
 * Takes screenshots of the SwiftShip frontend apps for the docs site.
 *
 * Requires the Docker Compose stack to be running (with nginx on the expected port).
 * Run with: mvn test -pl examples/logistics -Dtest=ScreenshotTest -Dscreenshot.baseUrl=http://localhost:8888
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ScreenshotTest {

    private static final Logger logger = LoggerFactory.getLogger(ScreenshotTest.class);

    private static final String BASE_URL = System.getProperty("screenshot.baseUrl", "http://localhost:8080");
    private static final String OUTPUT_DIR = System.getProperty("screenshot.outputDir",
            "../../docs/assets/images/logistics");

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
    void customerApp_trackingResult() throws Exception {
        driver.get(BASE_URL + "/track/");

        // Wait for React to mount
        wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("input")));
        Thread.sleep(500);

        // Enter tracking number
        WebElement input = driver.findElement(By.cssSelector("input"));
        input.clear();
        input.sendKeys("PKG-DEN1A001");

        // Click Track button
        for (WebElement btn : driver.findElements(By.tagName("button"))) {
            if (btn.getText().toLowerCase().contains("track")) {
                btn.click();
                break;
            }
        }

        // Wait for results — look for package detail content
        Thread.sleep(3000);

        // Verify we got results, not an error
        String pageSource = driver.getPageSource();
        assertTrue(pageSource.contains("Alice Johnson") || pageSource.contains("PKG-DEN1A001"),
                "Expected package details to appear after tracking lookup");

        saveScreenshot("customer-tracking.png");
    }

    @Test
    @Order(2)
    void adminApp_warehouseDashboard() throws Exception {
        // Alpine.js + module scripts have a race condition — retry page load if needed
        for (int attempt = 0; attempt < 3; attempt++) {
            driver.get(BASE_URL + "/admin/");
            try {
                // Wait for warehouse cards to appear (Alpine hydrated + REST data loaded)
                new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
                    var cards = d.findElements(By.cssSelector(".card.cursor-pointer"));
                    return !cards.isEmpty();
                });
                break;
            } catch (TimeoutException e) {
                logger.warn("Admin app attempt {} — Alpine.js didn't hydrate, retrying", attempt + 1);
                if (attempt == 2) throw e;
            }
        }

        // Click first warehouse card
        driver.findElement(By.cssSelector(".card.cursor-pointer")).click();

        // Wait for the GraphQL dashboard query — stats section appears
        wait.until(d -> {
            var stats = d.findElements(By.cssSelector(".stats .stat-value"));
            return !stats.isEmpty();
        });
        Thread.sleep(500);

        saveScreenshot("admin-dashboard.png");
    }

    @Test
    @Order(3)
    void reportingApp_dashboard() throws Exception {
        // Same Alpine.js race condition — retry if needed
        for (int attempt = 0; attempt < 3; attempt++) {
            driver.get(BASE_URL + "/reports/");
            try {
                new WebDriverWait(driver, Duration.ofSeconds(10)).until(d -> {
                    var stats = d.findElements(By.cssSelector(".stat-value"));
                    return stats.size() >= 4;
                });
                break;
            } catch (TimeoutException e) {
                logger.warn("Reports app attempt {} — data didn't load, retrying", attempt + 1);
                if (attempt == 2) throw e;
            }
        }

        // Give Chart.js time to render canvases
        Thread.sleep(2000);

        saveScreenshot("reporting-dashboard.png");
    }

    private void saveScreenshot(String filename) throws IOException {
        File screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
        Path target = Path.of(OUTPUT_DIR, filename);
        Files.copy(screenshot.toPath(), target, StandardCopyOption.REPLACE_EXISTING);
        logger.info("Saved screenshot: {}", target.toAbsolutePath());
    }
}
