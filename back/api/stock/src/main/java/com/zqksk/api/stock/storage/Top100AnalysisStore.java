package com.zqksk.api.stock.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zqksk.api.stock.dto.item.Top100NameCodeItem;
import com.zqksk.api.stock.dto.item.Top100OverseasNameCodeItem;
import com.zqksk.api.stock.dto.item.Top500DomesticAnalysisItem;
import com.zqksk.api.stock.dto.item.Top500OverseasAnalysisItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

@Slf4j
@Component
public class Top100AnalysisStore {

    private static final String FILENAME = "top100-analysis.json";
    private static final String CODES_FILENAME = "top100-codes.txt";
    private static final String OVERSEAS_FILENAME = "top100-overseas-analysis.json";
    private static final String OVERSEAS_CODES_FILENAME = "top100-overseas-codes.txt";
    private static final String OVERSEAS_NAME_CODE_FILENAME = "top100-overseas-name-code.json";
    private static final String NAME_CODE_FILENAME = "top100-name-code.json";
    private static final String TOP500_DOMESTIC_ANALYSIS_FILENAME = "top500-domestic-analysis.json";
    private static final String TOP500_OVERSEAS_ANALYSIS_FILENAME = "top500-overseas-analysis.json";
    private static final String TOP500_CODES_FILENAME = "top500-codes.txt";
    private static final String TOP500_OVERSEAS_CODES_FILENAME = "top500-overseas-codes.txt";
    private static final int TOP500_LIMIT = 500;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Path dataDir;

    public Top100AnalysisStore(@Value("${stock.gemini.data-dir:./data}") String dataDir) {
        this.dataDir = Path.of(dataDir).toAbsolutePath();
    }

    public Path getDataDir() {
        return dataDir;
    }

    public Path getTop100AnalysisPath() {
        return dataDir.resolve(FILENAME);
    }

    public void save(String analysisText) {
        writeAnalysisFile(getTop100AnalysisPath(), analysisText, "탑100 분석");
    }

    public Optional<String> load() {
        return readAnalysisFile(getTop100AnalysisPath(), "탑100 분석");
    }

    public Path getTop100NameCodePath() {
        return dataDir.resolve(NAME_CODE_FILENAME);
    }

    public void saveTop100NameCodeList(List<Top100NameCodeItem> items) {
        writeItemsFile(getTop100NameCodePath(), items, "탑100 종목명-코드");
    }

