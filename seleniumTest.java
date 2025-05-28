import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.CapabilityType;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.events.EventFiringWebDriver;

import com.google.common.collect.ImmutableMap;

public class CloudflareSeleniumTest {
    private WebDriver driver;
    private EventFiringWebDriver eventFiringWebDriver;
    private WebEventListener eventListener;

    @Before
    public void setup() {
        // 1) Setup ChromeDriver via WebDriverManager
        WebDriverManager.chromedriver().setup();

        // 2) Configure ChromeOptions to look less automated
        ChromeOptions options = new ChromeOptions();
        options.setExperimentalOption("excludeSwitches",
                Collections.singletonList("enable-automation"));
        options.setExperimentalOption("useAutomationExtension", false);
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("start-maximized");
        // (optional) set a realistic desktop user-agent
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                + "AppleWebKit/537.36 (KHTML, like Gecko) "
                + "Chrome/112.0.0.0 Safari/537.36");

        // Translation preferences
        Map<String, Object> prefs = new HashMap<>();
        Map<String, Object> langs = new HashMap<>();
        langs.put("tr", "en");
        langs.put("fr", "en");
        langs.put("de", "en");
        prefs.put("translate_whitelists", langs);
        prefs.put("translate", ImmutableMap.of("enabled", true));
        // prefs.put("intl.accept_languages", "tr");
        options.setExperimentalOption("prefs", prefs);

        // Logging preferences
        LoggingPreferences logs = new LoggingPreferences();
        logs.enable(LogType.DRIVER, Level.ALL);
        options.setCapability(CapabilityType.LOGGING_PREFS, logs);

        // Instantiate WebDriver
        driver = new ChromeDriver(options);
        
        // Wrap with EventFiringWebDriver and register listener
        eventFiringWebDriver = new EventFiringWebDriver(driver);
        eventListener = new WebEventListener();
        eventFiringWebDriver.register(eventListener);

        // Browser window and timeouts
        eventFiringWebDriver.manage().timeouts().implicitlyWait(45, TimeUnit.SECONDS);
    }

     @Test
    public void testCloudflareBypass() {
        // Navigate to CF-protected page
        String url = "https://example.com/protected";
        eventFiringWebDriver.get(url);

        // Wait utility
        WebDriverWait wait = new WebDriverWait(eventFiringWebDriver, Duration.ofSeconds(30));

        // Locate the Cloudflare iframe inside its shadow DOM using JavaScript
        WebElement cfIframe = (WebElement) ((org.openqa.selenium.JavascriptExecutor) eventFiringWebDriver)
            .executeScript(
                "const host = document.querySelector('cf-challenge');" +
                "const iframe = host && host.shadowRoot && host.shadowRoot.querySelector('iframe');" +
                "return iframe;"
            );

        // Switch into the CF challenge iframe
        eventFiringWebDriver.switchTo().frame(cfIframe);

        // Wait until the JS challenge completes (URL no longer contains chk_js)
        wait.until(d -> !d.getCurrentUrl().contains("/cdn-cgi/l/chk_js"));

        // Switch back to main document
        eventFiringWebDriver.switchTo().defaultContent();

        // Assert or log final page
        System.out.println("Bypass complete: " + eventFiringWebDriver.getCurrentUrl());
    }

    @Test
    public void testWithManualCaptchaSolve() {
        // 1) Navigate to the page containing the Google reCAPTCHA
        String url = "https://example.com/page-with-recaptcha";
        eventFiringWebDriver.get(url);

        // 2) Wait for the reCAPTCHA widget to render
        WebDriverWait wait = new WebDriverWait(eventFiringWebDriver, Duration.ofSeconds(15));
        // (adjust selector if needed)
        wait.until(d -> ((JavascriptExecutor)d)
            .executeScript("return document.querySelector('.g-recaptcha') != null"));

        // 3) Pause and prompt user to solve CAPTCHA
        System.out.println("=== CAPTCHA DETECTED ===");
        System.out.println("Please solve the CAPTCHA in the browser window,");
        System.out.println("then come back here and press ENTER to continue...");
        new Scanner(System.in).nextLine();

        // 4) After user presses ENTER, you can continue your assertions
        //    e.g. wait for some element on the post-CAPTCHA page:
        wait.until(d -> ((JavascriptExecutor)d)
            .executeScript("return document.querySelector('#protected-content') != null"));

        System.out.println("CAPTCHA solved. Page title is: " + eventFiringWebDriver.getTitle());
    }

    @After
    public void tearDown() {
        if (eventFiringWebDriver != null) {
            eventFiringWebDriver.quit();
        }
    }
}
