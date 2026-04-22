package com.zqksk.api.stock.dto.analysis;

import com.zqksk.api.stock.dto.kis.KisDailyItem;
import com.zqksk.api.stock.dto.kis.KisPriceOutput;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record AnalysisRequest(
    @NotBlank(message = "stockCode는 필수입니다")
    String stockCode,
    String exchange,
    String market,
    KisPriceOutput price,
    List<KisDailyItem> dailyChart
) {

    public boolean isOverseas() {
        return "US".equalsIgnoreCase(market) || (exchange != null && !exchange.isBlank());
    }
}
