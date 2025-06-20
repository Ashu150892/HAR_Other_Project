package com.practice.lph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.v85.network.Network;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.time.Duration;
import java.util.*;

public class CapturingHAR {

    public void CaptHAR() {
        // Setup WebDriver and Chrome DevTools Protocol (CDP)
        WebDriverManager.chromedriver().setup();

        // Set ChromeOptions for CDP
        ChromeOptions options = new ChromeOptions();
        options.addArguments("--remote-debugging-port=9222");  // Enable remote debugging
        options.addArguments("--headless");  // Run Chrome in headless mode, optional
        WebDriver driver = new ChromeDriver(options);

        // Use DevTools Protocol to capture network logs
        DevTools devTools = ((ChromeDriver) driver).getDevTools();
        devTools.createSession();
        devTools.send(Network.enable(Optional.of(10000), Optional.of(10000), Optional.of(10000)));

        // Add listener to capture all network requests
        devTools.addListener(Network.requestWillBeSent(), request -> {
            String requestUrl = request.getRequest().getUrl();
            String method = request.getRequest().getMethod();

            // Print all requests including the method type
            System.out.println("Captured Request: " + method + " " + requestUrl);

            // Optionally, filter based on specific URL patterns (e.g., userLogin)
            if (requestUrl.contains("bpsso.lenovo.com/webauthn/userLogin")) {
                System.out.println("✅ Found the userLogin request: " + requestUrl);
            }
        });

        try {
            // Wait for DevTools to initialize properly
            Thread.sleep(1000);  // Ensure DevTools setup completes before the page load begins

            // Open the website and perform login steps
            driver.get("https://www.lenovopartnerhub.com/");
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[normalize-space()='Login']"))).click();

            // Email login
            try {
                driver.findElement(By.xpath("//span[@class='nametText'][normalize-space()='lph_perft@lenovo.com']")).click();
            } catch (Exception e) {
                driver.findElement(By.xpath("//input[@placeholder='Email address']")).sendKeys("lph_perft@lenovo.com");
                driver.findElement(By.xpath("//a[@class='nextTop10 next']")).click();
            }

            // Proceed with password
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@id='loginClass7']//a[@class='next'][normalize-space()='Next']"))).click();
            driver.findElement(By.xpath("//input[@placeholder='Password']")).sendKeys("LPHtest@123");
            driver.findElement(By.xpath("//form[@action='/webauthn/userLogin']//a[@class='next'][normalize-space()='Next']")).click();

            // Wait for page to load completely
            Thread.sleep(35000); // Wait for full load (adjust time if necessary)

            JavascriptExecutor js = (JavascriptExecutor) driver;
            wait.until(wd -> js.executeScript("return document.readyState").equals("complete"));

            // Wait for performance data
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(
                    d -> ((Number) js.executeScript("return performance.timing.loadEventEnd")).longValue() > 0
            );

            System.out.println("📌 loadEventEnd: " + js.executeScript("return performance.timing.loadEventEnd") + " ms");

            // Get resource and xhr JSON data
            String resourceJson = (String) js.executeScript("return JSON.stringify(performance.getEntriesByType('resource'));");
            String xhrJson = (String) js.executeScript("return JSON.stringify(performance.getEntriesByType('xmlhttprequest'));");

            // Combine resources and xhr data
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            List<Map<String, Object>> resources = gson.fromJson(resourceJson, new TypeToken<List<Map<String, Object>>>() {}.getType());
            List<Map<String, Object>> xhrResources = gson.fromJson(xhrJson, new TypeToken<List<Map<String, Object>>>() {}.getType());
            resources.addAll(xhrResources);

            // Sort resources by duration (descending)
            resources.sort((a, b) -> {
                double durA = ((Number) a.get("duration")).doubleValue();
                double durB = ((Number) b.get("duration")).doubleValue();
                return Double.compare(durB, durA);
            });

            // Save resources data to Excel, JSON, and HAR files
            writeToExcel(resources, "Response_Sheet.xlsx");
            saveJson(gson.toJson(resources), "PerformanceData.json");
            saveJson(gson.toJson(resources), "PerformanceData.har");

            System.out.println("\n✅ Total number of entries: " + resources.size());

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            driver.quit();
        }
    }

    private static void writeToExcel(List<Map<String, Object>> data, String fileName) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Performance Data");

        String[] headers = {"URL", "Start Time (ms)", "End Time (ms)", "Duration (ms)"};
        Row headerRow = sheet.createRow(0);

        // Create header row
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }

        // Write data rows
        int rowNum = 1;
        for (Map<String, Object> entry : data) {
            Row row = sheet.createRow(rowNum++);
            String name = (String) entry.get("name");
            double startTime = ((Number) entry.get("startTime")).doubleValue();
            double duration = ((Number) entry.get("duration")).doubleValue();
            double endTime = startTime + duration;

            row.createCell(0).setCellValue(name);
            row.createCell(1).setCellValue(startTime);
            row.createCell(2).setCellValue(endTime);
            row.createCell(3).setCellValue(duration);
        }

        // Auto-size columns for better readability
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write the Excel file to disk
        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
            System.out.println("📄 Excel file written: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void saveJson(String jsonData, String fileName) {
        try (FileWriter writer = new FileWriter(fileName)) {
            writer.write(jsonData);
            System.out.println("📁 JSON/HAR file written: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        new CapturingHAR().CaptHAR();
    }
}
