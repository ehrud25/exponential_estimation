package com.zqksk.api.stock.service;

import com.zqksk.api.stock.client.GeminiClient;
import com.zqksk.api.stock.config.KisProperties;
import com.zqksk.api.stock.dto.analysis.AnalysisData;
import com.zqksk.api.stock.dto.analysis.AnalysisResponse;
import com.zqksk.api.stock.storage.Top100AnalysisStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockGeminiAnalysisService {

    private final KisProperties kisProperties;
    private final GeminiClient geminiClient;
    private final Top100AnalysisStore top100AnalysisStore;
    private final StockAnalysisDataFactory analysisDataFactory;

    public AnalysisResponse analyzeWithGemini(String stockCode) {
        return analyzeWithGemini(stockCode, true);
    }

    public AnalysisResponse analyzeWithGemini(String stockCode, boolean useTop100) {
        String code = stockCode != null ? stockCode.trim() : "";
        if (code.isEmpty()) {
            return new AnalysisResponse("오류: 종목 코드가 없습니다.", null, null);
        }
        if (!code.matches("\\d{6}")) {
            return new AnalysisResponse("국내 종목 6자리 코드만 지원합니다.", null, null);
        }
        if (!kisProperties.isConfigured()) {
            return new AnalysisResponse("KIS API가 설정되지 않았습니다.", null, null);
        }

        try {
            AnalysisData data = analysisDataFactory.fetchDomestic(code);
            String prompt = buildPrompt(code, buildRealTimeSummary(data, false), useTop100);
            String conclusion = sanitizeConclusion(geminiClient.generateContent(prompt));
            if (conclusion.isBlank()) {
                return new AnalysisResponse("Gemini 분석 결과가 비어 있습니다.", null, null);
            }
            return new AnalysisResponse(firstLine(conclusion), conclusion, conclusion);
        } catch (Exception e) {
            log.warn("Gemini 분석 실패: stockCode={}, error={}", code, e.getMessage());
            return new AnalysisResponse("분석 중 오류가 발생했습니다.", "오류: " + e.getMessage(), null);
        }
    }

    public AnalysisResponse analyzeWithGeminiOverseas(String excd, String symbol, boolean useTop100) {
        if (excd == null || symbol == null || excd.isBlank() || symbol.isBlank()) {
            return new AnalysisResponse("오류: 거래소 코드 또는 심볼이 없습니다.", null, null);
        }
        if (!kisProperties.isConfigured()) {
            return new AnalysisResponse("KIS API가 설정되지 않았습니다.", null, null);
        }

        try {
            AnalysisData data = analysisDataFactory.fetchOverseas(symbol.trim(), excd.trim());
            String stockLabel = excd.trim() + " " + symbol.trim();
            String prompt = buildPrompt(stockLabel, buildRealTimeSummary(data, true), useTop100);
            String conclusion = sanitizeConclusion(geminiClient.generateContent(prompt));
            if (conclusion.isBlank()) {
                return new AnalysisResponse("Gemini 분석 결과가 비어 있습니다.", null, null);
            }
            return new AnalysisResponse(firstLine(conclusion), conclusion, conclusion);
        } catch (Exception e) {
            log.warn("Gemini 해외 분석 실패: excd={}, symbol={}, error={}", excd, symbol, e.getMessage());
            return new AnalysisResponse("분석 중 오류가 발생했습니다.", "오류: " + e.getMessage(), null);
        }
    }

    private String buildPrompt(String label, String summary, boolean useTop100) {
        StringBuilder prompt = new StringBuilder();
        if (useTop100) {
            String domesticText = top100AnalysisStore.load().orElse("(국내 top100 분석이 없습니다.)");
            String overseasText = top100AnalysisStore.loadOverseas().orElse("(해외 top100 분석이 없습니다.)");
            prompt.append("다음은 국내 top100 요약 분석입니다.\n").append(domesticText).append("\n\n");
            prompt.append("다음은 해외 top100 요약 분석입니다.\n").append(overseasText).append("\n\n");
        }

        prompt.append("아래는 사용자가 요청한 종목(").append(label).append(")의 실시간 요약입니다.\n");
        prompt.append(summary).append("\n\n");
        prompt.append("아래 3문장 형식만 지켜서 답변하세요.\n");
        prompt.append("1) 지금 {종목명}은 [상방/하방] 압력입니다.\n");
        prompt.append("2) [단기], [중기]이며 [조정 가능성/반등 가능성/관망]입니다.\n");
        prompt.append("3) 따라서 현재 매도하면 손실은 -x~-y%, 매수하면 반등 시 수익은 +a~+b% 가능합니다.");
        return prompt.toString();
    }

    private String buildRealTimeSummary(AnalysisData data, boolean overseas) {
        double current = StockAnalysisDataFactory.parseDouble(data.price().stck_prpr());
        double changePct = StockAnalysisDataFactory.parseDouble(data.price().prdy_ctrt());
        StringBuilder summary = new StringBuilder();
        summary.append(overseas
            ? String.format(Locale.US, "현재가 $%.2f, 전일 대비 %.2f%%. ", current, changePct)
            : String.format(Locale.US, "현재가 %.0f원, 전일 대비 %.2f%%. ", current, changePct));

        if (data.ma5() != null && !Double.isNaN(data.ma5())) {
            summary.append(overseas ? String.format(Locale.US, "5일선 $%.2f, ", data.ma5()) : String.format(Locale.US, "5일선 %.0f원, ", data.ma5()));
        }
        if (data.ma20() != null && !Double.isNaN(data.ma20())) {
            double gap20 = (current - data.ma20()) / data.ma20() * 100;
            summary.append(overseas
                ? String.format(Locale.US, "20일선 $%.2f(현재가 대비 %.1f%%), ", data.ma20(), gap20)
                : String.format(Locale.US, "20일선 %.0f원(현재가 대비 %.1f%%), ", data.ma20(), gap20));
        }
        if (data.ma60() != null && !Double.isNaN(data.ma60())) {
            summary.append(overseas ? String.format(Locale.US, "60일선 $%.2f. ", data.ma60()) : String.format(Locale.US, "60일선 %.0f원. ", data.ma60()));
        }
        if (data.rsi() != null && !Double.isNaN(data.rsi())) {
            summary.append(String.format(Locale.US, "RSI(14)=%.1f. ", data.rsi()));
        }
        if (data.supportResistance() != null) {
            summary.append(overseas
                ? String.format(Locale.US, "저항 $%.2f, 지지 $%.2f. ", data.supportResistance().getResistance(), data.supportResistance().getSupport())
                : String.format(Locale.US, "저항 %.0f원, 지지 %.0f원. ", data.supportResistance().getResistance(), data.supportResistance().getSupport()));
        }
        return summary.toString();
    }

    private static String sanitizeConclusion(String text) {
        if (text == null) {
            return "";
        }
        return text
            .replaceAll("\\(티커\\s*[A-Z0-9.]+\\)", "")
            .replaceAll("\\(코드\\s*\\d{6}\\)", "")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static String firstLine(String text) {
        int index = text.indexOf('\n');
        return index >= 0 ? text.substring(0, index).trim() : text.trim();
    }
}
