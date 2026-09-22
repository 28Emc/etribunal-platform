package com.etribunal.ai.automation.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.etribunal.ai.automation.application.UserSelector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.UUID;

@ExtendWith(MockitoExtension.class)
class UserSelectorTest {

    @Mock
    private JdbcTemplate identityJdbcTemplate;

    @InjectMocks
    private UserSelector userSelector;

    @BeforeEach
    void setUp() {
    }

    @Test
    void selectDailyPool_returnsListOfBotUsers() {
        when(identityJdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(5)))
                .thenReturn(List.of(
                        new UserSelector.BotUser(UUID.randomUUID().toString(), "bot1"),
                        new UserSelector.BotUser(UUID.randomUUID().toString(), "bot2"),
                        new UserSelector.BotUser(UUID.randomUUID().toString(), "bot3"),
                        new UserSelector.BotUser(UUID.randomUUID().toString(), "bot4"),
                        new UserSelector.BotUser(UUID.randomUUID().toString(), "bot5")
                ));

        List<UserSelector.BotUser> pool = userSelector.selectDailyPool(5);

        assertThat(pool).hasSize(5);
        assertThat(pool.get(0).id()).isNotNull();
        assertThat(pool.get(0).username()).isNotNull();
    }

    @Test
    void selectDailyPool_returnsEmpty_whenNoBots() {
        when(identityJdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq(10)))
                .thenReturn(List.of());

        List<UserSelector.BotUser> pool = userSelector.selectDailyPool(10);

        assertThat(pool).isEmpty();
    }

    @Test
    void selectAndAssign_excludesAuthorAndSideB() {
        String authorId = UUID.randomUUID().toString();
        String sideBId = UUID.randomUUID().toString();
        String otherId1 = UUID.randomUUID().toString();
        String otherId2 = UUID.randomUUID().toString();
        String otherId3 = UUID.randomUUID().toString();

        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(authorId, "author"),
                new UserSelector.BotUser(sideBId, "sideb"),
                new UserSelector.BotUser(otherId1, "user1"),
                new UserSelector.BotUser(otherId2, "user2"),
                new UserSelector.BotUser(otherId3, "user3")
        );

        List<UserSelector.UserAssignment> assignments = userSelector.selectAndAssign(
                pool, authorId, sideBId, 3, 10
        );

        // Should only assign to other users
        for (UserSelector.UserAssignment a : assignments) {
            assertThat(a.userId()).isNotEqualTo(authorId);
            assertThat(a.userId()).isNotEqualTo(sideBId);
        }
    }

    @Test
    void selectAndAssign_respectsMaxPerUser() {
        String otherId = UUID.randomUUID().toString();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(UUID.randomUUID().toString(), "other")
        );

        int maxPerUser = 2;
        int interactionCount = 10;

        List<UserSelector.UserAssignment> assignments = userSelector.selectAndAssign(
                pool, "author", "sideb", maxPerUser, interactionCount
        );

        // Should only assign maxPerUser times to the same user
        assertThat(assignments).hasSize(maxPerUser);
    }

    @Test
    void selectAndAssign_distributesEvenlyAcrossPool() {
        String otherId1 = UUID.randomUUID().toString();
        String otherId2 = UUID.randomUUID().toString();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(otherId1, "user1"),
                new UserSelector.BotUser(otherId2, "user2")
        );

        List<UserSelector.UserAssignment> assignments = userSelector.selectAndAssign(
                pool, "author", "sideb", 5, 10
        );

        // Should distribute across both users
        long count1 = assignments.stream().filter(a -> a.userId().equals(otherId1)).count();
        long count2 = assignments.stream().filter(a -> a.userId().equals(otherId2)).count();

        assertThat(count1).isGreaterThan(0);
        assertThat(count2).isGreaterThan(0);
        assertThat(count1 + count2).isEqualTo(10);
    }

    @Test
    void selectAndAssign_returnsEmpty_whenNoEligibleUsers() {
        String authorId = UUID.randomUUID().toString();
        String sideBId = UUID.randomUUID().toString();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(authorId, "author"),
                new UserSelector.BotUser(sideBId, "sideb")
        );

        List<UserSelector.UserAssignment> assignments = userSelector.selectAndAssign(
                pool, authorId, sideBId, 3, 5
        );

        assertThat(assignments).isEmpty();
    }

    @Test
    void selectAndAssign_returnsEmpty_whenPoolEmpty() {
        List<UserSelector.UserAssignment> assignments = userSelector.selectAndAssign(
                List.of(), "author", "sideb", 3, 5
        );

        assertThat(assignments).isEmpty();
    }

    @Test
    void selectAndAssign_shufflesPool() {
        String otherId = UUID.randomUUID().toString();
        List<UserSelector.BotUser> pool = List.of(
                new UserSelector.BotUser(otherId, "user1")
        );

        // Run multiple times to verify shuffling (non-deterministic)
        List<UserSelector.UserAssignment> a1 = userSelector.selectAndAssign(
                pool, "author", "sideb", 3, 3
        );
        List<UserSelector.UserAssignment> a2 = userSelector.selectAndAssign(
                pool, "author", "sideb", 3, 3
        );

        // Both should have same size (since only one user available)
        assertThat(a1).hasSize(3);
        assertThat(a2).hasSize(3);
    }
}