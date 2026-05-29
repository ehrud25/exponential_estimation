package com.zqksk.api.stock.client;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zqksk.api.stock.config.KisProperties;
import com.zqksk.api.stock.dto.kis.KisDailyItem;
import com.zqksk.api.stock.dto.kis.KisPriceOutput;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class KisOverseasApiClient {

    private static final String PRICE_PATH = "/uapi/overseas-price/v1/quotations/price";
    private static final String DAILY_PRICE_PATH = "/uapi/overseas-price/v1/quotations/dailyprice";
    private static final String TR_ID_PRICE = "HHDFS00000300";
    private static final String TR_ID_DAILY = "HHDFS76240000";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final KisApiClient kisApiClient;
    private final KisProperties kisProperties;
    private final RestTemplate restTemplate;

    private String getBaseUrl() {
        return "practice".equalsIgnoreCase(kisProperties.getEnvironment())
            ? "https://openapivts.koreainvestment.com:29443"
            : "https://openapi.koreainvestment.com:9443";
    }

    public KisPriceOutput inquireOverseasPrice(String excd, String symb) {
        String url = getBaseUrl() + PRICE_PATH + "?AUTH=&EXCD=" + excd.trim() + "&SYMB=" + symb.trim();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + kisApiClient.getAccessToken());
        headers.set("appkey", kisProperties.getAppKey());
        headers.set("appsecret", kisProperties.getAppSecret());
        headers.set("tr_id", TR_ID_PRICE);

        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null || res.getBody().isBlank()) {
            throw new IllegalStateException("KIS 해외 현재가 조회 실패");
        }

        JsonNode body = readTree(res.getBody());
        if (!"0".equals(body.path("rt_cd").asText(""))) {
            throw new IllegalStateException("KIS 해외 API 오류: " + body.path("msg1").asText(""));
        }

        JsonNode out = body.path("output");
        if (out.isArray() && out.size() > 0) {
            out = out.get(0);
        }
        return mapOverseasPriceToKis(out);
    }

    public List<KisDailyItem> inquireOverseasDailyChart(String excd, String symb, String endDate) {
        String url = getBaseUrl() + DAILY_PRICE_PATH
            + "?AUTH=&EXCD=" + excd.trim()
            + "&SYMB=" + symb.trim()
            + "&GUBN=0&BYMD=" + endDate
            + "&MODP=0";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("authorization", "Bearer " + kisApiClient.getAccessToken());
        headers.set("appkey", kisProperties.getAppKey());
        headers.set("appsecret", kisProperties.getAppSecret());
        headers.set("tr_id", TR_ID_DAILY);

        ResponseEntity<String> res = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        if (!res.getStatusCode().is2xxSuccessful() || res.getBody() == null || res.getBody().isBlank()) {
            throw new IllegalStateException("KIS 해외 기간별 시세 조회 실패");
        }

        JsonNode body = readTree(res.getBody());
        if (!"0".equals(body.path("rt_cd").asText(""))) {
            throw new IllegalStateException("KIS 해외 API 오류: " + body.path("msg1").asText(""));
        }

        List<KisDailyItem> list = new ArrayList<>();
        JsonNode output2 = body.path("output2");
        if (output2.isArray()) {
            for (JsonNode item : output2) {
                list.add(mapOverseasDailyToKis(item));
            }
        }
        return list;
    }

    private static KisPriceOutput mapOverseasPriceToKis(JsonNode out) {
        String last = text(out, "last");
        String diff = text(out, "diff");
        String rate = text(out, "rate");
        String sign = normalizeSign(text(out, "sign"), diff);

        return new KisPriceOutput(
            last,
            diff != null ? diff : "0",
            sign,
            rate != null ? rate : "0",
            last,
            last,
            last,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private static KisDailyItem mapOverseasDailyToKis(JsonNode item) {
        return new KisDailyItem(
            text(item, "xymd"),
            text(item, "clos"),
            text(item, "open"),
            text(item, "high"),
            text(item, "low"),
            null,
            text(item, "diff"),
            text(item, "sign")
        );
    }

    private static String normalizeSign(String sign, String diff) {
        if (sign == null || sign.isEmpty()) {
            double delta = parseDoubleSafe(diff);
            return delta > 0 ? "2" : delta < 0 ? "5" : "3";
        }
        if ("+".equals(sign) || "2".equals(sign)) {
            return "2";
        }
        if ("-".equals(sign) || "5".equals(sign)) {
            return "5";
        }
        return "3";
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("KIS 해외 API 응답 JSON 파싱 실패: " + e.getMessage());
        }
    }

    private static String text(JsonNode node, String key) {
        if (node == null) {
            return null;
        }
        JsonNode value = node.path(key);
        return value.isMissingNode() ? null : value.asText(null);
    }

    private static double parseDoubleSafe(String s) {
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
