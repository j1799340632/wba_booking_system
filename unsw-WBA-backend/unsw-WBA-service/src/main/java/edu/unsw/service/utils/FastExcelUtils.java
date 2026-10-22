package edu.unsw.service.utils;

import edu.unsw.core.config.exception.BusinessException;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.enums.ResponseEnum;
import edu.unsw.core.utils.CurrentUserUtils;
import jakarta.servlet.http.HttpServletResponse;
import org.dhatim.fastexcel.Workbook;
import org.dhatim.fastexcel.Worksheet;
import org.dhatim.fastexcel.reader.Cell;
import org.dhatim.fastexcel.reader.ReadableWorkbook;
import org.dhatim.fastexcel.reader.Row;
import org.dhatim.fastexcel.reader.Sheet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 17/7/2026 22:02
 * @description Fast-excel utils
 */
public class FastExcelUtils {
    private FastExcelUtils() {

    }

    private static final Logger logger = LoggerFactory.getLogger(FastExcelUtils.class);

    private static final String TIME_FORMATE_PATTERN = "yyyy-MM-dd HH:mm:ss";

    private static final String APPLICATION_NAME = "UNSW_WBA";

    private static final String APPLICATION_VERSION = "1.0";

    private static void flushDataToResponse(HttpServletResponse response, String fileName, Consumer<OutputStream> consumer) {
        CurrentUser currentUser = CurrentUserUtils.getUser();
        if (Objects.nonNull(currentUser)) {
            fileName = currentUser.getId() + "_" + fileName;
        }
        try {
            String concatFileName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setCharacterEncoding("utf-8");
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=" + concatFileName);

            consumer.accept(response.getOutputStream());

            response.getOutputStream().flush();
        } catch (IOException e) {
            logger.error("Failed to export Excel file", e);
            throw new BusinessException(ResponseEnum.DOWNLOAD_FILE_FAILED);
        }
    }

    public static void exportExcel(List<String> columnTitleList, List<List<Object>> columnValueList, String fileName, HttpServletResponse response) {
        flushDataToResponse(response, fileName, outputStream -> writeDataToStream(outputStream, columnTitleList, columnValueList));
    }

    /**
     * Generate user-imported templates (directly output to the OutputStream, commonly used for Response)
     *
     * @param response Output stream
     */
    public static void generateUserImportTemplate(HttpServletResponse response, List<String> roleOptions) {
        try (
                Workbook workbook = new Workbook(response.getOutputStream(), APPLICATION_NAME, APPLICATION_VERSION);
                Worksheet ws = workbook.newWorksheet("User Import Template");
        ) {
            // Header
            List<String> headers = List.of("Name", "Email", "Password", "RoleKey");

            ws.width(0, 20);
            ws.width(1, 28);
            ws.width(2, 22);
            ws.width(3, 18);

            // Add a dropdown selection box for the RoleKey column (Data Validation)
            if (Objects.isNull(roleOptions) || roleOptions.isEmpty()) {
                roleOptions = List.of("SuAdmin", "Admin", "PS", "Stu");
            }

            // Format as an Excel string formula: "Admin, PS, Stu"
            String listFormula = "\"" + String.join(",", roleOptions) + "\"";

            // Column 4 (index 3), from row 2 to row 1000 (index 1 ~ 999)
            ws.range(1, 3, 999, 3).validateWithListByFormula(listFormula);

            flushDataToResponse(response, "user_import_template.xlsx", _ -> writeDataToStream(ws, headers, List.of()));
        } catch (Exception e) {
            logger.error("Failed to generate user import template: {}", e.getMessage());
            throw new BusinessException(ResponseEnum.DOWNLOAD_FILE_FAILED);
        }
    }

    private static void writeDataToStream(Worksheet ws, List<String> columnTitleList, List<List<Object>> columnValueList) {
        try {
            // Write a column header title
            setWorksheetHeader(ws, columnTitleList);

            // "i" is Row
            for (int i = 0; i < columnValueList.size(); i++) {
                // Start with row 1
                int rowIdx = i + 1;

                List<Object> singleRowValueList = columnValueList.get(i);

                // "j" is Column
                for (int j = 0; j < singleRowValueList.size(); j++) {
                    Object value = singleRowValueList.get(j);

                    if (Objects.isNull(value)) {
                        ws.value(rowIdx, j, "");
                        continue;
                    }

                    // General data type just set directly
                    ws.value(rowIdx, j, String.valueOf(value));

                    // The code below is handling special data type
                    if (value instanceof Boolean v) {
                        ws.value(rowIdx, j, v);
                    }

                    // Converting a Long type ID to a string using String.valueOf() and writing it will not result in loss of precision
                    if (value instanceof Long) {
                        ws.value(rowIdx, j, String.valueOf(value));
                    } else if (value instanceof Number v) {
                        ws.value(rowIdx, j, v);
                    }

                    if (isDateTime(value)) {
                        ws.style(rowIdx, j).format(TIME_FORMATE_PATTERN).set();
                        switch (value) {
                            case LocalDateTime v -> ws.value(rowIdx, j, v);
                            case LocalDate v -> ws.value(rowIdx, j, v);
                            case ZonedDateTime v -> ws.value(rowIdx, j, v);
                            case Instant v -> ws.value(rowIdx, j, v);
                            case Date v -> ws.value(rowIdx, j, v);
                            default -> ws.value(rowIdx, j, String.valueOf(value));
                        }
                    }
                }
            }

            // Flush stream
            ws.finish();
        } catch (IOException e) {
            logger.error("Failed to write data to Excel file", e);
            throw new BusinessException(ResponseEnum.DOWNLOAD_FILE_FAILED);
        }
    }

