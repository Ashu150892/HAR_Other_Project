package com.practice.lph;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;

public class FullCodeVersion {

    public void CaptHAR() {
        WebDriverManager.chromedriver().setup();

        LoggingPreferences logPrefs = new LoggingPreferences();
        logPrefs.enable(LogType.PERFORMANCE, Level.ALL);

        ChromeOptions options = new ChromeOptions();
        options.setCapability("goog:loggingPrefs", logPrefs);
        options.addArguments("--disable-gpu");

        WebDriver driver = new ChromeDriver(options);
        driver.manage().window().maximize();

        try {
            driver.get("https://www.lenovopartnerhub.com/");

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(20));
            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//a[normalize-space()='Login']"))).click();

            try {
                driver.findElement(By.xpath("//span[@class='nametText'][normalize-space()='lph_perft@lenovo.com']")).click();
            } catch (Exception e) {
                driver.findElement(By.xpath("//input[@placeholder='Email address']")).sendKeys("lph_perft@lenovo.com");
                driver.findElement(By.xpath("//a[@class='nextTop10 next']")).click();
            }

            wait.until(ExpectedConditions.elementToBeClickable(By.xpath("//div[@id='loginClass7']//a[@class='next'][normalize-space()='Next']"))).click();
            driver.findElement(By.xpath("//input[@placeholder='Password']")).sendKeys("LPHtest@123");
            driver.findElement(By.xpath("//form[@action='/webauthn/userLogin']//a[@class='next'][normalize-space()='Next']")).click();

            Thread.sleep(35000); // Wait for full load

            JavascriptExecutor js = (JavascriptExecutor) driver;
            wait.until(wd -> js.executeScript("return document.readyState").equals("complete"));
            new WebDriverWait(driver, Duration.ofSeconds(20)).until(
                    d -> ((Number) js.executeScript("return performance.timing.loadEventEnd")).longValue() > 0
            );

            String resourceJson = (String) js.executeScript("return JSON.stringify(performance.getEntriesByType('resource'));");
            String xhrJson = (String) js.executeScript("return JSON.stringify(performance.getEntriesByType('xmlhttprequest'));");
            String timingJson = (String) js.executeScript("return JSON.stringify(performance.timing);");

            Gson gson = new Gson();
            List<Map<String, Object>> resources = gson.fromJson(resourceJson, new TypeToken<List<Map<String, Object>>>() {}.getType());
            List<Map<String, Object>> xhrResources = gson.fromJson(xhrJson, new TypeToken<List<Map<String, Object>>>() {}.getType());
            Map<String, Double> timing = gson.fromJson(timingJson, new TypeToken<Map<String, Double>>() {}.getType());

            resources.addAll(xhrResources);

            // Optional: Save all URLs to file for debugging
            try (BufferedWriter bw = new BufferedWriter(new FileWriter("all_urls.txt"))) {
                for (Map<String, Object> entry : resources) {
                    bw.write(entry.get("name").toString());
                    bw.newLine();
                }
            }

            resources.sort((a, b) -> Double.compare(
                    ((Number) b.get("duration")).doubleValue(),
                    ((Number) a.get("duration")).doubleValue()
            ));

            writeToExcel(resources, "Response_Sheet.xlsx");
            writeProcessedMetrics(resources, timing, "Processed_Performance.xlsx");

            System.out.println("✅ Total entries written: " + resources.size());

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
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

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

        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
            System.out.println("📄 Excel file written: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void writeProcessedMetrics(List<Map<String, Object>> data, Map<String, Double> timing, String fileName) {
        List<List<String>> resultRows = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.of("UTC"));

        List<Triple> urlPairs = List.of(
                new Triple("https://eu4-live.inside-graph.com", "signalr/start"),
                new Triple("lenovopartnerhub.com", "/api/navigation")
        );

        for (Triple pair : urlPairs) {
            Optional<Map<String, Object>> startEntry = data.stream()
                    .filter(d -> d.get("name").toString().contains(pair.startUrl)).findFirst();
            Optional<Map<String, Object>> endEntry = data.stream()
                    .filter(d -> d.get("name").toString().contains(pair.endUrl)).findFirst();

            if (startEntry.isPresent() && endEntry.isPresent()) {
                long startMillis = ((Number) startEntry.get().get("startTime")).longValue();
                long endMillis = ((Number) endEntry.get().get("startTime")).longValue();

                Instant startInstant = Instant.ofEpochMilli(startMillis);
                Instant endInstant = Instant.ofEpochMilli(endMillis);
                double duration = (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / 1000.0;

                resultRows.add(List.of(
                        pair.startUrl + " → " + pair.endUrl,
                        "Network Load",
                        formatter.format(startInstant),
                        formatter.format(endInstant),
                        String.format("%.3f", duration)
                ));
            } else {
                System.out.println("❌ No match for: " + pair.startUrl + " → " + pair.endUrl);
            }
        }

    

        // Write to Excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Processed Metrics");

        String[] headers = {"Page Name", "Metric Value", "Start Time", "End Time", "Duration (sec)"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        int rowNum = 1;
        for (List<String> rowData : resultRows) {
            Row row = sheet.createRow(rowNum++);
            for (int i = 0; i < rowData.size(); i++) {
                row.createCell(i).setCellValue(rowData.get(i));
            }
        }

        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        try (FileOutputStream out = new FileOutputStream(fileName)) {
            workbook.write(out);
            System.out.println("📊 Processed metrics written: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    static class Triple {
        String startUrl;
        String endUrl;

        Triple(String startUrl, String endUrl) {
            this.startUrl = startUrl;
            this.endUrl = endUrl;
        }
    }

    public static void main(String[] args) {
        new FullCodeVersion().CaptHAR();
    }
}
