package com.zqksk.api.stock.dto.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisDailyItem(
    String stck_bsop_date,
    String stck_clpr,
    String stck_oprc,
    String stck_hgpr,
    String stck_lwpr,
    String acml_vol,
    String prdy_vrss,
    String prdy_vrss_sign
) {
}
