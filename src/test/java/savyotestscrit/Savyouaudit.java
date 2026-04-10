package savyotestscrit;

import org.openqa.selenium.By;
import org.openqa.selenium.Cookie;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Savoy: customer login is required for Add to Cart. This class waits for a real logged-in header,
 * opens the shop like a user (hover + category), and if the catalog still loads as a guest it
 * logs in again via the direct login URL and returns to the same catalog URL.
 */
public class Savyouaudit {

    private static final String BASE_URL = "https://www.savoymedical-nycdoe.com/";
    /** Magento customer login — works even when no “Sign In” link is visible on the page. */
    private static final String CUSTOMER_LOGIN_URL = BASE_URL + "customer/account/login/";
    private static final String USER_EMAIL = "exinent45@yopmail.com";
    private static final String USER_PASSWORD = "test@1234";
    private static final Duration WAIT = Duration.ofSeconds(25);

    private static void waitDocumentComplete(WebDriver driver, WebDriverWait wait) {
        wait.until(d -> "complete".equals(
                ((JavascriptExecutor) d).executeScript("return document.readyState")));
    }

    private static String alignHrefToCurrentOrigin(WebDriver driver, String href) {
        if (href == null || href.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty href");
        }
        try {
            URI base = new URI(driver.getCurrentUrl());
            URI target = (href.startsWith("http://") || href.startsWith("https://"))
                    ? new URI(href)
                    : base.resolve(href);

            String scheme = base.getScheme();
            String host = base.getHost();
            int port = base.getPort();
            String tHost = target.getHost();
            if (tHost == null) {
                return target.toASCIIString();
            }

            if (host != null && !host.equalsIgnoreCase(tHost)) {
                target = new URI(scheme, target.getUserInfo(), host, port,
                        target.getPath(), target.getQuery(), target.getFragment());
            } else if (scheme != null && target.getScheme() != null
                    && !scheme.equalsIgnoreCase(target.getScheme())) {
                target = new URI(scheme, target.getUserInfo(), tHost, target.getPort(),
                        target.getPath(), target.getQuery(), target.getFragment());
            }
            return target.toASCIIString();
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Bad href: " + href, e);
        }
    }

    private static void logCookieSummary(WebDriver driver, String label) {
        Set<Cookie> cookies = driver.manage().getCookies();
        System.out.println(label + " — URL: " + driver.getCurrentUrl());
        System.out.println(label + " — cookie count: " + cookies.size());
        cookies.stream().map(c -> c.getName() + " (domain=" + c.getDomain() + ")").sorted()
                .forEach(n -> System.out.println("  " + n));
    }

    /** Magento logged-in header: customer menu toggle (not present for catalog guests). */
    private static boolean isCustomerMenuVisible(WebDriver driver) {
        List<WebElement> found = driver.findElements(By.xpath(
                "//button[contains(@class,'switch') and contains(@class,'action')]"));
        for (WebElement e : found) {
            try {
                if (e.isDisplayed()) {
                    return true;
                }
            } catch (Exception ignored) {
                // stale
            }
        }
        return false;
    }

    /** Guest header: link to customer login. */
    private static boolean isSignInLinkVisible(WebDriver driver) {
        List<WebElement> found = driver.findElements(By.xpath(
                "//a[contains(@href,'customer/account/login')]"));
        for (WebElement e : found) {
            try {
                if (e.isDisplayed()) {
                    return true;
                }
            } catch (Exception ignored) {
                // stale
            }
        }
        return false;
    }

    /**
     * After navigation, wait until we can tell guest vs logged-in (avoid racing the header).
     */
    private static void waitForHeaderToSettle(WebDriver driver, WebDriverWait wait) {
        wait.until(d -> isCustomerMenuVisible(d) || isSignInLinkVisible(d));
    }

    private static void waitUntilLoggedInHeader(WebDriverWait wait) {
        wait.until(Savyouaudit::isCustomerMenuVisible);
    }

    /**
     * Open login page and submit credentials. Call when {@link #CUSTOMER_LOGIN_URL} is loaded
     * (form with id email/pass).
     */
    private static boolean isElementDisplayed(WebDriver driver, By by) {
        List<WebElement> els = driver.findElements(by);
        for (WebElement e : els) {
            try {
                if (e.isDisplayed()) {
                    return true;
                }
            } catch (Exception ignored) {
                // stale
            }
        }
        return false;
    }

