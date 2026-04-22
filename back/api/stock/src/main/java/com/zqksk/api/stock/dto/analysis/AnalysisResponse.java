package com.zqksk.api.stock.dto.analysis;

public record AnalysisResponse(
    String summary,
    String fullAnalysis,
    String conclusion
) {
}
