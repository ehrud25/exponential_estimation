package com.zqksk.api.stock.service;

import com.zqksk.api.stock.dto.analysis.AnalysisData;
import com.zqksk.api.stock.dto.analysis.AnalysisRequest;
import com.zqksk.api.stock.dto.analysis.AnalysisResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockAnalysisService {

    private final StockAnalysisPlanFactory planFactory;
    private final StockAnalysisResponseFactory responseFactory;

    public AnalysisResponse analyze(AnalysisRequest request) {
        StockAnalysisPlan plan = planFactory.create(request);
        if (!plan.isExecutable()) {
            return plan.failureResponse();
        }

        try {
            AnalysisData data = plan.load();
            return responseFactory.create(plan.stockCode(), plan.overseas(), data);
        } catch (Exception e) {
            log.warn("분석 실패: stockCode={}, overseas={}, error={}", plan.stockCode(), plan.overseas(), e.getMessage());
            return new AnalysisResponse(
                "분석 중 오류가 발생했습니다.",
                "시세 조회 또는 지표 계산 중 오류: " + e.getMessage(),
                null
            );
        }
    }
}