    public List<Top100NameCodeItem> loadTop100NameCodeList() {
        Path path = getTop100NameCodePath();
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            JsonNode items = MAPPER.readTree(Files.readString(path)).path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<Top100NameCodeItem> list = new ArrayList<>();
            for (JsonNode item : items) {
                String name = item.path("name").asText(null);
                String code = item.path("code").asText(null);
                if (code != null && code.matches("\\d{6}")) {
                    list.add(new Top100NameCodeItem(name != null ? name.trim() : null, code.trim()));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (IOException e) {
            log.warn("탑100 종목명-코드 파일 읽기 실패: {}", e.getMessage());
            return List.of();
        }
    }

    public List<String> loadTop100Codes() {
        return loadCodeFile(dataDir.resolve(CODES_FILENAME), 100, true);
    }

    public Path getTop100OverseasAnalysisPath() {
        return dataDir.resolve(OVERSEAS_FILENAME);
    }

    public void saveOverseas(String analysisText) {
        writeAnalysisFile(getTop100OverseasAnalysisPath(), analysisText, "탑100 해외 분석");
    }

    public Optional<String> loadOverseas() {
        return readAnalysisFile(getTop100OverseasAnalysisPath(), "탑100 해외 분석");
    }

    public Path getTop100OverseasNameCodePath() {
        return dataDir.resolve(OVERSEAS_NAME_CODE_FILENAME);
    }

    public void saveTop100OverseasNameCodeList(List<Top100OverseasNameCodeItem> items) {
        writeItemsFile(getTop100OverseasNameCodePath(), items, "탑100 해외 종목 목록");
    }

    public List<Top100OverseasNameCodeItem> loadTop100OverseasNameCodeList() {
        Path path = getTop100OverseasNameCodePath();
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            JsonNode items = MAPPER.readTree(Files.readString(path)).path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<Top100OverseasNameCodeItem> list = new ArrayList<>();
            for (JsonNode item : items) {
                String excd = item.path("excd").asText(null);
                String symbol = item.path("symbol").asText(null);
                if (excd != null && symbol != null && !excd.isBlank() && !symbol.isBlank()) {
                    list.add(new Top100OverseasNameCodeItem(excd.trim(), symbol.trim()));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (IOException e) {
            log.warn("탑100 해외 종목 목록 파일 읽기 실패: {}", e.getMessage());
            return List.of();
        }
    }

    public List<String> loadTop100OverseasCodes() {
        return loadCodeFile(dataDir.resolve(OVERSEAS_CODES_FILENAME), 100, false);
    }

    public List<String> loadTop500Codes() {
        Path path500 = dataDir.resolve(TOP500_CODES_FILENAME);
        Path path = Files.exists(path500) ? path500 : dataDir.resolve(CODES_FILENAME);
        return loadCodeFile(path, TOP500_LIMIT, true);
    }

    public List<String> loadTop500OverseasCodes() {
        Path path500 = dataDir.resolve(TOP500_OVERSEAS_CODES_FILENAME);
        Path path = Files.exists(path500) ? path500 : dataDir.resolve(OVERSEAS_CODES_FILENAME);
        return loadCodeFile(path, TOP500_LIMIT, false);
    }

    public Path getTop500DomesticAnalysisPath() {
        return dataDir.resolve(TOP500_DOMESTIC_ANALYSIS_FILENAME);
    }

    public void saveTop500DomesticAnalysis(List<Top500DomesticAnalysisItem> items) {
        writeItemsFile(getTop500DomesticAnalysisPath(), items, "탑500 국내 분석");
    }

    public List<Top500DomesticAnalysisItem> loadTop500DomesticAnalysisItems() {
        Path path = getTop500DomesticAnalysisPath();
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            JsonNode items = MAPPER.readTree(Files.readString(path)).path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<Top500DomesticAnalysisItem> list = new ArrayList<>();
            for (JsonNode item : items) {
                String code = item.path("code").asText(null);
                String name = item.path("name").asText(null);
                String analysis = item.path("analysis").asText(null);
                if (code != null && code.matches("\\d{6}")) {
                    list.add(new Top500DomesticAnalysisItem(code.trim(), name, analysis));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (IOException e) {
            log.warn("탑500 국내 분석 파일 읽기 실패: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, String> loadTop500DomesticAnalysisMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Top500DomesticAnalysisItem item : loadTop500DomesticAnalysisItems()) {
            if (item.code() != null && item.analysis() != null) {
                map.put(item.code(), item.analysis());
            }
        }
        return map;
    }

    public Path getTop500OverseasAnalysisPath() {
        return dataDir.resolve(TOP500_OVERSEAS_ANALYSIS_FILENAME);
    }

    public void saveTop500OverseasAnalysis(List<Top500OverseasAnalysisItem> items) {
        writeItemsFile(getTop500OverseasAnalysisPath(), items, "탑500 해외 분석");
    }

    public List<Top500OverseasAnalysisItem> loadTop500OverseasAnalysisItems() {
        Path path = getTop500OverseasAnalysisPath();
        if (!Files.exists(path)) {
            return List.of();
        }
        try {
            JsonNode items = MAPPER.readTree(Files.readString(path)).path("items");
            if (!items.isArray()) {
                return List.of();
            }
            List<Top500OverseasAnalysisItem> list = new ArrayList<>();
            for (JsonNode item : items) {
                String excd = item.path("excd").asText(null);
                String symbol = item.path("symbol").asText(null);
                String analysis = item.path("analysis").asText(null);
                if (excd != null && symbol != null && !excd.isBlank() && !symbol.isBlank()) {
                    list.add(new Top500OverseasAnalysisItem(excd.trim(), symbol.trim(), analysis));
                }
            }
            return Collections.unmodifiableList(list);
        } catch (IOException e) {
            log.warn("탑500 해외 분석 파일 읽기 실패: {}", e.getMessage());
            return List.of();
        }
    }

    public Map<String, String> loadTop500OverseasAnalysisMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (Top500OverseasAnalysisItem item : loadTop500OverseasAnalysisItems()) {
            if (item.excd() != null && item.symbol() != null && item.analysis() != null) {
                map.put(item.excd() + "," + item.symbol(), item.analysis());
            }
        }
        return map;
    }

    private void writeAnalysisFile(Path path, String analysisText, String label) {
        try {
            Files.createDirectories(dataDir);
            ObjectNode root = MAPPER.createObjectNode();
            root.put("generatedAt", Instant.now().toString());
            root.put("analysisText", analysisText != null ? analysisText : "");
            Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (IOException e) {
            log.error("{} 저장 실패: {}", label, e.getMessage());
            throw new RuntimeException(label + " 저장 실패", e);
        }
    }

    private Optional<String> readAnalysisFile(Path path, String label) {
        if (!Files.exists(path)) {
            return Optional.empty();
        }
        try {
            String text = MAPPER.readTree(Files.readString(path)).path("analysisText").asText("");
            return Optional.ofNullable(text.isBlank() ? null : text);
        } catch (IOException e) {
            log.warn("{} 파일 읽기 실패: {}", label, e.getMessage());
            return Optional.empty();
        }
    }

    private void writeItemsFile(Path path, List<?> items, String label) {
        try {
            Files.createDirectories(dataDir);
            ObjectNode root = MAPPER.createObjectNode();
            root.put("generatedAt", Instant.now().toString());
            root.set("items", MAPPER.valueToTree(items != null ? items : List.of()));
            Files.writeString(path, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root));
        } catch (IOException e) {
            log.error("{} 저장 실패: {}", label, e.getMessage());
            throw new RuntimeException(label + " 저장 실패", e);
        }
    }

    private List<String> loadCodeFile(Path path, int limit, boolean domestic) {
        if (!Files.exists(path)) {
            return List.of();
        }
        try (Stream<String> lines = Files.lines(path)) {
            List<String> codes = new ArrayList<>();
            lines.map(String::trim)
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .filter(line -> domestic ? line.matches("\\d{6}") : line.contains(","))
                .limit(limit)
                .forEach(codes::add);
            return codes;
        } catch (IOException e) {
            log.warn("{} 파일 읽기 실패: {}", path.getFileName(), e.getMessage());
            return List.of();
        }
    }
}