    private static void submitCustomerLoginForm(WebDriver driver, WebDriverWait wait) {
        WebElement user = wait.until(ExpectedConditions.visibilityOfElementLocated(By.id("email")));
        user.clear();
        user.sendKeys(USER_EMAIL);

        WebElement pass = driver.findElement(By.id("pass"));
        pass.clear();
        pass.sendKeys(USER_PASSWORD);

        WebElement submit = wait.until(
                ExpectedConditions.elementToBeClickable(By.xpath("//span[normalize-space()='SUBMIT']")));
        submit.click();
    }

    /**
     * Open Magento customer login; submit credentials only if the form is shown (not redirected).
     */
    private static void performFullLogin(WebDriver driver, WebDriverWait wait) {
        driver.get(CUSTOMER_LOGIN_URL);
        waitDocumentComplete(driver, wait);
        wait.until(d -> isCustomerMenuVisible(d) || isElementDisplayed(d, By.id("email")));

        if (!isCustomerMenuVisible(driver)) {
            submitCustomerLoginForm(driver, wait);
            wait.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("dashboard"),
                    ExpectedConditions.urlContains("account"),
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//span[contains(text(),'Shop Products')]"))));
            waitDocumentComplete(driver, wait);
        }
        waitUntilLoggedInHeader(wait);
        System.out.println("Login: customer menu visible — session established");
    }

    /**
     * If catalog opened as guest, re-login without hunting a “Sign In” button, then reopen catalog.
     * Waits for the customer menu first so a slow header does not trigger an extra login (that can
     * drop or replace the session before checkout).
     */
    private static void recoverSessionIfCatalogGuest(WebDriver driver, WebDriverWait wait, String catalogUrl) {
        waitForHeaderToSettle(driver, wait);
        try {
            new WebDriverWait(driver, Duration.ofSeconds(15)).until(Savyouaudit::isCustomerMenuVisible);
            System.out.println("Catalog: still logged in (customer menu present)");
            return;
        } catch (org.openqa.selenium.TimeoutException ignored) {
            // Header stayed without customer menu — treat as guest / lost session.
        }
        if (!isSignInLinkVisible(driver)) {
            System.out.println("Catalog: unclear header state — attempting session recovery anyway");
        } else {
            System.out.println("Catalog: guest state detected (Sign In link) — re-logging in");
        }
        logCookieSummary(driver, "Before re-login");

        performFullLogin(driver, wait);
        logCookieSummary(driver, "After re-login");

        System.out.println("Returning to catalog URL: " + catalogUrl);
        driver.get(catalogUrl);
        waitDocumentComplete(driver, wait);
        waitForHeaderToSettle(driver, wait);

        if (!isCustomerMenuVisible(driver)) {
            logCookieSummary(driver, "Still failing after re-login + catalog");
            throw new IllegalStateException(
                    "Still not logged in on catalog after recovery. Likely server/WAF blocking "
                            + "automation sessions — needs a test account exception or vendor support.");
        }
        System.out.println("Catalog: session recovered — customer menu visible");
    }

