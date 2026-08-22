package com.chessplatform.bot;

import com.chessplatform.persistence.entity.User;
import com.chessplatform.persistence.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BotAccountSeederTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    private BotAccountSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new BotAccountSeeder(userRepository, passwordEncoder);
    }

    @Test
    void usernameForGivesADistinctNameForEachDifficulty() {
        List<String> names = List.of(
                BotAccountSeeder.usernameFor(BotDifficulty.EASY),
                BotAccountSeeder.usernameFor(BotDifficulty.MEDIUM),
                BotAccountSeeder.usernameFor(BotDifficulty.HARD)
        );

        assertThat(names).doesNotHaveDuplicates();
        assertThat(names).allMatch(name -> name.contains("Stockfish"));
    }

    @Test
    void seedBotAccountsCreatesAllThreeWhenNoneExistYet() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-inutilizable");

        seeder.seedBotAccounts();

        ArgumentCaptor<User> savedUsers = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(savedUsers.capture());
        assertThat(savedUsers.getAllValues()).allMatch(User::isBot);
        assertThat(savedUsers.getAllValues())
                .extracting(User::getUsername)
                .containsExactlyInAnyOrder(
                        BotAccountSeeder.usernameFor(BotDifficulty.EASY),
                        BotAccountSeeder.usernameFor(BotDifficulty.MEDIUM),
                        BotAccountSeeder.usernameFor(BotDifficulty.HARD)
                );
    }

    @Test
    void seedBotAccountsDoesNothingWhenAllThreeAlreadyExist() {
        User existingBot = new User("Stockfish (Fácil)", "hash");
        existingBot.markAsBot();
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.of(existingBot));

        seeder.seedBotAccounts();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void seedBotAccountsOnlyCreatesTheOnesThatAreMissing() {
        User existingEasyBot = new User(BotAccountSeeder.usernameFor(BotDifficulty.EASY), "hash");
        existingEasyBot.markAsBot();
        when(userRepository.findByUsername(BotAccountSeeder.usernameFor(BotDifficulty.EASY)))
                .thenReturn(Optional.of(existingEasyBot));
        when(userRepository.findByUsername(BotAccountSeeder.usernameFor(BotDifficulty.MEDIUM)))
                .thenReturn(Optional.empty());
        when(userRepository.findByUsername(BotAccountSeeder.usernameFor(BotDifficulty.HARD)))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-inutilizable");

        seeder.seedBotAccounts();

        verify(userRepository, times(2)).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void seedBotAccountsMarksEachCreatedAccountAsBot() {
        when(userRepository.findByUsername(anyString())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(anyString())).thenReturn("hash-inutilizable");

        seeder.seedBotAccounts();

        ArgumentCaptor<User> savedUsers = ArgumentCaptor.forClass(User.class);
        verify(userRepository, times(3)).save(savedUsers.capture());
        assertThat(savedUsers.getAllValues()).allMatch(User::isBot);
    }

    @Test
    void difficultyForFindsTheDifficultyFromABotsUsername() {
        for (BotDifficulty difficulty : BotDifficulty.values()) {
            assertThat(BotAccountSeeder.difficultyFor(BotAccountSeeder.usernameFor(difficulty)))
                    .contains(difficulty);
        }
    }

    @Test
    void difficultyForReturnsEmptyForAnyOtherUsername() {
        assertThat(BotAccountSeeder.difficultyFor("un-humano-cualquiera")).isEmpty();
    }
}