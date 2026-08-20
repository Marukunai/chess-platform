package com.chessplatform.media.dto;

/** previewUrl: miniatura para la rejilla de resultados. fullUrl: lo que se manda de verdad como mensaje al elegirlo. */
public record GifSearchResult(String previewUrl, String fullUrl) {
}