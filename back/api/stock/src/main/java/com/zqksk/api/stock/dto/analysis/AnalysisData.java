package com.zqksk.api.stock.dto.analysis;

import com.zqksk.api.stock.dto.kis.KisDailyItem;
import com.zqksk.api.stock.dto.kis.KisPriceOutput;
import com.zqksk.api.stock.util.TechnicalIndicatorCalculator;

import java.util.List;

public record AnalysisData(
    KisPriceOutput price,
    List<KisDailyItem> dailyItems,
    List<Double> closes,
    List<Double> highs,
    List<Double> lows,
    Double ma5,
    Double ma20,
    Double ma60,
    List<Double> maList,
    Double rsi,
    TechnicalIndicatorCalculator.MacdResult macd,
    Double volatility20,
    TechnicalIndicatorCalculator.BollingerResult bollinger,
    TechnicalIndicatorCalculator.SupportResistance supportResistance
) {
}
