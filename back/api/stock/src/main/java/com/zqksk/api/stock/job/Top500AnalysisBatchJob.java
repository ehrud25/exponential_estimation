package com.zqksk.api.stock.job;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zqksk.api.stock.client.GeminiClient;
import com.zqksk.api.stock.client.KisApiClient;
import com.zqksk.api.stock.config.GeminiProperties;
import com.zqksk.api.stock.config.KisProperties;
import com.zqksk.api.stock.dto.item.Top500DomesticAnalysisItem;
import com.zqksk.api.stock.dto.kis.KisDailyItem;
import com.zqksk.api.stock.dto.kis.KisPriceOutput;
import com.zqksk.api.stock.storage.Top100AnalysisStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class Top500AnalysisBatchJob {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DAILY_DAYS = 30;
    private static final int CHUNK_SIZE = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KisProperties kisProperties;
    private final GeminiProperties geminiProperties;
    private final KisApiClient kisApiClient;
    private final GeminiClient geminiClient;
    private final Top100AnalysisStore store;

    @Scheduled(cron = "${stock.gemini.top500.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void run() {
        if (!kisProperties.isConfigured()) {
            log.warn("탑500 배치를 건너뜁니다. KIS API 설정이 없습니다.");
            return;
        }
        if (geminiProperties.isConfigured()) {
            log.warn("탑500 배치를 건너뜁니다. Gemini API 설정이 없습니다.");
            return;
        }

        List<String> codes = store.loadTop500Codes();
        if (codes.isEmpty()) {
            codes = getDefaultTop100Codes();
        }
        if (codes.isEmpty()) {
            log.warn("탑500 배치를 건너뜁니다. 종목 코드가 없습니다.");
            return;
        }

        String endDate = LocalDate.now().format(YYYYMMDD);
        String startDate = LocalDate.now().minusDays(DAILY_DAYS).format(YYYYMMDD);
        List<StockSummary> summaries = new ArrayList<>();

        for (String code : codes.subList(0, Math.min(500, codes.size()))) {
            try {
                KisPriceOutput price = kisApiClient.inquirePrice(code);
                List<KisDailyItem> daily = kisApiClient.inquireDailyChart(code, startDate, endDate);
                String name = price != null && price.prdt_name() != null && !price.prdt_name().isBlank() ? price.prdt_name().trim() : null;
                summaries.add(new StockSummary(code, name, formatStockSummary(code, name, price, daily)));
                TimeUnit.MILLISECONDS.sleep(1000);
            } catch (Exception e) {
                log.warn("탑500 종목 조회 실패: code={}, error={}", code, e.getMessage());
            }
        }

        List<Top500DomesticAnalysisItem> results = new ArrayList<>();
        for (int from = 0; from < summaries.size(); from += CHUNK_SIZE) {
            int to = Math.min(from + CHUNK_SIZE, summaries.size());
            List<StockSummary> chunk = summaries.subList(from, to);
            try {
                results.addAll(parseDomesticAnalysisJson(geminiClient.generateContent(buildChunkPrompt(chunk)), chunk));
                if (to < summaries.size()) {
                    TimeUnit.SECONDS.sleep(2);
                }
            } catch (Exception e) {
                log.error("탑500 Gemini 청크 실패: {}~{}, error={}", from, to, e.getMessage());
            }
        }

        if (!results.isEmpty()) {
            store.saveTop500DomesticAnalysis(results);
            log.info("탑500 국내 배치 완료: {}건", results.size());
        }
    }

    private String buildChunkPrompt(List<StockSummary> chunk) {
        StringBuilder data = new StringBuilder();
        for (StockSummary summary : chunk) {
            data.append(summary.summary()).append("\n\n");
        }
        return "다음은 국내 대형 종목 시세 요약입니다.\n"
            + "종목마다 3문장 분석을 만들고 JSON 배열만 반환하세요.\n"
            + "[{\"code\":\"005930\",\"name\":\"삼성전자\",\"analysis\":\"...\"}]\n\n"
            + data;
    }

    private List<Top500DomesticAnalysisItem> parseDomesticAnalysisJson(String raw, List<StockSummary> fallbackChunk) {
        List<Top500DomesticAnalysisItem> list = new ArrayList<>();
        String json = raw == null ? "" : raw.trim();
        if (json.isEmpty()) {
            return list;
        }
        int start = json.indexOf('[');
        int end = json.lastIndexOf(']');
        if (start >= 0 && end > start) {
            json = json.substring(start, end + 1);
        }
        try {
            JsonNode arr = MAPPER.readTree(json);
            if (!arr.isArray()) {
                return list;
            }
            for (JsonNode node : arr) {
                String code = node.path("code").asText(null);
                String name = node.path("name").asText(null);
                String analysis = node.path("analysis").asText(null);
                if (code != null && code.matches("\\d{6}") && analysis != null && !analysis.isBlank()) {
                    list.add(new Top500DomesticAnalysisItem(code, name, analysis.trim()));
                }
            }
        } catch (Exception e) {
            log.warn("탑500 Gemini JSON 파싱 실패, fallback 사용: {}", e.getMessage());
            for (StockSummary summary : fallbackChunk) {
                list.add(new Top500DomesticAnalysisItem(summary.code(), summary.name(), "(분석 생성 실패)"));
            }
        }
        return list;
    }

    private static String formatStockSummary(String code, String name, KisPriceOutput price, List<KisDailyItem> daily) {
        double current = parseDouble(price != null ? price.stck_prpr() : null);
        double changePct = parseDouble(price != null ? price.prdy_ctrt() : null);
        String per = price != null ? price.per() : null;
        int candles = daily != null ? daily.size() : 0;
        return String.format(Locale.US, "[%s] %s | 현재가 %.0f원, 전일 대비 %.2f%%, PER %s, 일봉 %d개", code, name != null ? name : "", current, changePct, per != null && !per.isBlank() ? per : "-", candles);
    }

    private static double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private List<String> getDefaultTop100Codes() {
        return List.of(
            "005930", "000660", "035420", "051910", "006400",
            "005380", "035720", "000270", "068270", "207940",
            "005490", "012330", "066570", "003550", "017670",
            "051900", "000810", "009150", "032830", "033780",
            "247540", "034730", "009540", "034020", "000720",
            "028260", "161390", "018880", "086520", "011200",
            "096770", "003670", "009830", "251270", "011070",
            "066970", "000100", "024110", "316140", "009420",
            "034220", "008930", "004020", "003490", "017800",
            "011170", "009410", "004170", "000850", "009290",
            "009200", "011780", "008350", "001800", "004990",
            "003540", "001450", "008260", "001040", "001230",
            "008730", "001460", "007310", "002790", "001380",
            "001120", "001740", "002380", "004840", "005250",
            "005680", "005720", "005740", "005830", "005940",
            "006050", "006120", "006260", "006360", "006650",
            "006800", "006840", "007070", "007210", "007460",
            "007540", "007810", "008060", "105560", "373220"
        );
    }

    private record StockSummary(String code, String name, String summary) {
    }
}
