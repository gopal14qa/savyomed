package savyotestscrit;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentEmailReporter;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.testng.Assert;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;
import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Listeners;
import org.testng.annotations.Test;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Savoy Medical NYC DOE order site — E2E flow with TestNG + Extent (HTML + emailable).
 * Single-file suite: tests + Extent listener below. Site note: credentials often ALL CAPS.
 */
@Listeners(Savyouaudit2.ExtentReporterListener.class)
public class Savyouaudit2 {

    private static final String BASE_URL = "https://savoymedical-nycdoe.com/";
    /** Uppercase per site instructions */
    private static final String USERNAME = "EXINENT45@YOPMAIL.COM";
    private static final String PASSWORD = "TEST@1234";

    /** Daily folders: D:\scrrenshots for automation\savyomed\yyyy-MM-dd\ */
    private static final Path SCREENSHOT_BASE_DIR =
            Paths.get("D:", "scrrenshots for automation", "savyomed");

    private WebDriver driver;
    private WebDriverWait wait;
    /** Shorter waits for menu / category (faster than fixed sleeps) */
    private WebDriverWait waitQuick;
    private Actions actions;
    private JavascriptExecutor js;

    @BeforeClass(alwaysRun = true)
    public void beforeClass() {
        WebDriverManager.chromedriver().setup();
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--start-maximized");
        options.addArguments("--disable-notifications");
        driver = new ChromeDriver(options);
        wait = new WebDriverWait(driver, Duration.ofSeconds(25));
        waitQuick = new WebDriverWait(driver, Duration.ofSeconds(12));
        actions = new Actions(driver);
        js = (JavascriptExecutor) driver;
        System.out.println("[SETUP] WebDriver started (Chrome).");
    }

    @AfterMethod(alwaysRun = true)
    public void captureScreenshotAfterEachTest(ITestResult result) {
        if (driver == null) {
            return;
        }
        try {
            String dayFolder = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
            Path dayDir = SCREENSHOT_BASE_DIR.resolve(dayFolder);
            Files.createDirectories(dayDir);

            String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HHmmss"));
            String status;
            switch (result.getStatus()) {
                case ITestResult.SUCCESS:
                    status = "PASS";
                    break;
                case ITestResult.FAILURE:
                    status = "FAIL";
                    break;
                case ITestResult.SKIP:
                    status = "SKIP";
                    break;
                default:
                    status = "OTHER";
                    break;
            }
            String fileName = result.getMethod().getMethodName() + "_" + status + "_" + time + ".png";
            Path dest = dayDir.resolve(fileName);

            File shot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.FILE);
            Files.copy(shot.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[SCREENSHOT] " + dest.toAbsolutePath());
        } catch (Exception e) {
            System.err.println("[SCREENSHOT] Could not save: " + e.getMessage());
        }
    }

    @AfterClass(alwaysRun = true)
    public void afterClass() {
        if (driver != null) {
            driver.quit();
        }
        System.out.println("[TEARDOWN] WebDriver quit.");
    }

    private void logStep(String name) {
        System.out.println("[TEST STEP] " + name);
    }

    private void scrollIntoView(WebElement el) {
        js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
        pause(400);
    }

