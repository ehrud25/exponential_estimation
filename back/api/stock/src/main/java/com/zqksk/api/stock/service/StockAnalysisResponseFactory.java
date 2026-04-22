package com.zqksk.api.stock.service;

import com.zqksk.api.stock.dto.analysis.AnalysisData;
import com.zqksk.api.stock.dto.analysis.AnalysisResponse;
import com.zqksk.api.stock.util.TechnicalIndicatorCalculator.BollingerResult;
import com.zqksk.api.stock.util.TechnicalIndicatorCalculator.MacdResult;
import com.zqksk.api.stock.util.TechnicalIndicatorCalculator.SupportResistance;
import org.springframework.stereotype.Component;

import java.util.Locale;

import static java.lang.Double.isNaN;

@Component
class StockAnalysisResponseFactory {

    AnalysisResponse create(String stockCode, boolean overseas, AnalysisData data) {
        AnalysisNarrativeContext context = new AnalysisNarrativeContext(stockCode, overseas, data);
        return new AnalysisResponse(
            buildSummary(context),
            buildFullAnalysis(context),
            buildConclusion(context)
        );
    }

    private String buildConclusion(AnalysisNarrativeContext context) {
        return String.format(
            Locale.KOREA,
            "지금 {%s}은 %s 압력입니다.%n%s, %s이며 %s%n따라서 현재 매도하면 손실은 %s, 매수하면 반등 시 수익은 %s 가능성이 있습니다.",
            context.stockCode(),
            context.pressure(),
            context.shortTermTrend(),
            context.midTermTrend(),
            context.momentum(),
            context.lossRange(),
            context.upsideRange()
        );
    }

    private String buildSummary(AnalysisNarrativeContext context) {
        StringBuilder summary = new StringBuilder(String.format(Locale.US, "현재가 %s. ", context.formatPrice(context.current())));

        if (context.hasMa20()) {
            double gap = (context.current() - context.ma20()) / context.ma20() * 100;
            summary.append(String.format(Locale.US, "20일선 대비 %.1f%%. ", gap));
        }
        if (context.hasRsi()) {
            summary.append(String.format(Locale.US, "RSI(14)=%.1f. ", context.rsi()));
        }
        if (context.hasVolatility20()) {
            summary.append(String.format(Locale.US, "20일 변동성 %.2f%%. ", context.volatility20()));
        }
        appendMeaningfulText(summary, "PER ", context.data().price().per(), ". ");

        String text = summary.toString().trim();
        return text.length() > 50 ? text.substring(0, 47) + "..." : text;
    }

    private String buildFullAnalysis(AnalysisNarrativeContext context) {
        StringBuilder detail = new StringBuilder();
        detail.append(String.format(
            Locale.US,
            "[%s] 현재가 %s, 전일 대비 %s%s(%s%.2f%%). ",
            context.stockCode(),
            context.formatPrice(context.current()),
            context.sign(),
            context.formatChange(context.change()),
            context.sign(),
            context.changePct()
        ));

        appendPricePoint(detail, "시가", context.open(), ", ", context);
        appendPricePoint(detail, "고가", context.high(), ", ", context);
        appendPricePoint(detail, "저가", context.low(), ". ", context);

        appendMovingAverage(detail, context);
        appendIndicators(detail, context);
        appendValuation(detail, context);

        return detail.toString();
    }

    private void appendMovingAverage(StringBuilder detail, AnalysisNarrativeContext context) {
        if (context.hasMa5()) {
            detail.append(String.format(Locale.US, "5일선 %s, ", context.formatPrice(context.ma5())));
        }
        if (context.hasMa20()) {
            double gap20 = (context.current() - context.ma20()) / context.ma20() * 100;
            detail.append(String.format(Locale.US, "20일선 %s(현재가 대비 %.1f%%), ", context.formatPrice(context.ma20()), gap20));
        }
        if (context.hasMa60()) {
            detail.append(String.format(Locale.US, "60일선 %s. ", context.formatPrice(context.ma60())));
        }
    }

    private void appendIndicators(StringBuilder detail, AnalysisNarrativeContext context) {
        if (context.hasRsi()) {
            detail.append(String.format(Locale.US, "RSI(14) %.1f(%s). ", context.rsi(), context.rsiZone()));
        }

        MacdResult macd = context.data().macd();
        if (macd != null) {
            detail.append(String.format(Locale.US, "MACD %.2f, Signal %.2f, Histogram %.2f. ", macd.getMacdLine(), macd.getSignalLine(), macd.getHistogram()));
        }

        if (context.hasVolatility20()) {
            detail.append(String.format(Locale.US, "20일 변동성 %.2f%%. ", context.volatility20()));
        }

        BollingerResult bollinger = context.data().bollinger();
        if (bollinger != null) {
            detail.append(String.format(
                Locale.US,
                "볼린저밴드 상단 %s, 중단 %s, 하단 %s. ",
                context.formatPrice(bollinger.getUpper()),
                context.formatPrice(bollinger.getMiddle()),
                context.formatPrice(bollinger.getLower())
            ));
        }

        SupportResistance sr = context.data().supportResistance();
        if (sr != null && (sr.getResistance() > 0 || sr.getSupport() > 0)) {
            detail.append(String.format(Locale.US, "저항 %s, 지지 %s. ", context.formatPrice(sr.getResistance()), context.formatPrice(sr.getSupport())));
        }
    }

