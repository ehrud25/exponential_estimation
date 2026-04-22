package com.zqksk.api.stock.dto.kakao;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record KakaoSkillResponse(
    String version,
    SkillTemplate template
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SkillTemplate(List<OutputItem> outputs) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OutputItem(SimpleText simpleText) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SimpleText(String text) {
    }

    public static KakaoSkillResponse ofText(String text) {
        return new KakaoSkillResponse(
            "2.0",
            new SkillTemplate(List.of(
                new OutputItem(new SimpleText(text != null ? text : "응답을 생성하지 못했습니다."))
            ))
        );
    }
}
