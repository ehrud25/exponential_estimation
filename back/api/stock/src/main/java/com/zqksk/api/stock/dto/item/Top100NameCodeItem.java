package com.zqksk.api.stock.dto.item;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Top100NameCodeItem(
    String name,
    String code
) {
}