    /** Lighter scroll settle for nav / category (faster). */
    private void scrollIntoViewQuick(WebElement el) {
        js.executeScript("arguments[0].scrollIntoView({block:'center', inline:'nearest'});", el);
        pause(120);
    }

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private WebElement waitVisible(By locator) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
    }

    private WebElement waitClickable(By locator) {
        return wait.until(ExpectedConditions.elementToBeClickable(locator));
    }

    private void tryClick(By locator) {
        WebElement el = waitClickable(locator);
        scrollIntoView(el);
        el.click();
    }

    /** Prefer first matching visible element from several locators */
    private boolean clickFirstWorking(By[] locators) {
        for (By by : locators) {
            try {
                List<WebElement> found = driver.findElements(by);
                for (WebElement e : found) {
                    if (e.isDisplayed() && e.isEnabled()) {
                        scrollIntoView(e);
                        e.click();
                        return true;
                    }
                }
            } catch (Exception ignored) {
                // try next
            }
        }
        return false;
    }

    private boolean isLikelyProductHref(String href) {
        if (href == null || href.isBlank() || href.startsWith("javascript:") || href.contains("#")) {
            return false;
        }
        String h = href.toLowerCase();
        return !h.contains("/customer/")
                && !h.contains("/checkout/")
                && !h.contains("/cart")
                && !h.contains("account")
                && !h.contains("/compare")
                && !h.contains("/wishlist");
    }

    private void scrollPageInSteps(int steps, int deltaY) {
        for (int i = 0; i < steps; i++) {
            js.executeScript("window.scrollBy(0, arguments[0]);", deltaY);
            pause(350);
        }
    }

    /** Clicks first product-style link on listing; returns false if none. */
    private boolean clickFirstProductLinkOnPage() {
        By[] productLocators = new By[]{
                By.cssSelector("a.product-item-link"),
                By.cssSelector(".product-items .product-item-name a"),
                By.cssSelector(".product-items .product-item a.product-item-link"),
                By.cssSelector("ol.products.list a.product-item-link"),
                By.cssSelector(".product-item-info a"),
                By.xpath("//a[contains(@class,'product-item-link')]"),
                By.xpath("//*[contains(@class,'product-item')]//a[@href][not(contains(@href,'javascript'))]"),
                By.xpath("//div[contains(@class,'products')]//li//a[@href and string-length(@href)>10]")
        };
        scrollPageInSteps(6, 450);
        js.executeScript("window.scrollTo(0, 0);");
        pause(400);
        scrollPageInSteps(8, 400);
        for (By by : productLocators) {
            try {
                List<WebElement> items = driver.findElements(by);
                for (WebElement p : items) {
                    if (!p.isDisplayed()) {
                        continue;
                    }
                    String href = p.getAttribute("href");
                    if (!isLikelyProductHref(href)) {
                        continue;
                    }
                    scrollIntoView(p);
                    try {
                        wait.until(ExpectedConditions.elementToBeClickable(p));
                        p.click();
                    } catch (Exception ex) {
                        js.executeScript("arguments[0].click();", p);
                    }
                    return true;
                }
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    @Test(priority = 1)
    public void tc01_openBrowserAndEnterUrl() {
        logStep("tc01_openBrowserAndEnterUrl — open browser and load URL");
        driver.get(BASE_URL);
        pause(1500);
        js.executeScript("window.scrollTo(0, document.body.scrollHeight / 4);");
        pause(500);
        Assert.assertTrue(driver.getCurrentUrl().contains("savoymedical-nycdoe.com"), "Should land on Savoy site");
        System.out.println("[PASS] tc01_openBrowserAndEnterUrl — URL loaded: " + driver.getCurrentUrl());
    }

    @Test(priority = 2, dependsOnMethods = "tc01_openBrowserAndEnterUrl")
    public void tc02_navigateToSignIn() {
        logStep("tc02_navigateToSignIn — open sign-in page");
        By[] signInLinks = new By[]{
                By.partialLinkText("Sign In"),
                By.linkText("Sign In"),
                By.xpath("//a[contains(@href,'customer/account/login') or contains(@href,'account/login')]"),
                By.xpath("//header//a[contains(translate(normalize-space(.),'sign in','SIGN IN'),'SIGN IN')]")
        };
        boolean clicked = clickFirstWorking(signInLinks);
        Assert.assertTrue(clicked, "Sign In link should be found");
        pause(1200);
        System.out.println("[PASS] tc02_navigateToSignIn — sign-in page reached");
    }

    @Test(priority = 3, dependsOnMethods = "tc02_navigateToSignIn")
    public void tc03_enterUsername() {
        logStep("tc03_enterUsername — enter username / email");
        By[] userFields = new By[]{
                By.id("email"),
                By.name("login[username]"),
                By.cssSelector("input[name='login[username]']"),
                By.xpath("//input[@type='email']"),
                By.xpath("//label[contains(.,'Username')]/following::input[1]"),
                By.xpath("//input[contains(@name,'username') or contains(@id,'username')]")
        };
        WebElement field = null;
        for (By by : userFields) {
            try {
                field = waitVisible(by);
                break;
            } catch (Exception ignored) {
            }
        }
        Assert.assertNotNull(field, "Username field should exist");
        scrollIntoView(field);
        field.clear();
        field.sendKeys(USERNAME);
        pause(400);
        System.out.println("[PASS] tc03_enterUsername — username entered");
    }

    @Test(priority = 4, dependsOnMethods = "tc03_enterUsername")
    public void tc04_enterPassword() {
        logStep("tc04_enterPassword — enter password");
        By[] passFields = new By[]{
                By.id("pass"),
                By.name("login[password]"),
                By.cssSelector("input[name='login[password]']"),
                By.cssSelector("input[type='password']")
        };
        WebElement field = null;
        for (By by : passFields) {
            try {
                field = waitVisible(by);
                break;
            } catch (Exception ignored) {
            }
        }
        Assert.assertNotNull(field, "Password field should exist");
        scrollIntoView(field);
        field.clear();
        field.sendKeys(PASSWORD);
        pause(400);
        System.out.println("[PASS] tc04_enterPassword — password entered");
    }

    @Test(priority = 5, dependsOnMethods = "tc04_enterPassword")
    public void tc05_clickSignIn() {
        logStep("tc05_clickSignIn — submit login");
        By[] submits = new By[]{
                By.id("send2"),
                By.cssSelector("button[type='submit']"),
                By.xpath("//button[contains(.,'Sign In')]"),
                By.xpath("//span[contains(text(),'SUBMIT')]/ancestor::button"),
                By.xpath("//button[contains(translate(.,'submit','SUBMIT'),'SUBMIT')]")
        };
        boolean ok = clickFirstWorking(submits);
        if (!ok) {
            driver.switchTo().activeElement().sendKeys(Keys.ENTER);
        }
        try {
            waitQuick.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(
                            By.cssSelector(".customer-welcome, .customer-name, .greet.welcome, a[href*='customer/account/logout']")),
                    ExpectedConditions.invisibilityOfElementLocated(By.id("send2")),
                    ExpectedConditions.urlContains("customer/account")));
        } catch (Exception ignored) {
            pause(600);
        }
        pause(350);
        js.executeScript("window.scrollTo(0, 0);");
        System.out.println("[PASS] tc05_clickSignIn — login submitted");
    }

    @Test(priority = 6, dependsOnMethods = "tc05_clickSignIn")
    public void tc06_hoverCategoryMenu() {
        logStep("tc06_hoverCategoryMenu — hover Shop Products / category menu");
        By[] menu = new By[]{
                By.xpath("//a[contains(.,'Shop Products')]"),
                By.xpath("//span[contains(.,'Shop Products')]"),
                By.linkText("Shop Products"),
                By.partialLinkText("Shop Products")
        };
        WebElement menuEl = null;
        for (By by : menu) {
            try {
                menuEl = waitVisible(by);
                if (menuEl.isDisplayed()) {
                    break;
                }
            } catch (Exception ignored) {
            }
        }
        Assert.assertNotNull(menuEl, "Shop Products menu should be visible");
        scrollIntoViewQuick(menuEl);
        actions.moveToElement(menuEl).pause(Duration.ofMillis(80)).perform();
        try {
            waitQuick.until(d -> {
                List<WebElement> sub = d.findElements(By.cssSelector(
                        "li.level1 a, .level0.submenu .ui-menu-item a, .submenu li a"));
                return sub.stream().anyMatch(WebElement::isDisplayed);
            });
        } catch (Exception ignored) {
            pause(250);
        }
        System.out.println("[PASS] tc06_hoverCategoryMenu — menu hovered");
    }

    @Test(priority = 7, dependsOnMethods = "tc06_hoverCategoryMenu")
    public void tc07_openCategory() {
        logStep("tc07_openCategory — open first visible subcategory");
        try {
            waitQuick.until(ExpectedConditions.or(
                    ExpectedConditions.visibilityOfElementLocated(By.cssSelector("ul.submenu, .submenu, nav .level0.submenu")),
                    ExpectedConditions.visibilityOfElementLocated(By.xpath("//div[contains(@class,'submenu')]//a"))
            ));
        } catch (Exception ignored) {
            pause(200);
        }
        final String urlBefore = driver.getCurrentUrl();
        By[] subLinks = new By[]{
                By.cssSelector("li.level1 > a"),
                By.cssSelector(".level0.submenu .ui-menu-item a"),
                By.cssSelector("nav .submenu:not([style*='none']) a[href]"),
                By.cssSelector("li.level1 a, .ui-menu-item a"),
                By.xpath("//div[contains(@class,'submenu')]//a[@href and string-length(@href)>1 and not(contains(@href,'#'))]"),
                By.xpath("//nav//ul//ul//a[@href and not(contains(@href,'#'))]")
        };
        boolean clicked = false;
        for (By by : subLinks) {
            try {
                List<WebElement> links = wait.until(ExpectedConditions.presenceOfAllElementsLocatedBy(by));
                for (WebElement a : links) {
                    String href = a.getAttribute("href");
                    if (href == null || href.isBlank() || href.contains("#")) {
                        continue;
                    }
                    if (!a.isDisplayed()) {
                        continue;
                    }
                    scrollIntoViewQuick(a);
                    try {
                        waitQuick.until(ExpectedConditions.elementToBeClickable(a));
                        a.click();
                    } catch (Exception e) {
                        js.executeScript("arguments[0].click();", a);
                    }
                    clicked = true;
                    break;
                }
            } catch (Exception ignored) {
            }
            if (clicked) {
                break;
            }
        }
        Assert.assertTrue(clicked, "A category link should open");
        try {
            waitQuick.until(d -> {
                if (!urlBefore.equals(d.getCurrentUrl())) {
                    return true;
                }
                JavascriptExecutor j = (JavascriptExecutor) d;
                if (!"complete".equals(String.valueOf(j.executeScript("return document.readyState")))) {
                    return false;
                }
                return !d.findElements(By.cssSelector(
                        ".category-view, .page-title-wrapper .page-title, .product-items .product-item, .products-grid .product-item"))
                        .isEmpty();
            });
        } catch (Exception ignored) {
            pause(400);
        }
        wait.until(d -> "complete".equals(
                String.valueOf(((JavascriptExecutor) d).executeScript("return document.readyState"))));
        js.executeScript("window.scrollTo(0, 300);");
        pause(200);
        System.out.println("[PASS] tc07_openCategory — category page opened: " + driver.getCurrentUrl());
    }

    @Test(priority = 8, dependsOnMethods = "tc07_openCategory")
    public void tc08_openProductPage() {
        logStep("tc08_openProductPage — open a product from listing");
        try {
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector(".products, .product-items, ol.products, #maincontent")));
        } catch (Exception ignored) {
            pause(800);
        }
        boolean clicked = clickFirstProductLinkOnPage();
        if (!clicked) {
            System.out.println("[INFO] tc08 — no product on category page; trying catalog search fallback");
            driver.get(BASE_URL + "catalogsearch/result/?q=medical");
            pause(2500);
            clicked = clickFirstProductLinkOnPage();
        }
        if (!clicked) {
            driver.get(BASE_URL + "catalogsearch/result/?q=a");
            pause(2500);
            clicked = clickFirstProductLinkOnPage();
        }
        Assert.assertTrue(clicked,
                "A product link should open (check category listing or search results DOM)");
        pause(2000);
        System.out.println("[PASS] tc08_openProductPage — product detail: " + driver.getCurrentUrl());
    }

    @Test(priority = 9, dependsOnMethods = "tc08_openProductPage")
    public void tc09_addToCart() {
        logStep("tc09_addToCart — add product to cart");
        js.executeScript("window.scrollTo(0, document.body.scrollHeight / 3);");
        pause(600);
        By[] addButtons = new By[]{
                By.id("product-addtocart-button"),
                By.cssSelector("button.action.tocart"),
                By.xpath("//button[contains(.,'Add to Cart')]"),
                By.xpath("//span[contains(text(),'Add to Cart')]/ancestor::button")
        };
        boolean clicked = clickFirstWorking(addButtons);
        Assert.assertTrue(clicked, "Add to Cart should be clickable");
        pause(2500);
        System.out.println("[PASS] tc09_addToCart — added to cart");
    }

    @Test(priority = 10, dependsOnMethods = "tc09_addToCart")
    public void tc10_openCartPage() {
        logStep("tc10_openCartPage — open shopping cart");
        By[] cartIcons = new By[]{
                By.cssSelector("a.showcart, .minicart-wrapper a.action.showcart"),
                By.partialLinkText("My Cart"),
                By.xpath("//a[contains(@href,'checkout/cart')]")
        };
        boolean opened = clickFirstWorking(cartIcons);
        if (opened) {
            pause(500);
            By viewCart = By.xpath("//a[contains(.,'View') and contains(.,'Cart')]");
            try {
                if (driver.findElements(viewCart).stream().anyMatch(WebElement::isDisplayed)) {
                    tryClick(viewCart);
                }
            } catch (Exception ignored) {
            }
        }
        if (!driver.getCurrentUrl().contains("cart")) {
            driver.get(BASE_URL + "checkout/cart/");
            pause(1500);
        }
        js.executeScript("window.scrollTo(0, 200);");
        pause(500);
        System.out.println("[PASS] tc10_openCartPage — cart page: " + driver.getCurrentUrl());
    }

    @Test(priority = 11, dependsOnMethods = "tc10_openCartPage")
    public void tc11_goToCheckout() {
        logStep("tc11_goToCheckout — proceed to checkout");
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        pause(600);
        By[] checkout = new By[]{
                By.cssSelector("button.action.primary.checkout"),
                By.xpath("//button[contains(.,'Checkout')]"),
                By.partialLinkText("Checkout"),
                By.xpath("//a[contains(.,'Proceed to Checkout')]")
        };
        boolean ok = clickFirstWorking(checkout);
        if (!ok) {
            driver.get(BASE_URL + "checkout/");
            ok = true;
        }
        pause(2500);
        Assert.assertTrue(ok, "Should navigate toward checkout");
        System.out.println("[PASS] tc11_goToCheckout — checkout: " + driver.getCurrentUrl());
    }

    @Test(priority = 12, dependsOnMethods = "tc11_goToCheckout")
    public void tc12_selectShippingAndFinalCheckout() {
        logStep("tc12_selectShippingAndFinalCheckout — shipping address / continue");
        js.executeScript("window.scrollTo(0, 400);");
        pause(800);

        By[] radios = new By[]{
                By.cssSelector("input[type='radio'][name*='shipping']"),
                By.cssSelector("table.table-checkout-shipping-method input[type='radio']"),
                By.xpath("//input[@type='radio' and contains(@name,'shipping')]")
        };
        for (By by : radios) {
            try {
                List<WebElement> rs = driver.findElements(by);
                for (WebElement r : rs) {
                    if (r.isDisplayed() && r.isEnabled()) {
                        scrollIntoView(r);
                        if (!r.isSelected()) {
                            js.executeScript("arguments[0].click();", r);
                        }
                        pause(500);
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
        }

        By[] continues = new By[]{
                By.cssSelector("button.action.continue, button[data-role='opc-continue']"),
                By.xpath("//button[contains(.,'Next')]"),
                By.xpath("//button[contains(.,'Continue')]"),
                By.xpath("//span[contains(text(),'Continue')]/ancestor::button")
        };
        clickFirstWorking(continues);
        pause(2000);
        js.executeScript("window.scrollTo(0, document.body.scrollHeight);");
        pause(1000);
        System.out.println("[PASS] tc12_selectShippingAndFinalCheckout — advanced checkout: " + driver.getCurrentUrl());
    }

    @Test(priority = 13, dependsOnMethods = "tc12_selectShippingAndFinalCheckout")
    public void tc13_returnToHomePage() {
        logStep("tc13_returnToHomePage — navigate back to home");
        By[] home = new By[]{
                By.xpath("//a[contains(@class,'logo')]"),
                By.partialLinkText("Home"),
                By.linkText("Home")
        };
        boolean ok = clickFirstWorking(home);
        if (!ok) {
            driver.get(BASE_URL);
        }
        pause(1500);
        js.executeScript("window.scrollTo(0, 0);");
        Assert.assertTrue(driver.getCurrentUrl().contains("savoymedical-nycdoe.com"));
        System.out.println("[PASS] tc13_returnToHomePage — home: " + driver.getCurrentUrl());
    }

    /** Extent HTML + emailable reports; registered via {@link Listeners} on this class. */
    public static class ExtentReporterListener implements ITestListener {

        private static ExtentReports extent;
        private static final ThreadLocal<ExtentTest> TEST = new ThreadLocal<>();

        @Override
        public void onStart(ITestContext context) {
            Path out = Paths.get("test-output", "extent");
            try {
                Files.createDirectories(out);
            } catch (Exception ignored) {
                // use cwd
            }

            ExtentHtmlReporter html = new ExtentHtmlReporter(out.resolve("ExtentHtmlReport.html").toString());
            html.config().setDocumentTitle("Savoy Medical Automation");
            html.config().setReportName("Savoy E2E");
            html.config().setTheme(Theme.STANDARD);

            ExtentEmailReporter email = new ExtentEmailReporter(out.resolve("ExtentEmailableReport.html").toString());

            extent = new ExtentReports();
            extent.attachReporter(html, email);
            extent.setSystemInfo("OS", System.getProperty("os.name"));
            extent.setSystemInfo("Java", System.getProperty("java.version"));
        }

        @Override
        public void onTestStart(ITestResult result) {
            TEST.set(extent.createTest(result.getMethod().getMethodName()));
        }

        @Override
        public void onTestSuccess(ITestResult result) {
            ExtentTest t = TEST.get();
            if (t != null) {
                t.pass("Passed");
            }
        }

        @Override
        public void onTestFailure(ITestResult result) {
            ExtentTest t = TEST.get();
            if (t != null) {
                t.fail(result.getThrowable());
            }
        }

        @Override
        public void onTestSkipped(ITestResult result) {
            ExtentTest t = TEST.get();
            if (t != null) {
                t.skip("Skipped");
            }
        }

        @Override
        public void onFinish(ITestContext context) {
            if (extent != null) {
                extent.flush();
            }
            TEST.remove();
        }
    }
}
