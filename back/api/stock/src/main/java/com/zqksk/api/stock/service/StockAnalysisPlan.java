package com.zqksk.api.stock.service;

import com.zqksk.api.stock.dto.analysis.AnalysisData;
import com.zqksk.api.stock.dto.analysis.AnalysisResponse;

final class StockAnalysisPlan {

    private final String stockCode;
    private final boolean overseas;
    private final AnalysisResponse failureResponse;
    private final AnalysisLoader loader;

    private StockAnalysisPlan(String stockCode, boolean overseas, AnalysisResponse failureResponse, AnalysisLoader loader) {
        this.stockCode = stockCode;
        this.overseas = overseas;
        this.failureResponse = failureResponse;
        this.loader = loader;
    }

    static StockAnalysisPlan executable(String stockCode, boolean overseas, AnalysisLoader loader) {
        return new StockAnalysisPlan(stockCode, overseas, null, loader);
    }

    static StockAnalysisPlan invalid(AnalysisResponse failureResponse) {
        return new StockAnalysisPlan("", false, failureResponse, null);
    }

    boolean isExecutable() {
        return loader != null;
    }

    String stockCode() {
        return stockCode;
    }

    boolean overseas() {
        return overseas;
    }

    AnalysisResponse failureResponse() {
        return failureResponse;
    }

    AnalysisData load() {
        if (loader == null) {
            throw new IllegalStateException("실행 가능한 분석 계획이 아닙니다.");
        }
        return loader.load();
    }

    @FunctionalInterface
    interface AnalysisLoader {
        AnalysisData load();
    }
}
