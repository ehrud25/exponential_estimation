package com.zqksk.api.stock.service;

import com.zqksk.api.stock.config.KisProperties;
import com.zqksk.api.stock.dto.analysis.AnalysisRequest;
import com.zqksk.api.stock.dto.analysis.AnalysisResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.zqksk.api.stock.define.Message.ERROR_NOT_CODE;

@Component
@RequiredArgsConstructor
class StockAnalysisPlanFactory {

    private final KisProperties kisProperties;
    private final StockAnalysisDataFactory analysisDataFactory;

    StockAnalysisPlan create(AnalysisRequest request) {
        String stockCode = normalizeStockCode(request);
        if (stockCode.isEmpty()) {
            return StockAnalysisPlan.invalid(new AnalysisResponse(ERROR_NOT_CODE.message(), "종목 코드를 입력해 주세요.", null));
        }

        boolean overseas = request != null && request.isOverseas();
        if (hasProvidedData(request)) {
            return StockAnalysisPlan.executable(
                stockCode,
                overseas,
                () -> analysisDataFactory.fromProvidedData(request.price(), request.dailyChart())
            );
        }

        if (isOverseasSymbol(stockCode)) {
            String exchange = normalizeExchange(request);
            if (exchange.isEmpty()) {
                return StockAnalysisPlan.invalid(new AnalysisResponse(
                    "해외 종목은 거래소 코드가 필요합니다.",
                    "해외 종목 분석 시 exchange 값을 함께 보내 주세요. 예: NAS, NYS, AMS",
                    null
                ));
            }
            if (!kisProperties.isConfigured()) {
                return missingKisConfiguration();
            }
            return StockAnalysisPlan.executable(
                stockCode,
                true,
                () -> analysisDataFactory.fetchOverseas(stockCode, exchange)
            );
        }

        if (!kisProperties.isConfigured()) {
            return missingKisConfiguration();
        }

        return StockAnalysisPlan.executable(stockCode, false, () -> analysisDataFactory.fetchDomestic(stockCode));
    }

    private String normalizeStockCode(AnalysisRequest request) {
        if (request == null || request.stockCode() == null) {
            return "";
        }
        return request.stockCode().trim();
    }

    private String normalizeExchange(AnalysisRequest request) {
        if (request == null || request.exchange() == null) {
            return "";
        }
        return request.exchange().trim();
    }

    private boolean hasProvidedData(AnalysisRequest request) {
        return request != null
            && request.price() != null
            && request.dailyChart() != null
            && !request.dailyChart().isEmpty();
    }

    private boolean isOverseasSymbol(String stockCode) {
        return !stockCode.matches("\\d{6}");
    }

    private StockAnalysisPlan missingKisConfiguration() {
        return StockAnalysisPlan.invalid(new AnalysisResponse("KIS API가 설정되지 않았습니다.", "설정 후 다시 시도해 주세요.", null));
    }
}
