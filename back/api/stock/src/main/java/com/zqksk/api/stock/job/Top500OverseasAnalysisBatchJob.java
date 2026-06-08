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
import com.zqksk.api.stock.client.KisOverseasApiClient;
import com.zqksk.api.stock.config.GeminiProperties;
import com.zqksk.api.stock.config.KisProperties;
import com.zqksk.api.stock.dto.item.Top500OverseasAnalysisItem;
import com.zqksk.api.stock.dto.kis.KisDailyItem;
import com.zqksk.api.stock.dto.kis.KisPriceOutput;
import com.zqksk.api.stock.storage.Top100AnalysisStore;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class Top500OverseasAnalysisBatchJob {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int CHUNK_SIZE = 100;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KisProperties kisProperties;
    private final GeminiProperties geminiProperties;
    private final KisOverseasApiClient kisOverseasApiClient;
    private final GeminiClient geminiClient;
    private final Top100AnalysisStore store;

    @Scheduled(cron = "${stock.gemini.top500.overseas.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void run() {
        if (!kisProperties.isConfigured()) {
            log.warn("탑500 해외 배치를 건너뜁니다. KIS API 설정이 없습니다.");
            return;
        }
        if (geminiProperties.isConfigured()) {
            log.warn("탑500 해외 배치를 건너뜁니다. Gemini API 설정이 없습니다.");
            return;
        }

        List<String> codeLines = store.loadTop500OverseasCodes();
        if (codeLines.isEmpty()) {
            codeLines = getDefaultTop100OverseasCodes();
        }

        String endDate = LocalDate.now().format(YYYYMMDD);
        List<OverseasSummary> summaries = new ArrayList<>();
        for (String line : codeLines.subList(0, Math.min(500, codeLines.size()))) {
            String[] parts = line.split(",", 2);
            if (parts.length != 2) {
                continue;
            }
            String excd = parts[0].trim();
            String symbol = parts[1].trim();
            try {
                KisPriceOutput price = kisOverseasApiClient.inquireOverseasPrice(excd, symbol);
                List<KisDailyItem> daily = kisOverseasApiClient.inquireOverseasDailyChart(excd, symbol, endDate);
                summaries.add(new OverseasSummary(excd, symbol, formatOverseasSummary(excd, symbol, price, daily)));
                TimeUnit.MILLISECONDS.sleep(1000);
            } catch (Exception e) {
                log.warn("탑500 해외 종목 조회 실패: {} {}, error={}", excd, symbol, e.getMessage());
            }
        }

        List<Top500OverseasAnalysisItem> results = new ArrayList<>();
        for (int from = 0; from < summaries.size(); from += CHUNK_SIZE) {
            int to = Math.min(from + CHUNK_SIZE, summaries.size());
            List<OverseasSummary> chunk = summaries.subList(from, to);
            try {
                results.addAll(parseOverseasAnalysisJson(geminiClient.generateContent(buildChunkPrompt(chunk)), chunk));
                if (to < summaries.size()) {
                    TimeUnit.SECONDS.sleep(2);
                }
            } catch (Exception e) {
                log.error("탑500 해외 Gemini 청크 실패: {}~{}, error={}", from, to, e.getMessage());
            }
        }

        if (!results.isEmpty()) {
            store.saveTop500OverseasAnalysis(results);
            log.info("탑500 해외 배치 완료: {}건", results.size());
        }
    }

    private String buildChunkPrompt(List<OverseasSummary> chunk) {
        StringBuilder data = new StringBuilder();
        for (OverseasSummary summary : chunk) {
            data.append(summary.summary()).append("\n\n");
        }
        return "다음은 해외 대형 종목 시세 요약입니다.\n"
            + "종목마다 3문장 분석을 만들고 JSON 배열만 반환하세요.\n"
            + "[{\"excd\":\"NAS\",\"symbol\":\"AAPL\",\"analysis\":\"...\"}]\n\n"
            + data;
    }

    private List<Top500OverseasAnalysisItem> parseOverseasAnalysisJson(String raw, List<OverseasSummary> fallbackChunk) {
        List<Top500OverseasAnalysisItem> list = new ArrayList<>();
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
                String excd = node.path("excd").asText(null);
                String symbol = node.path("symbol").asText(null);
                String analysis = node.path("analysis").asText(null);
                if (excd != null && symbol != null && !excd.isBlank() && !symbol.isBlank() && analysis != null && !analysis.isBlank()) {
                    list.add(new Top500OverseasAnalysisItem(excd.trim(), symbol.trim(), analysis.trim()));
                }
            }
        } catch (Exception e) {
            log.warn("탑500 해외 Gemini JSON 파싱 실패, fallback 사용: {}", e.getMessage());
            for (OverseasSummary summary : fallbackChunk) {
                list.add(new Top500OverseasAnalysisItem(summary.excd(), summary.symbol(), "(분석 생성 실패)"));
            }
        }
        return list;
    }

    private static String formatOverseasSummary(String excd, String symbol, KisPriceOutput price, List<KisDailyItem> daily) {
        double current = parseDouble(price != null ? price.stck_prpr() : null);
        double changePct = parseDouble(price != null ? price.prdy_ctrt() : null);
        int candles = daily != null ? daily.size() : 0;
        return String.format(Locale.US, "[%s %s] 현재가 $%.2f, 전일 대비 %.2f%%, 일봉 %d개", excd, symbol, current, changePct, candles);
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

    private static List<String> getDefaultTop100OverseasCodes() {
        return List.of(
            "NAS,AAPL", "NAS,MSFT", "NAS,GOOGL", "NAS,AMZN", "NAS,NVDA",
            "NAS,META", "NAS,TSLA", "NAS,AVGO", "NAS,COST", "NAS,PEP",
            "NAS,NFLX", "NAS,ADBE", "NAS,CSCO", "NAS,AMD", "NAS,INTC",
            "NAS,QCOM", "NAS,TXN", "NAS,INTU", "NAS,AMAT", "NAS,SBUX",
            "NYS,BRK.B", "NYS,JPM", "NYS,V", "NYS,MA", "NYS,UNH",
            "NYS,JNJ", "NYS,PG", "NYS,HD", "NYS,DIS", "NYS,XOM",
            "NYS,CVX", "NYS,KO", "NYS,WMT", "NYS,MCD", "NYS,ABBV",
            "NYS,MRK", "NYS,PFE", "NYS,BAC", "NYS,WFC", "NYS,GS",
            "NYS,MS", "NYS,AXP", "NYS,CRM", "NYS,VZ", "NYS,T",
            "NYS,BMY", "NYS,PM", "NYS,UNP", "NYS,LMT", "NYS,HON",
            "NYS,RTX", "NYS,UPS", "NYS,LOW", "NYS,IBM", "NYS,GE",
            "NYS,AMGN", "NYS,SPGI", "NYS,DE", "NYS,CAT", "NYS,ORCL",
            "NYS,GILD", "NYS,MDT", "NYS,SCHW", "NYS,BLK", "NYS,ADI",
            "NYS,C", "NYS,MMC", "NYS,REGN", "NYS,LRCX", "NYS,PLD",
            "NYS,CI", "NYS,SO", "NYS,DUK", "NYS,BDX", "NYS,EOG",
            "NYS,MO", "NYS,SLB", "NYS,FIS", "NYS,CMCSA", "NYS,ZTS",
            "NYS,APD", "NYS,USB", "NYS,PGR", "NYS,KLAC", "NYS,TGT",
            "NYS,MCO", "NYS,CB", "NYS,SYK", "NYS,DHR", "NYS,NEE",
            "NYS,BSX", "NYS,WM", "NYS,ICE", "NYS,EQIX", "NYS,SHW"
        );
    }

    private record OverseasSummary(String excd, String symbol, String summary) {
    }
}