    private static String openShopCategoryAfterLogin(WebDriver driver, WebDriverWait wait, JavascriptExecutor js) {
        WebElement shopLabel = wait.until(
                ExpectedConditions.visibilityOfElementLocated(By.xpath("//span[contains(text(),'Shop Products')]")));

        WebElement hoverTarget = shopLabel;
        for (By ancestor : Arrays.asList(
                By.xpath("./ancestor::li[contains(@class,'level0')][1]"),
                By.xpath("./ancestor::li[1]"),
                By.xpath("./ancestor::a[1]"))) {
            try {
                hoverTarget = shopLabel.findElement(ancestor);
                break;
            } catch (Exception ignored) {
                // try next
            }
        }

        Actions actions = new Actions(driver);
        actions.moveToElement(hoverTarget).pause(Duration.ofMillis(700)).perform();

        WebDriverWait subWait = new WebDriverWait(driver, Duration.ofSeconds(8));
        try {
            WebElement submenuLink = subWait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//ul[contains(@class,'submenu')]"
                            + "//a[@href and normalize-space(@href)!='' and normalize-space(@href)!='#']"
                            + "[not(starts-with(normalize-space(@href),'javascript'))]")));
            js.executeScript("arguments[0].removeAttribute('target'); arguments[0].click();", submenuLink);
            System.out.println("Shop: clicked submenu category under Shop Products");
        } catch (org.openqa.selenium.TimeoutException e) {
            System.out.println("Shop: no submenu — using top-level Shop Products link");
            WebElement categoryAnchor = null;
            try {
                categoryAnchor = wait.until(ExpectedConditions.elementToBeClickable(
                        By.xpath("//a[.//span[contains(normalize-space(.),'Shop Products')]]")));
            } catch (org.openqa.selenium.TimeoutException e2) {
                // ignore
            }

            if (categoryAnchor != null) {
                String categoryHref = categoryAnchor.getAttribute("href");
                if (categoryHref != null && !categoryHref.trim().isEmpty()
                        && !categoryHref.trim().startsWith("javascript")
                        && !"#".equals(categoryHref.trim())) {
                    String categoryUrl = alignHrefToCurrentOrigin(driver, categoryHref);
                    System.out.println("Shop: opening aligned URL: " + categoryUrl);
                    js.executeScript("window.location.assign(arguments[0]);", categoryUrl);
                } else {
                    js.executeScript("arguments[0].removeAttribute('target'); arguments[0].click();", categoryAnchor);
                }
            } else {
                shopLabel.click();
            }
        }

        waitDocumentComplete(driver, wait);
        return driver.getCurrentUrl();
    }

    public static void main(String[] args) {
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--disable-dev-shm-usage");

        WebDriver driver = new ChromeDriver(options);
        WebDriverWait wait = new WebDriverWait(driver, WAIT);
        JavascriptExecutor js = (JavascriptExecutor) driver;

        try {
            driver.manage().window().maximize();
            performFullLogin(driver, wait);
            logCookieSummary(driver, "After first login");

            waitUntilLoggedInHeader(wait);
            String catalogUrl = openShopCategoryAfterLogin(driver, wait, js);
            logCookieSummary(driver, "After shop navigation");

            recoverSessionIfCatalogGuest(driver, wait, catalogUrl);
            logCookieSummary(driver, "After catalog session check");

            String mainWindow = driver.getWindowHandle();
            WebElement product = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//a[contains(text(),'ACCU-CHEK GUIDE ME')]")));
            js.executeScript(
                    "arguments[0].removeAttribute('target'); arguments[0].removeAttribute('rel'); arguments[0].click();",
                    product);
            System.out.println("Product link clicked");

            WebDriverWait shortWait = new WebDriverWait(driver, Duration.ofSeconds(8));
            boolean extraTab = false;
            try {
                shortWait.until(d -> d.getWindowHandles().size() > 1);
                extraTab = driver.getWindowHandles().size() > 1;
            } catch (org.openqa.selenium.TimeoutException e) {
                extraTab = false;
            }
            if (extraTab) {
                for (String h : driver.getWindowHandles()) {
                    if (!h.equals(mainWindow)) {
                        driver.switchTo().window(h);
                        System.out.println("Switched to product tab");
                        break;
                    }
                }
            } else {
                wait.until(ExpectedConditions.stalenessOf(product));
            }

            WebDriverWait longWait = new WebDriverWait(driver, Duration.ofSeconds(45));
            longWait.until(ExpectedConditions.or(
                    ExpectedConditions.presenceOfElementLocated(By.id("product_addtocart_form")),
                    ExpectedConditions.presenceOfElementLocated(
                            By.xpath("//span[normalize-space()='Add to Cart']"))));
            System.out.println("Product page loaded");

            WebElement addToCart = longWait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//span[normalize-space()='Add to Cart']")));
            try {
                addToCart.click();
            } catch (Exception e) {
                js.executeScript("arguments[0].click();", addToCart);
            }
            System.out.println("Product added to cart");

            WebElement cart = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//a[@class='action showcart']")));
            cart.click();
            System.out.println("Cart is displayed");

            WebElement view = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//span[normalize-space()='View and Edit Cart']")));
            view.click();
            System.out.println("View and edit cart is displayed");

            WebElement checkout = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//span[contains(text(),'Proceed to Checkout')]")));
            checkout.click();
            System.out.println("Checkout page is displayed");

            // Stay on checkout for logout: avoid navigating Home first (extra full reload / cache path).
            WebDriverWait onCheckout = new WebDriverWait(driver, Duration.ofSeconds(30));
            onCheckout.until(ExpectedConditions.or(
                    ExpectedConditions.urlContains("checkout"),
                    ExpectedConditions.urlContains("/checkout/")));

            WebElement drop = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("//button[contains(@class,'action') and contains(@class,'switch')]")));
            drop.click();
            System.out.println("Dropdown is displayed");

            WebElement logout = wait.until(
                    ExpectedConditions.elementToBeClickable(By.xpath("//a[contains(text(),'Sign Out')]")));
            logout.click();
            System.out.println("Logout successfully");
        } finally {
            driver.quit();
        }
    }
}