    private void appendValuation(StringBuilder detail, AnalysisNarrativeContext context) {
        appendMeaningfulText(detail, "PER ", context.data().price().per(), ". ");
        appendMeaningfulText(detail, "PBR ", context.data().price().pbr(), ". ");
        appendMeaningfulText(detail, context.overseas() ? "EPS $" : "EPS ", context.data().price().eps(), context.overseas() ? ". " : "원. ");
        appendMeaningfulText(detail, context.overseas() ? "시총 $" : "시총 ", context.data().price().hts_avls(), context.overseas() ? ". " : "원. ");
    }

    private void appendPricePoint(StringBuilder detail, String label, double value, String suffix, AnalysisNarrativeContext context) {
        if (value > 0) {
            detail.append(String.format(Locale.US, "%s %s%s", label, context.formatPrice(value), suffix));
        }
    }

    private void appendMeaningfulText(StringBuilder builder, String prefix, String value, String suffix) {
        if (value != null && !value.isBlank() && !"-".equals(value)) {
            builder.append(prefix).append(value).append(suffix);
        }
    }

    static final class AnalysisNarrativeContext {

        private final String stockCode;
        private final boolean overseas;
        private final AnalysisData data;
        private final double current;
        private final double open;
        private final double high;
        private final double low;
        private final double change;
        private final double changePct;

        AnalysisNarrativeContext(String stockCode, boolean overseas, AnalysisData data) {
            this.stockCode = stockCode;
            this.overseas = overseas;
            this.data = data;
            this.current = StockAnalysisDataFactory.parseDouble(data.price().stck_prpr());
            this.open = StockAnalysisDataFactory.parseDouble(data.price().stck_oprc());
            this.high = StockAnalysisDataFactory.parseDouble(data.price().stck_hgpr());
            this.low = StockAnalysisDataFactory.parseDouble(data.price().stck_lwpr());
            this.change = StockAnalysisDataFactory.parseDouble(data.price().prdy_vrss());
            this.changePct = StockAnalysisDataFactory.parseDouble(data.price().prdy_ctrt());
        }

        String stockCode() {
            return stockCode;
        }

        boolean overseas() {
            return overseas;
        }

        AnalysisData data() {
            return data;
        }

        double current() {
            return current;
        }

        double open() {
            return open;
        }

        double high() {
            return high;
        }

        double low() {
            return low;
        }

        double change() {
            return change;
        }

        double changePct() {
            return changePct;
        }

        Double ma5() {
            return data.ma5();
        }

        Double ma20() {
            return data.ma20();
        }

        Double ma60() {
            return data.ma60();
        }

        Double rsi() {
            return data.rsi();
        }

        Double volatility20() {
            return data.volatility20();
        }

        boolean hasMa5() {
            return hasNumber(ma5());
        }

        boolean hasMa20() {
            return hasNumber(ma20());
        }

        boolean hasMa60() {
            return hasNumber(ma60());
        }

        boolean hasRsi() {
            return hasNumber(rsi());
        }

        boolean hasVolatility20() {
            return hasNumber(volatility20());
        }

        String pressure() {
            if (!hasRsi()) {
                return "중립";
            }
            if (rsi() >= 70) {
                return "상방";
            }
            if (rsi() <= 30) {
                return "하방";
            }
            if (hasMa20()) {
                return current >= ma20() ? "상방" : "하방";
            }
            return "중립";
        }

        String shortTermTrend() {
            if (!hasMa5()) {
                return "단기 중립";
            }
            return current >= ma5() ? "단기 우위" : "단기 약세";
        }

        String midTermTrend() {
            if (!hasMa20()) {
                return "중기 중립";
            }
            return current >= ma20() ? "중기 우위" : "중기 약세";
        }

        String momentum() {
            if (hasRsi() && rsi() >= 65) {
                return "조정 가능성이 있는 구간입니다.";
            }
            if (hasRsi() && rsi() <= 35) {
                return "반등 가능성이 있는 구간입니다.";
            }
            return "추세가 아직 한쪽으로 강하게 기울진 않았습니다.";
        }

        String lossRange() {
            SupportResistance sr = data.supportResistance();
            if (sr == null || sr.getSupport() <= 0) {
                return "-5~-10%";
            }
            double downPct = (current - sr.getSupport()) / current * 100;
            return String.format(Locale.US, "-%.0f~-%.0f%%", Math.max(3, downPct - 2), Math.max(5, downPct + 2));
        }

        String upsideRange() {
            SupportResistance sr = data.supportResistance();
            if (sr == null || sr.getResistance() <= current) {
                return "+10~+15%";
            }
            double upPct = (sr.getResistance() - current) / current * 100;
            return String.format(Locale.US, "+%.0f~+%.0f%%", Math.max(1, upPct - 2), Math.max(3, upPct + 2));
        }

        String rsiZone() {
            if (!hasRsi()) {
                return "중립";
            }
            if (rsi() >= 70) {
                return "과매수권";
            }
            if (rsi() <= 30) {
                return "과매도권";
            }
            return "중립";
        }

        String sign() {
            return switch (data.price().prdy_vrss_sign()) {
                case "2" -> "+";
                case "5" -> "-";
                default -> "";
            };
        }

        String formatPrice(double value) {
            return overseas ? String.format(Locale.US, "$%,.2f", value) : String.format(Locale.US, "%,.0f원", value);
        }

        String formatChange(double value) {
            return overseas ? String.format(Locale.US, "$%,.2f", Math.abs(value)) : String.format(Locale.US, "%,.0f원", Math.abs(value));
        }

        private boolean hasNumber(Double value) {
            return value != null && !isNaN(value);
        }
    }
}
