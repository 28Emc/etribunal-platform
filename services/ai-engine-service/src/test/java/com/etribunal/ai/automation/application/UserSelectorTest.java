package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.Collections;
import java.util.List;

@ExtendWith(MockitoExtension.class)
class UserSelectorTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private UserSelector userSelector;

    @SuppressWarnings("unchecked")
    @Test
    void selectDailyPool_returnsEligibleUsers() {
        List<UserSelector.BotUser> expected = List.of(
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10)))
                .thenReturn(expected);

        List<UserSelector.BotUser> result = userSelector.selectDailyPool(10);
        assertThat(result).hasSize(2);
        assertThat(result.get(0).id()).isEqualTo("u1");
    }

    @SuppressWarnings("unchecked")
    @Test
    void selectDailyPool_returnsEmptyWhenNoBots() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), anyInt()))
                .thenReturn(Collections.emptyList());

        List<UserSelector.BotUser> result = userSelector.selectDailyPool(10);
        assertThat(result).isEmpty();
    }

    @Test
    void selectAndAssign_excludesAuthorAndSideB() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("author", "author-bot"),
                new UserSelector.BotUser("sideb", "sideb-bot"),
                new UserSelector.BotUser("u1", "bot1"),
                new UserSelector.BotUser("u2", "bot2")
        );

        List<UserSelector.UserAssignment> result = userSelector.selectAndAssign(
                pool, "author", "sideb", 3, 5
        );

        assertThat(result).isNotEmpty();
        result.forEach(a -> {
            assertThat(a.userId()).isNotEqualTo("author");
            assertThat(a.userId()).isNotEqualTo("sideb");
        });
    }

    @Test
    void selectAndAssign_returnsEmptyWhenNoAvailable() {
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser("author", "author-bot")
        );

        List<UserSelector.UserAssignment> result = userSelector.selectAndAssign(
                pool, "author", null, 3, 5
        );

        assertThat(result).isEmpty();
    }
}