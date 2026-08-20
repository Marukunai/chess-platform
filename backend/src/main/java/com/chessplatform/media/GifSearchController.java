package com.chessplatform.media;

import com.chessplatform.media.dto.GifSearchResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Proxy hacia Giphy — requiere identidad (cae en el .anyRequest().authenticated() por
 * defecto de SecurityConfig, no hace falta ninguna regla extra) a propósito: es la
 * única forma de que alguien llegue hasta la cuota de la clave de API, y sin exigir
 * sesión cualquiera podría agotarla sin ni siquiera haber entrado a la plataforma.
 */
@RestController
@RequestMapping("/api/gifs")
public class GifSearchController {

    private final GiphyClient giphyClient;

    public GifSearchController(GiphyClient giphyClient) {
        this.giphyClient = giphyClient;
    }

    @GetMapping("/search")
    public List<GifSearchResult> search(@RequestParam String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return giphyClient.search(q.trim());
    }
}