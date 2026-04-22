package com.zqksk.api.stock.dto.kakao;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoSkillRequest(
    UserRequest userRequest
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UserRequest(String utterance) {
    }
}