    private static void writeDataToStream(OutputStream outputStream, List<String> columnTitleList, List<List<Object>> columnValueList) {
        // Create a workbook and directly bind the output stream.
        // The first parameter here is the name of the application that generated this file, and the second is the version number, which is used to write the metadata into Excel
        try (
                Workbook wb = new Workbook(outputStream, APPLICATION_NAME, APPLICATION_VERSION);
                Worksheet ws = wb.newWorksheet("Student Scores");
        ) {
            writeDataToStream(ws, columnTitleList, columnValueList);
        } catch (IOException e) {
            logger.error("Failed to write data to Excel file", e);
            throw new BusinessException(ResponseEnum.DOWNLOAD_FILE_FAILED);
        }
    }

    private static void setWorksheetHeader(Worksheet ws, List<String> columnTitleList) {
        // Column header height (row 0)
        ws.rowHeight(0, 35);
        for (int i = 0; i < columnTitleList.size(); i++) {
            ws.style(0, i)
                    // Bold family
                    .bold()
                    // Gray background
                    .fillColor("F2F2F2")
                    // Thin border
                    .borderStyle("thin")
                    // Content center
                    .horizontalAlignment("center")
                    .verticalAlignment("center")
                    .set();
            ws.value(0, i, columnTitleList.get(i));
        }
    }

    public static boolean isDateTime(Object obj) {
        if (obj == null) {
            return false;
        }

        return obj instanceof Date
                || obj instanceof TemporalAccessor
                || obj instanceof Calendar;
    }

    public static String formatAnything(Object obj) {
        if (Objects.isNull(obj)) {
            return "";
        }

        return switch (obj) {
            case TemporalAccessor temporalAccessor -> DateTimeFormatter.ofPattern(TIME_FORMATE_PATTERN)
                    .format(temporalAccessor);
            case Date date -> new SimpleDateFormat(TIME_FORMATE_PATTERN)
                    .format(date);
            case Calendar calendar -> new SimpleDateFormat(TIME_FORMATE_PATTERN)
                    .format(calendar.getTime());
            default -> obj.toString();
        };
    }

    /**
     * Basic reading: Reads a specified sheet from an Excel file into a two-dimensional string array List<List<String>>
     *
     * @param inputStream File stream
     * @param sheetIndex  Sheet index (start with 0)
     * @return A collection of text in each row and column
     */
    public static List<List<String>> readToList(InputStream inputStream, int sheetIndex) {
        List<List<String>> result = new ArrayList<>();

        try (ReadableWorkbook workbook = new ReadableWorkbook(inputStream)) {
            Sheet sheet = workbook.getSheet(sheetIndex).orElseThrow(() -> new IllegalArgumentException("Sheet index [" + sheetIndex + "] not found!"));

            // Streaming line-by-line reading
            try (Stream<Row> rows = sheet.openStream()) {
                rows.forEach(row -> {
                    List<String> rowData = new ArrayList<>();
                    // Iterate through each column of the current row
                    for (Cell cell : row) {
                        // getText() will automatically convert numbers, dates, and strings to text format
                        rowData.add(cell == null ? "" : cell.getText());
                    }
                    result.add(rowData);
                });
            }
        } catch (Exception e) {
            logger.error("Failed to parse Excel file using FastExcel", e);
            throw new RuntimeException("Read Excel file error", e);
        }

        return result;
    }

    /**
     * Overload: By default, the first Sheet page is read
     */
    public static List<List<String>> readToList(InputStream inputStream) {
        return readToList(inputStream, 0);
    }

    /**
     * Structured reading: Use the first row as the Header key and read as List<Map<Header, CellValue>>
     * Scenario: Commonly used for importing data from tables (e.g., [{"Name": "Zhang San", "Age": "20"}, {"Name": "Li Si", "Age": "22"}])
     *
     * @param inputStream File stream
     * @param sheetIndex  Sheet index
     * @return Dictionary list
     */
    public static List<Map<String, String>> readToMapList(InputStream inputStream, int sheetIndex) {
        List<List<String>> rawData = readToList(inputStream, sheetIndex);
        if (rawData.isEmpty()) {
            return new ArrayList<>();
        }

        // Retrieve row 0 as the table header
        List<String> headers = rawData.getFirst();
        List<Map<String, String>> mapList = new ArrayList<>();

        // Iterate starting from row 1 (data row)
        for (int i = 1; i < rawData.size(); i++) {
            List<String> row = rawData.get(i);
            Map<String, String> rowMap = new HashMap<>();

            for (int col = 0; col < headers.size(); col++) {
                String headerName = headers.get(col);
                String cellValue = col < row.size() ? row.get(col) : "";
                if (headerName != null && !headerName.isBlank()) {
                    rowMap.put(headerName.trim(), cellValue);
                }
            }
            mapList.add(rowMap);
        }

        return mapList;
    }

    /**
     * Overload: By default, it reads the first sheet of structured data
     */
    public static List<Map<String, String>> readToMapList(InputStream inputStream) {
        return readToMapList(inputStream, 0);
    }
}
