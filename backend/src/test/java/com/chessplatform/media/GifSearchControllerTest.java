package com.chessplatform.media;

import com.chessplatform.media.dto.GifSearchResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GifSearchControllerTest {

    @Mock
    private GiphyClient giphyClient;

    private GifSearchController controller;

    @BeforeEach
    void setUp() {
        controller = new GifSearchController(giphyClient);
    }

    @Test
    void searchDelegatesToGiphyClientWithTheTrimmedQuery() {
        when(giphyClient.search("jaque mate")).thenReturn(List.of(new GifSearchResult("preview", "full")));

        List<GifSearchResult> results = controller.search("  jaque mate  ");

        assertThat(results).hasSize(1);
        verify(giphyClient).search("jaque mate");
    }

    @Test
    void searchReturnsEmptyListForABlankQueryWithoutCallingGiphyClient() {
        List<GifSearchResult> results = controller.search("   ");

        assertThat(results).isEmpty();
        verify(giphyClient, never()).search(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void searchReturnsEmptyListForANullQueryWithoutCallingGiphyClient() {
        List<GifSearchResult> results = controller.search(null);

        assertThat(results).isEmpty();
        verify(giphyClient, never()).search(org.mockito.ArgumentMatchers.anyString());
    }
}