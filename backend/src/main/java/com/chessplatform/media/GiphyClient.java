package com.chessplatform.media;

import com.chessplatform.media.dto.GifSearchResult;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Cliente hacia la API de búsqueda de Giphy — la clave de API vive SOLO aquí, en el
 * backend, nunca en el cliente web (cualquiera podría leerla del código fuente servido
 * al navegador y agotar la cuota, o usarla para sus propios fines). El cliente web solo
 * habla con GifSearchController, nunca con Giphy directamente.
 */
@Component
public class GiphyClient {

    private static final Logger log = LoggerFactory.getLogger(GiphyClient.class);
    private static final int RESULT_LIMIT = 24;

    private final RestClient restClient;
    private final String apiKey;

    public GiphyClient(RestClient.Builder restClientBuilder, @Value("${giphy.api-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = restClientBuilder.baseUrl("https://api.giphy.com").build();
    }

    /**
     * Lista vacía en dos casos distintos: sin clave configurada (el buscador de GIFs es
     * una función más, no algo de lo que dependa el resto de la plataforma — ver
     * application.yml) o si Giphy falla por lo que sea (caído, cuota agotada, clave
     * inválida). En ningún caso se propaga la excepción hacia quien pidió la búsqueda —
     * un servicio externo caído no debería tirar la petición del usuario.
     */
    public List<GifSearchResult> search(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
        try {
            GiphySearchResponse response = restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/v1/gifs/search")
                            .queryParam("api_key", apiKey)
                            .queryParam("q", query)
                            .queryParam("limit", RESULT_LIMIT)
                            .queryParam("rating", "pg-13")
                            .build())
                    .retrieve()
                    .body(GiphySearchResponse.class);

            if (response == null || response.data() == null) {
                return List.of();
            }
            return response.data().stream()
                    .filter(gif -> gif.images() != null && gif.images().preview() != null && gif.images().original() != null)
                    .map(gif -> new GifSearchResult(gif.images().preview().url(), gif.images().original().url()))
                    .toList();
        } catch (RestClientException e) {
            log.warn("Fallo al buscar GIFs en Giphy para \"{}\"", query, e);
            return List.of();
        }
    }

    // Records privados que reflejan SOLO los campos de la respuesta de Giphy que hacen
    // falta aquí — su API devuelve mucho más (usuario que lo subió, metadatos, enlaces
    // de compartir...) que no nos interesa para nada.
    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiphySearchResponse(List<GiphyGif> data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiphyGif(String id, GiphyImages images) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiphyImages(
            @JsonProperty("fixed_height_small") GiphyImageVariant preview,
            GiphyImageVariant original
    ) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record GiphyImageVariant(String url) {
    }
}