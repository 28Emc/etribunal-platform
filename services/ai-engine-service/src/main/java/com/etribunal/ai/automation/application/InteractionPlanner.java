package com.etribunal.ai.automation.application;

import com.etribunal.ai.automation.config.AutomationConfig;
import com.etribunal.ai.automation.domain.AIProvider;
import com.etribunal.ai.automation.domain.AutomationInteractionType;
import com.etribunal.ai.automation.domain.dtos.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class InteractionPlanner {

    private static final Logger log = LoggerFactory.getLogger(InteractionPlanner.class);
    private static final int MAX_VALIDATION_ATTEMPTS = 2;

    private final AIProvider aiProvider;
    private final AutomationConfig config;

    public InteractionPlanner(AIProvider aiProvider, AutomationConfig config) {
        this.aiProvider = aiProvider;
        this.config = config;
    }

    public record PlanResult(
        List<PlannedInteractionWithUser> interactions,
        InteractionPlan raw
    ) {}

    public record PlannedInteractionWithUser(
        int index,
        AutomationInteractionType type,
        String userId,
        String stance,
        Integer tone,
        String content,
        String reaction,
        String option,
        Integer replyToCommentIndex
    ) {}

    public Mono<PlanResult> generate(
            String caseId,
            String title,
            String sideAContent,
            String sideBContent,
            String category,
            int interactionCount,
            int intensity,
            List<UserSelector.BotUser> pool,
            String authorId,
            String sideBUserId,
            int maxPerUser
    ) {
        List<UserSelector.BotUser> available = pool.stream()
                .filter(u -> !u.id().equals(authorId))
                .filter(u -> !u.id().equals(sideBUserId))
                .toList();

        if (available.isEmpty()) {
            return Mono.just(new PlanResult(List.of(), new InteractionPlan(List.of())));
        }

        GenerateInteractionPlanInput input = new GenerateInteractionPlanInput(
                caseId, title, sideAContent, sideBContent, category,
                interactionCount, intensity, config.getLanguage(),
                available.size(), maxPerUser
        );

        return generateWithValidation(input, available, maxPerUser, 0);
    }

    private Mono<PlanResult> generateWithValidation(
            GenerateInteractionPlanInput input,
            List<UserSelector.BotUser> available,
            int maxPerUser,
            int attempt
    ) {
        return aiProvider.generateInteractionPlan(input)
                .flatMap(plan -> {
                    List<String> errors = validatePlan(plan);
                    if (errors.isEmpty()) {
                        return Mono.just(new PlanResult(mergeWithUsers(plan, available, maxPerUser), plan));
                    }

                    if (attempt < MAX_VALIDATION_ATTEMPTS) {
                        InteractionPlan repaired = repairPlan(plan);
                        List<String> repairErrors = validatePlan(repaired);
                        if (repairErrors.isEmpty()) {
                            return Mono.just(new PlanResult(mergeWithUsers(repaired, available, maxPerUser), repaired));
                        }
                    }

                    log.warn("Plan validation failed after {} attempts, retrying with fresh prompt", attempt + 1);
                    if (attempt < MAX_VALIDATION_ATTEMPTS) {
                        return generateWithValidation(input, available, maxPerUser, attempt + 1);
                    }

                    return Mono.just(new PlanResult(mergeWithUsers(plan, available, maxPerUser), plan));
                });
    }

    private List<String> validatePlan(InteractionPlan plan) {
        List<String> errors = new ArrayList<>();
        if (plan == null || plan.interactions() == null) {
            errors.add("Plan is null");
            return errors;
        }

        for (int i = 0; i < plan.interactions().size(); i++) {
            PlannedInteraction pi = plan.interactions().get(i);
            if (pi.type() == null) {
                errors.add("Interaction " + i + ": missing type");
                continue;
            }

            switch (pi.type()) {
                case COMMENT -> {
                    if (pi.content() == null || pi.content().isBlank()) {
                        errors.add("Interaction " + i + ": COMMENT requires content");
                    }
                }
                case REPLY -> {
                    if (pi.content() == null || pi.content().isBlank()) {
                        errors.add("Interaction " + i + ": REPLY requires content");
                    }
                    if (pi.replyToIndex() == null || pi.replyToIndex() < 0 || pi.replyToIndex() >= i) {
                        errors.add("Interaction " + i + ": REPLY requires valid replyToIndex < " + i);
                    }
                }
                case REACTION -> {
                    if (pi.reaction() == null || !List.of("LIKE", "LOVE", "ANGRY").contains(pi.reaction())) {
                        errors.add("Interaction " + i + ": REACTION requires valid emoji (LIKE/LOVE/ANGRY)");
                    }
                }
                case VOTE -> {
                    if (pi.option() == null || !List.of("A", "B", "BOTH_WRONG").contains(pi.option())) {
                        errors.add("Interaction " + i + ": VOTE requires valid option (A/B/BOTH_WRONG)");
                    }
                }
            }
        }
        return errors;
    }

    private InteractionPlan repairPlan(InteractionPlan plan) {
        List<PlannedInteraction> repaired = new ArrayList<>();
        for (int i = 0; i < plan.interactions().size(); i++) {
            PlannedInteraction pi = plan.interactions().get(i);
            if (pi.type() == AutomationInteractionType.REPLY && pi.replyToIndex() != null && pi.replyToIndex() >= i) {
                int newTarget = findNearestCommentIndex(plan.interactions(), i);
                if (newTarget >= 0) {
                    repaired.add(new PlannedInteraction(pi.type(), pi.stance(), pi.tone(), pi.content(), pi.reaction(), pi.option(), newTarget));
                }
            } else if (pi.type() == AutomationInteractionType.REPLY && (pi.content() == null || pi.content().isBlank())) {
                // Remove unrepairable REPLY
            } else {
                repaired.add(pi);
            }
        }
        return new InteractionPlan(repaired);
    }

    private int findNearestCommentIndex(List<PlannedInteraction> interactions, int beforeIndex) {
        for (int i = beforeIndex - 1; i >= 0; i--) {
            if (interactions.get(i).type() == AutomationInteractionType.COMMENT) {
                return i;
            }
        }
        return -1;
    }

    private List<PlannedInteractionWithUser> mergeWithUsers(
            InteractionPlan plan,
            List<UserSelector.BotUser> available,
            int maxPerUser
    ) {
        Map<String, Integer> userCounts = new HashMap<>();
        List<PlannedInteractionWithUser> result = new ArrayList<>();
        int userIndex = 0;

        for (int i = 0; i < plan.interactions().size(); i++) {
            PlannedInteraction pi = plan.interactions().get(i);
            String userId = null;

            if (pi.type() != AutomationInteractionType.REPLY) {
                // Assign user with round-robin and max-per-user cap
                int attempts = 0;
                while (attempts < available.size()) {
                    UserSelector.BotUser candidate = available.get(userIndex % available.size());
                    int count = userCounts.getOrDefault(candidate.id(), 0);
                    if (count < maxPerUser) {
                        userId = candidate.id();
                        userCounts.merge(userId, 1, Integer::sum);
                        userIndex++;
                        break;
                    }
                    userIndex++;
                    attempts++;
                }
            } else {
                // REPLY uses same user as the parent COMMENT
                if (pi.replyToIndex() != null && pi.replyToIndex() < result.size()) {
                    userId = result.get(pi.replyToIndex()).userId();
                }
            }

            result.add(new PlannedInteractionWithUser(
                    i, pi.type(), userId, pi.stance(), pi.tone(),
                    pi.content(), pi.reaction(), pi.option(), pi.replyToIndex()
            ));
        }

        return result;
    }
}