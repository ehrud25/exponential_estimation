package com.zqksk.api.stock.dto.item;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Top100OverseasNameCodeItem(
    String excd,
    String symbol
) {
}
