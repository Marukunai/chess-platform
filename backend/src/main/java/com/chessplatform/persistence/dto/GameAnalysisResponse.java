package com.chessplatform.persistence.dto;

import java.util.List;

public record GameAnalysisResponse(String gameId, List<MoveAnalysisResponse> moves) {
}