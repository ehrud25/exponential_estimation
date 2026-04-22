package com.zqksk.api.stock.service;

import com.zqksk.api.stock.client.KisApiClient;
import com.zqksk.api.stock.client.KisOverseasApiClient;
import com.zqksk.api.stock.define.indecies.MA;
import com.zqksk.api.stock.dto.analysis.AnalysisData;
import com.zqksk.api.stock.dto.kis.KisDailyItem;
import com.zqksk.api.stock.dto.kis.KisPriceOutput;
import com.zqksk.api.stock.util.TechnicalIndicatorCalculator;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.zqksk.api.stock.define.indecies.RSI.RSI15;

@Component
public class StockAnalysisDataFactory {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DAILY_DAYS = 120;

    private final KisApiClient kisApiClient;
    private final KisOverseasApiClient kisOverseasApiClient;

    public StockAnalysisDataFactory(KisApiClient kisApiClient, KisOverseasApiClient kisOverseasApiClient) {
        this.kisApiClient = kisApiClient;
        this.kisOverseasApiClient = kisOverseasApiClient;
    }

    public AnalysisData fromProvidedData(KisPriceOutput price, List<KisDailyItem> dailyChart) {
        return build(price, dailyChart);
    }

    public AnalysisData fetchDomestic(String stockCode) {
        KisPriceOutput price = kisApiClient.inquirePrice(stockCode);
        String endDate = LocalDate.now().format(YYYYMMDD);
        String startDate = LocalDate.now().minusDays(DAILY_DAYS).format(YYYYMMDD);
        List<KisDailyItem> dailyItems = kisApiClient.inquireDailyChart(stockCode, startDate, endDate);
        return build(price, dailyItems);
    }

    public AnalysisData fetchOverseas(String symbol, String exchange) {
        KisPriceOutput price = kisOverseasApiClient.inquireOverseasPrice(exchange, symbol);
        String endDate = LocalDate.now().format(YYYYMMDD);
        List<KisDailyItem> dailyItems = kisOverseasApiClient.inquireOverseasDailyChart(exchange, symbol, endDate);
        if (dailyItems == null || dailyItems.isEmpty()) {
            throw new IllegalStateException("해외 일봉 데이터가 없습니다. 종목코드와 거래소 코드를 확인해 주세요.");
        }
        return build(price, dailyItems);
    }

    public static AnalysisData build(KisPriceOutput price, List<KisDailyItem> dailyItems) {
        List<KisDailyItem> safeDailyItems = dailyItems == null ? List.of() : dailyItems;
        List<KisDailyItem> sorted = new ArrayList<>(safeDailyItems);
        sorted.sort((a, b) -> {
            if (a.stck_bsop_date() == null || b.stck_bsop_date() == null) {
                return 0;
            }
            return a.stck_bsop_date().compareTo(b.stck_bsop_date());
        });

        List<Double> closes = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        for (KisDailyItem item : sorted) {
            closes.add(parseDouble(item.stck_clpr()));
            highs.add(parseDouble(item.stck_hgpr()));
            lows.add(parseDouble(item.stck_lwpr()));
        }

        List<Double> maList = new ArrayList<>();
        for (MA ma : MA.values()) {
            maList.add(ma.calculate(closes));
        }

        Double ma5 = closes.size() >= 5 ? TechnicalIndicatorCalculator.ma(closes, 5) : null;
        Double ma20 = closes.size() >= 20 ? TechnicalIndicatorCalculator.ma(closes, 20) : null;
        Double ma60 = closes.size() >= 60 ? TechnicalIndicatorCalculator.ma(closes, 60) : null;
        Double rsi = closes.size() >= 15 ? TechnicalIndicatorCalculator.rsi14(closes) : null;
        if (rsi == null && !closes.isEmpty()) {
            rsi = RSI15.calculate(closes);
        }
        TechnicalIndicatorCalculator.MacdResult macd = TechnicalIndicatorCalculator.macd(closes);
        Double volatility20 = closes.size() >= 21 ? TechnicalIndicatorCalculator.volatility(closes, 20) : null;
        TechnicalIndicatorCalculator.BollingerResult bollinger = TechnicalIndicatorCalculator.bollingerBands(closes);
        TechnicalIndicatorCalculator.SupportResistance sr = TechnicalIndicatorCalculator.supportResistance(highs, lows, Math.min(20, highs.size()));

        return new AnalysisData(
            price,
            Collections.unmodifiableList(new ArrayList<>(safeDailyItems)),
            Collections.unmodifiableList(closes),
            Collections.unmodifiableList(highs),
            Collections.unmodifiableList(lows),
            ma5,
            ma20,
            ma60,
            Collections.unmodifiableList(maList),
            rsi,
            macd,
            volatility20,
            bollinger,
            sr
        );
    }

    public static double parseDouble(String s) {
        if (s == null || s.isBlank()) {
            return 0;
        }
        try {
            return Double.parseDouble(s.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
