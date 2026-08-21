package com.chessplatform.media;

import com.chessplatform.media.dto.GifSearchResult;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GiphyClientTest {

    private static final String SAMPLE_RESPONSE = """
            {
              "data": [
                {
                  "id": "abc123",
                  "images": {
                    "fixed_height_small": { "url": "https://media.giphy.com/abc123/200.gif" },
                    "original": { "url": "https://media.giphy.com/abc123/giphy.gif" }
                  }
                },
                {
                  "id": "def456",
                  "images": {
                    "fixed_height_small": { "url": "https://media.giphy.com/def456/200.gif" },
                    "original": { "url": "https://media.giphy.com/def456/giphy.gif" }
                  }
                }
              ]
            }
            """;

    @Test
    void searchReturnsEmptyListWhenNoApiKeyIsConfigured() {
        RestClient.Builder builder = RestClient.builder();
        GiphyClient client = new GiphyClient(builder, ""); // clave vacía == sin configurar, ver application.yml

        List<GifSearchResult> results = client.search("chess");

        assertThat(results).isEmpty();
    }

    @Test
    void searchReturnsEmptyListWhenApiKeyIsNull() {
        RestClient.Builder builder = RestClient.builder();
        GiphyClient client = new GiphyClient(builder, null);

        List<GifSearchResult> results = client.search("chess");

        assertThat(results).isEmpty();
    }

    @Test
    void searchParsesPreviewAndFullUrlFromEachResult() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        GiphyClient client = new GiphyClient(builder, "test-api-key");

        mockServer.expect(requestTo("https://api.giphy.com/v1/gifs/search?api_key=test-api-key&q=chess&limit=24&rating=pg-13"))
                .andRespond(withSuccess(SAMPLE_RESPONSE, MediaType.APPLICATION_JSON));

        List<GifSearchResult> results = client.search("chess");

        mockServer.verify();
        assertThat(results).hasSize(2);
        assertThat(results.getFirst().previewUrl()).isEqualTo("https://media.giphy.com/abc123/200.gif");
        assertThat(results.getFirst().fullUrl()).isEqualTo("https://media.giphy.com/abc123/giphy.gif");
    }

    @Test
    void searchSendsTheQueryAsTheQParameter() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        GiphyClient client = new GiphyClient(builder, "test-api-key");

        // queryParam() de MockRestServiceServer compara contra el valor tal cual queda
        // en la URL ya construida, no contra el texto original sin codificar — el
        // espacio de "jaque mate" se convierte en %20 antes de que el matcher lo vea.
        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/gifs/search")))
                .andExpect(queryParam("q", "jaque%20mate"))
                .andRespond(withSuccess("""
                        {"data": []}
                        """, MediaType.APPLICATION_JSON));

        client.search("jaque mate");

        mockServer.verify();
    }

    @Test
    void searchReturnsEmptyListWhenGiphyRespondsWithAnError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        GiphyClient client = new GiphyClient(builder, "test-api-key");

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/gifs/search")))
                .andRespond(withServerError());

        // Un fallo del servicio externo no debería propagarse como excepción — el
        // buscador de GIFs es una función más, no algo de lo que dependa el resto.
        List<GifSearchResult> results = client.search("chess");

        assertThat(results).isEmpty();
    }

    @Test
    void searchReturnsEmptyListWhenAResultIsMissingImageVariants() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer mockServer = MockRestServiceServer.bindTo(builder).build();
        GiphyClient client = new GiphyClient(builder, "test-api-key");

        mockServer.expect(requestTo(org.hamcrest.Matchers.containsString("/v1/gifs/search")))
                .andRespond(withSuccess("""
                        {"data": [{"id": "sin-imagenes", "images": {}}]}
                        """, MediaType.APPLICATION_JSON));

        List<GifSearchResult> results = client.search("chess");

        assertThat(results).isEmpty();
    }
}