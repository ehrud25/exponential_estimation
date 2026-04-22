package com.zqksk.api.stock.controller;

import com.zqksk.api.stock.dto.analysis.AnalysisRequest;
import com.zqksk.api.stock.dto.analysis.AnalysisResponse;
import com.zqksk.api.stock.dto.kakao.KakaoSkillRequest;
import com.zqksk.api.stock.dto.kakao.KakaoSkillResponse;
import com.zqksk.api.stock.service.KakaoSkillService;
import com.zqksk.api.stock.service.StockAnalysisService;
import com.zqksk.api.stock.service.StockGeminiAnalysisService;
import com.zqksk.api.support.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController {

    private final StockAnalysisService stockAnalysisService;
    private final StockGeminiAnalysisService stockGeminiAnalysisService;
    private final KakaoSkillService kakaoSkillService;

    @PostMapping("/analysis")
    public ResponseEntity<ApiResponse> analysis(@Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.ok(ApiResponse.of(stockAnalysisService.analyze(request)));
    }

    @PostMapping("/analysis/gemini")
    public ResponseEntity<AnalysisResponse> analysisWithGemini(@Valid @RequestBody AnalysisRequest request) {
        return ResponseEntity.ok(stockGeminiAnalysisService.analyzeWithGemini(request.stockCode()));
    }

    @RequestMapping(value = "/kakao/skill", method = {RequestMethod.GET, RequestMethod.HEAD})
    public ResponseEntity<KakaoSkillResponse> kakaoSkillGetOrHead() {
        return ResponseEntity.ok(KakaoSkillResponse.ofText("카카오 스킬은 POST로 요청해 주세요."));
    }

    @PostMapping("/kakao/skill")
    public ResponseEntity<KakaoSkillResponse> kakaoSkill(@RequestBody KakaoSkillRequest request) {
        String utterance = request != null && request.userRequest() != null
            ? request.userRequest().utterance()
            : null;

        try {
            return ResponseEntity.ok(KakaoSkillResponse.ofText(kakaoSkillService.analyzeFromUtterance(utterance)));
        } catch (Exception e) {
            log.error("카카오 스킬 처리 중 오류: utterance={}, error={}", utterance, e.getMessage(), e);
            return ResponseEntity.ok(KakaoSkillResponse.ofText(
                "일시적인 오류가 발생했습니다. 잠시 후 다시 시도해 주세요."
            ));
        }
    }
}
