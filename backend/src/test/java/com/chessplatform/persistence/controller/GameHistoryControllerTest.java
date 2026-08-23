package com.chessplatform.persistence.controller;

import com.chessplatform.persistence.GameAnalysisService;
import com.chessplatform.persistence.GameReplayService;
import com.chessplatform.persistence.dto.GameAnalysisResponse;
import com.chessplatform.persistence.dto.GameDetailResponse;
import com.chessplatform.persistence.dto.GameSummaryResponse;
import com.chessplatform.persistence.entity.Game;
import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GameHistoryControllerTest {

    @Mock
    private GameRepository gameRepository;

    @Mock
    private GameAnalysisService analysisService;

    private GameHistoryController controller;

    @BeforeEach
    void setUp() {
        // GameReplayService real: es puro/barato, y así se comprueba el cableado
        // completo (jugadas guardadas -> FEN reconstruidos), no solo que se llamó a algo.
        controller = new GameHistoryController(gameRepository, new GameReplayService(), analysisService);
    }

    private static Game gameOf(User white, User black, String result, String moveList) {
        Game game = new Game(white, black, "5+3");
        game.setResult(result);
        game.setMoveList(moveList);
        return game;
    }

    private static Game gameOf(User white, User black, String result, String reason, String moveList) {
        Game game = gameOf(white, black, result, moveList);
        game.setReason(reason);
        return game;
    }

    @Test
    void historyForUserMapsGamesToSummaries() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Game game = gameOf(alice, bob, "1-0", "checkmate", "e2e4 e7e5");
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("user-1", "user-1"))
                .thenReturn(List.of(game));

        List<GameSummaryResponse> history = controller.historyForUser("user-1");

        assertThat(history).hasSize(1);
        GameSummaryResponse summary = history.getFirst();
        assertThat(summary.whiteUsername()).isEqualTo("alice");
        assertThat(summary.blackUsername()).isEqualTo("bob");
        assertThat(summary.result()).isEqualTo("1-0");
        assertThat(summary.reason()).isEqualTo("checkmate");
        assertThat(summary.timeControl()).isEqualTo("5+3");
    }

    @Test
    void historyForUserReturnsEmptyListWhenThereAreNoGames() {
        when(gameRepository.findByWhitePlayer_IdOrBlackPlayer_IdOrderByPlayedAtDesc("user-1", "user-1"))
                .thenReturn(List.of());

        assertThat(controller.historyForUser("user-1")).isEmpty();
    }

    @Test
    void gameDetailReturnsMovesAndReconstructedFenPositions() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Game game = gameOf(alice, bob, "1-0", "e2e4 e7e5");
        when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

        GameDetailResponse detail = controller.gameDetail("game-1");

        assertThat(detail.movesNotation()).containsExactly("e4", "e5");
        assertThat(detail.fenPositions()).hasSize(3); // inicial + 2 jugadas
        assertThat(detail.whiteUsername()).isEqualTo("alice");
        assertThat(detail.blackUsername()).isEqualTo("bob");
    }

    @Test
    void gameDetailHandlesAGameWithNoMovesRecordedYet() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Game game = gameOf(alice, bob, "1/2-1/2", null);
        when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

        GameDetailResponse detail = controller.gameDetail("game-1");

        assertThat(detail.movesNotation()).isEmpty();
        assertThat(detail.fenPositions()).hasSize(1); // solo la posición inicial
    }

    @Test
    void gameDetailThrowsNotFoundWhenTheGameDoesNotExist() {
        when(gameRepository.findById("missing-game")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.gameDetail("missing-game"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void gameDetailIncludesTheReason() {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Game game = gameOf(alice, bob, "0-1", "resignation", "e2e4");
        when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));

        GameDetailResponse detail = controller.gameDetail("game-1");

        assertThat(detail.reason()).isEqualTo("resignation");
    }

    @Test
    void gameAnalysisThrowsServiceUnavailableWhenTheEngineIsNotConfigured() {
        when(analysisService.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> controller.gameAnalysis("game-1"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void gameAnalysisThrowsNotFoundWhenTheGameDoesNotExist() {
        when(analysisService.isAvailable()).thenReturn(true);
        when(gameRepository.findById("missing-game")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.gameAnalysis("missing-game"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void gameAnalysisReturnsTheAnalyzedMoves() throws IOException {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Game game = gameOf(alice, bob, "1-0", "e2e4 e7e5");
        when(analysisService.isAvailable()).thenReturn(true);
        when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
        when(analysisService.analyze("e2e4 e7e5")).thenReturn(List.of(
                new GameAnalysisService.MoveAnalysis(1, "e4", 20, null, "best"),
                new GameAnalysisService.MoveAnalysis(2, "e5", 15, null, "best")
        ));

        GameAnalysisResponse response = controller.gameAnalysis("game-1");

        assertThat(response.gameId()).isEqualTo("game-1");
        assertThat(response.moves()).hasSize(2);
        assertThat(response.moves().getFirst().notation()).isEqualTo("e4");
        assertThat(response.moves().getFirst().classification()).isEqualTo("best");
    }

    @Test
    void gameAnalysisThrowsServiceUnavailableWhenTheEngineFailsMidAnalysis() throws IOException {
        User alice = new User("alice", "hash");
        User bob = new User("bob", "hash");
        Game game = gameOf(alice, bob, "1-0", "e2e4 e7e5");
        when(analysisService.isAvailable()).thenReturn(true);
        when(gameRepository.findById("game-1")).thenReturn(Optional.of(game));
        when(analysisService.analyze(anyString())).thenThrow(new IOException("el motor se cayó"));

        assertThatThrownBy(() -> controller.gameAnalysis("game-1"))
                .isInstanceOf(ResponseStatusException.class);
    }
}