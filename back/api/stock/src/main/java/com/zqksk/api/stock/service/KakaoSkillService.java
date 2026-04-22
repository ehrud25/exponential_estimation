package com.zqksk.api.stock.service;

import com.zqksk.api.stock.define.Code;
import com.zqksk.api.stock.dto.item.Top500DomesticAnalysisItem;
import com.zqksk.api.stock.dto.item.Top500OverseasAnalysisItem;
import com.zqksk.api.stock.storage.Top100AnalysisStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.zqksk.api.stock.define.Message.DEFAULT_NOT_IN_TOP500;

@Service
@RequiredArgsConstructor
public class KakaoSkillService {

    private static final String MSG_NOT_IN_TOP500 = "TOP500에 포함된 종목만 조회 가능합니다. 없는 종목입니다.";

    private static final Map<String, String> FALLBACK_OVERSEAS_NAME_TO_EXCD_SYMBOL = Map.ofEntries(
        Map.entry("엔비디아", "NAS,NVDA"), Map.entry("nvidia", "NAS,NVDA"), Map.entry("nvda", "NAS,NVDA"),
        Map.entry("애플", "NAS,AAPL"), Map.entry("apple", "NAS,AAPL"), Map.entry("aapl", "NAS,AAPL"),
        Map.entry("마이크로소프트", "NAS,MSFT"), Map.entry("microsoft", "NAS,MSFT"), Map.entry("msft", "NAS,MSFT"),
        Map.entry("테슬라", "NAS,TSLA"), Map.entry("tesla", "NAS,TSLA"), Map.entry("tsla", "NAS,TSLA"),
        Map.entry("알파벳", "NAS,GOOGL"), Map.entry("구글", "NAS,GOOGL"), Map.entry("google", "NAS,GOOGL"), Map.entry("googl", "NAS,GOOGL"),
        Map.entry("아마존", "NAS,AMZN"), Map.entry("amazon", "NAS,AMZN"), Map.entry("amzn", "NAS,AMZN"),
        Map.entry("메타", "NAS,META"), Map.entry("meta", "NAS,META"), Map.entry("페이스북", "NAS,META"),
        Map.entry("amd", "NAS,AMD"), Map.entry("인텔", "NAS,INTC"), Map.entry("intel", "NAS,INTC"), Map.entry("intc", "NAS,INTC"),
        Map.entry("넷플릭스", "NAS,NFLX"), Map.entry("netflix", "NAS,NFLX"), Map.entry("nflx", "NAS,NFLX"),
        Map.entry("코스트코", "NAS,COST"), Map.entry("costco", "NAS,COST")
    );

    private final Top100AnalysisStore top100AnalysisStore;

    public String analyzeFromUtterance(String utterance) {
        if (utterance == null || utterance.isBlank()) {
            return "종목명 또는 6자리 종목코드를 입력해 주세요. 예: 삼성전자, 005930";
        }

        String trimmed = utterance.trim();
        Map<String, String> domesticMap = top100AnalysisStore.loadTop500DomesticAnalysisMap();
        Map<String, String> overseasMap = top100AnalysisStore.loadTop500OverseasAnalysisMap();
        List<Top500DomesticAnalysisItem> domesticItems = top100AnalysisStore.loadTop500DomesticAnalysisItems();
        List<Top500OverseasAnalysisItem> overseasItems = top100AnalysisStore.loadTop500OverseasAnalysisItems();

        if (trimmed.matches("\\d{6}")) {
            String analysis = domesticMap.get(trimmed);
            return analysis != null && !analysis.isBlank() ? analysis : MSG_NOT_IN_TOP500;
        }

        Optional<Top500DomesticAnalysisItem> domesticMatch = findDomesticByName(domesticItems, trimmed);
        if (domesticMatch.isPresent() && domesticMatch.get().analysis() != null && !domesticMatch.get().analysis().isBlank()) {
            return domesticMatch.get().analysis();
        }

        String fallbackCode = resolveFallbackName(trimmed);
        if (fallbackCode != null) {
            String analysis = domesticMap.get(fallbackCode);
            if (analysis != null && !analysis.isBlank()) {
                return analysis;
            }
        }

        Optional<OverseasResolved> overseas = resolveOverseasForTop500(trimmed, overseasItems);
        if (overseas.isPresent()) {
            String key = overseas.get().excd() + "," + overseas.get().symbol();
            String analysis = overseasMap.get(key);
            if (analysis != null && !analysis.isBlank()) {
                return analysis;
            }
        }

        return DEFAULT_NOT_IN_TOP500.message();
    }

    private Optional<Top500DomesticAnalysisItem> findDomesticByName(List<Top500DomesticAnalysisItem> items, String utterance) {
        String normalized = utterance.replaceAll("\\s+", "").toLowerCase();
        for (Top500DomesticAnalysisItem item : items) {
            if (item.code() != null && item.code().equals(utterance.trim())) {
                return Optional.of(item);
            }
            if (item.name() == null) {
                continue;
            }
            String nameNorm = item.name().replaceAll("\\s+", "").toLowerCase();
            if (nameNorm.equals(normalized) || nameNorm.contains(normalized) || normalized.contains(nameNorm)) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    private Optional<OverseasResolved> resolveOverseasForTop500(String utterance, List<Top500OverseasAnalysisItem> overseasItems) {
        String normalized = utterance.replaceAll("\\s+", "").toLowerCase();
        String excdSymbol = FALLBACK_OVERSEAS_NAME_TO_EXCD_SYMBOL.get(normalized);
        if (excdSymbol != null) {
            String[] parts = excdSymbol.split(",", 2);
            if (parts.length == 2 && !parts[0].isBlank() && !parts[1].isBlank()) {
                return Optional.of(new OverseasResolved(parts[0].trim(), parts[1].trim()));
            }
        }

        for (Top500OverseasAnalysisItem item : overseasItems) {
            if (item.symbol() != null && item.symbol().equalsIgnoreCase(normalized)) {
                return Optional.of(new OverseasResolved(item.excd(), item.symbol()));
            }
            if (item.symbol() != null && normalized.equals(item.symbol().replaceAll("\\s+", "").toLowerCase())) {
                return Optional.of(new OverseasResolved(item.excd(), item.symbol()));
            }
        }
        return Optional.empty();
    }

    private String resolveFallbackName(String utterance) {
        Code code = Code.fromValue(utterance.replaceAll("\\s+", "").toLowerCase());
        return code != null ? code.toString() : null;
    }

    private record OverseasResolved(String excd, String symbol) {
    }
}
