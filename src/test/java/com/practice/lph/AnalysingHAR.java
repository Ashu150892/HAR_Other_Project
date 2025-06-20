package com.practice.lph;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class AnalysingHAR {

    private static final List<String> START_URLS = List.of(
            "bpsso.lenovo.com/webauthn"
    );

    private static final List<String> END_URLS = List.of(
            "/signalr/start"
    );

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
            .withZone(ZoneId.of("UTC"));

    public static void main(String[] args) {
        String inputFilePath = "PerformanceData.json";
        String outputFilePath = "SeparatedURLTimings.xlsx";

        try (Reader reader = new FileReader(inputFilePath)) {
            Gson gson = new Gson();

            // Read JSON data
            List<Map<String, Object>> data = gson.fromJson(reader, new TypeToken<List<Map<String, Object>>>() {}.getType());

            System.out.println("✅ JSON data read successfully!");

            // Store timing information for start and end URLs
            Map<String, Instant> startTimes = new HashMap<>();
            Map<String, Instant> endTimes = new HashMap<>();
            List<Map<String, String>> extractedTimings = new ArrayList<>();

            // Process Start URLs
            for (String startUrl : START_URLS) {
                boolean found = false;
                for (Map<String, Object> entry : data) {
                    if (entry.get("name").toString().contains(startUrl)) {
                        found = true;
                        long millis = ((Number) entry.get("startTime")).longValue();
                        Instant instant = Instant.ofEpochMilli(millis);
                        startTimes.put(startUrl, instant);
                        System.out.println("✅ Start URL found: " + startUrl + " at " + formatter.format(instant));
                        break;
                    }
                }
                if (!found) {
                    System.out.println("⚠️ Start URL not found: " + startUrl);
                }
            }

            // Process End URLs and calculate durations
            for (String endUrl : END_URLS) {
                boolean found = false;
                for (Map<String, Object> entry : data) {
                    if (entry.get("name").toString().contains(endUrl)) {
                        found = true;
                        long millis = ((Number) entry.get("startTime")).longValue();
                        Instant endInstant = Instant.ofEpochMilli(millis);
                        endTimes.put(endUrl, endInstant);
                        System.out.println("✅ End URL found: " + endUrl + " at " + formatter.format(endInstant));

                        // Now find matching start URL and calculate the duration
                        for (String startUrl : START_URLS) {
                            if (startTimes.containsKey(startUrl)) {
                                Instant startInstant = startTimes.get(startUrl);
                                double duration = (endInstant.toEpochMilli() - startInstant.toEpochMilli()) / 1000.0;

                                // Add to extracted timings
                                Map<String, String> row = new HashMap<>();
                                row.put("Start URL", startUrl);
                                row.put("End URL", endUrl);
                                row.put("Start Time", formatter.format(startInstant));
                                row.put("End Time", formatter.format(endInstant));
                                row.put("Duration (s)", String.format("%.3f", duration));
                                row.put("Status", "✅ Matched");
                                extractedTimings.add(row);

                                // Debugging information
                                System.out.println("✅ " + startUrl + " → " + endUrl);
                                System.out.println("Start URL : " + startUrl);
                                System.out.println("End URL   : " + endUrl);
                                System.out.println("Start Time: " + formatter.format(startInstant));
                                System.out.println("End Time  : " + formatter.format(endInstant));
                                System.out.println("Duration  : " + String.format("%.3f", duration) + " seconds");
                            }
                        }
                        break;
                    }
                }
                if (!found) {
                    System.out.println("⚠️ End URL not found: " + endUrl);
                }
            }

            // Check if extractedTimings has data before writing
            if (extractedTimings.isEmpty()) {
                System.out.println("⚠️ No data found to write to Excel.");
            } else {
                // Write results to Excel
                writeToExcel(extractedTimings, outputFilePath);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void writeToExcel(List<Map<String, String>> rows, String fileName) {
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("URL Timings");

        // Create the header row
        String[] headers = {"Start URL", "End URL", "Start Time", "End Time", "Duration (s)", "Status"};
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }

        // Write each row of data
        int rowNum = 1;
        for (Map<String, String> rowData : rows) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(rowData.get("Start URL"));
            row.createCell(1).setCellValue(rowData.get("End URL"));
            row.createCell(2).setCellValue(rowData.get("Start Time"));
            row.createCell(3).setCellValue(rowData.get("End Time"));
            row.createCell(4).setCellValue(rowData.get("Duration (s)"));
            row.createCell(5).setCellValue(rowData.get("Status"));
        }

        // Auto size columns for better readability
        for (int i = 0; i < headers.length; i++) {
            sheet.autoSizeColumn(i);
        }

        // Write the Excel file to disk
        try (FileOutputStream fileOut = new FileOutputStream(fileName)) {
            workbook.write(fileOut);
            System.out.println("✅ Excel saved: " + fileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
