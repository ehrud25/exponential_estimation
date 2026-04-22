package com.zqksk.api.stock.dto.kis;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KisPriceOutput(
    String stck_prpr,
    String prdy_vrss,
    String prdy_vrss_sign,
    String prdy_ctrt,
    String stck_oprc,
    String stck_hgpr,
    String stck_lwpr,
    String acml_vol,
    @JsonProperty("acml_tr_pbmn") String acmlTrPbmn,
    String hts_avls,
    String per,
    String pbr,
    String eps,
    String stck_mxpr,
    String stck_llam,
    String prdt_name
) {
}
