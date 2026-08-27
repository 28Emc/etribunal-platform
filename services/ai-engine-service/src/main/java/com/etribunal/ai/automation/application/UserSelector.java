package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserSelector {

    private final JdbcTemplate jdbcTemplate;

    public UserSelector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record BotUser(String id, String username) {}

    public record UserAssignment(String userId, int interactionCount) {}

    public List<BotUser> selectDailyPool(int size) {
        List<BotUser> eligible = jdbcTemplate.query(
            """
            SELECT id, username FROM users
            WHERE is_bot = true
              AND automation_enabled = true
              AND deleted_at IS NULL
              AND is_anonymous = false
            ORDER BY RANDOM()
            LIMIT ?
            """,
            (rs, rowNum) -> new BotUser(rs.getString("id"), rs.getString("username")),
            size
        );
        return eligible;
    }

    public List<UserAssignment> selectAndAssign(
            List<BotUser> pool,
            String authorId,
            String sideBUserId,
            int maxPerUser,
            int interactionCount
    ) {
        List<BotUser> available = pool.stream()
                .filter(u -> !u.id().equals(authorId))
                .filter(u -> !u.id().equals(sideBUserId))
                .collect(Collectors.toList());

        if (available.isEmpty()) {
            return Collections.emptyList();
        }

        Collections.shuffle(available, new Random());

        Map<String, Integer> assignedCounts = new LinkedHashMap<>();
        List<UserAssignment> assignments = new ArrayList<>();

        int idx = 0;
        for (int i = 0; i < interactionCount; i++) {
            BotUser user = available.get(idx % available.size());
            assignedCounts.merge(user.id(), 1, Integer::sum);

            if (assignedCounts.get(user.id()) <= maxPerUser) {
                assignments.add(new UserAssignment(user.id(), 0));
            }

            idx++;
        }

        return assignments;
    }
}