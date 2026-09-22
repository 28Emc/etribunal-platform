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

@Service
public class InteractionPlanner {

    private static final Logger log = LoggerFactory.getLogger(InteractionPlanner.class);
    private static final int MAX_VALIDATION_ATTEMPTS = 2;
    private static final String INTERACTION_PREFIX = "Interaction ";

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

    public record PlanInput(
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
    ) {}

    public Mono<PlanResult> generate(PlanInput input) {
        List<UserSelector.BotUser> available = input.pool().stream()
                .filter(u -> !u.id().equals(input.authorId()))
                .filter(u -> !u.id().equals(input.sideBUserId()))
                .toList();

        if (available.isEmpty()) {
            return Mono.just(new PlanResult(List.of(), new InteractionPlan(List.of())));
        }

        GenerateInteractionPlanInput aiInput = new GenerateInteractionPlanInput(
                input.caseId(), input.title(), input.sideAContent(), input.sideBContent(), input.category(),
                input.interactionCount(), input.intensity(), config.getLanguage(),
                available.size(), input.maxPerUser()
        );

        return generateWithValidation(aiInput, available, input.maxPerUser(), 0);
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

                    InteractionPlan repaired = repairPlan(plan);
                    boolean repairedOk = validatePlan(repaired).isEmpty();

                    if (attempt < MAX_VALIDATION_ATTEMPTS) {
                        if (repairedOk) {
                            return Mono.just(new PlanResult(mergeWithUsers(repaired, available, maxPerUser), repaired));
                        }
                        log.warn("Plan validation failed after {} attempts, retrying with fresh prompt", attempt + 1);
                        return generateWithValidation(input, available, maxPerUser, attempt + 1);
                    }

                    // Fallback final: usar SIEMPRE el plan reparado (sin REPLYs rotos ni
                    // referencias inválidas), nunca el original inválido.
                    return Mono.just(new PlanResult(mergeWithUsers(repaired, available, maxPerUser), repaired));
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
            validateInteraction(pi, i, errors);
        }
        return errors;
    }

    private void validateInteraction(PlannedInteraction pi, int i, List<String> errors) {
        if (pi.type() == null) {
            errors.add(INTERACTION_PREFIX + i + ": missing type");
            return;
        }

        switch (pi.type()) {
            case COMMENT -> validateComment(pi, i, errors);
            case REPLY -> validateReply(pi, i, errors);
            case REACTION -> validateReaction(pi, i, errors);
            case VOTE -> validateVote(pi, i, errors);
        }
    }

    private static void validateComment(PlannedInteraction pi, int i, List<String> errors) {
        if (pi.content() == null || pi.content().isBlank()) {
            errors.add(INTERACTION_PREFIX + i + ": COMMENT requires content");
        }
    }

    private static void validateReply(PlannedInteraction pi, int i, List<String> errors) {
        if (pi.content() == null || pi.content().isBlank()) {
            errors.add(INTERACTION_PREFIX + i + ": REPLY requires content");
        }
        if (pi.replyToIndex() == null || pi.replyToIndex() < 0 || pi.replyToIndex() >= i) {
            errors.add(INTERACTION_PREFIX + i + ": REPLY requires valid replyToIndex < " + i);
        }
    }

    private static void validateReaction(PlannedInteraction pi, int i, List<String> errors) {
        if (pi.reaction() == null || !List.of("LIKE", "LOVE", "ANGRY").contains(pi.reaction())) {
            errors.add(INTERACTION_PREFIX + i + ": REACTION requires valid emoji (LIKE/LOVE/ANGRY)");
        }
    }

    private static void validateVote(PlannedInteraction pi, int i, List<String> errors) {
        if (pi.option() == null || !List.of("A", "B", "BOTH_WRONG").contains(pi.option())) {
            errors.add(INTERACTION_PREFIX + i + ": VOTE requires valid option (A/B/BOTH_WRONG)");
        }
    }

    private InteractionPlan repairPlan(InteractionPlan plan) {
        List<PlannedInteraction> source = plan.interactions();
        List<PlannedInteraction> repaired = new ArrayList<>();
        List<Integer> keptOriginalIndexes = new ArrayList<>();

        for (int i = 0; i < source.size(); i++) {
            PlannedInteraction pi = repairOrNull(source, i);
            if (pi == null) {
                continue;
            }
            repaired.add(pi);
            keptOriginalIndexes.add(i);
        }

        // Si hubo remociones, los replyToIndex originales ya no apuntan bien:
        // re-indexar contra la posición real de cada padre (o re-dirigir si el
        // padre fue removido).
        if (keptOriginalIndexes.size() != source.size()) {
            return new InteractionPlan(remapReplyTargets(source, repaired, keptOriginalIndexes));
        }

        return new InteractionPlan(repaired);
    }

    private PlannedInteraction repairOrNull(List<PlannedInteraction> source, int i) {
        PlannedInteraction pi = source.get(i);
        // REPLY sin contenido no es reparable: se elimina.
        if (pi.type() == AutomationInteractionType.REPLY
                && (pi.content() == null || pi.content().isBlank())) {
            return null;
        }
        // REPLY apuntando hacia adelante se re-dirige al COMMENT más cercano previo;
        // si no hay ninguno al que anclar, no es reparable y se elimina.
        if (pi.type() == AutomationInteractionType.REPLY
                && pi.replyToIndex() != null && pi.replyToIndex() >= i) {
            int newTarget = findNearestCommentIndex(source, i);
            if (newTarget < 0) {
                return null;
            }
            return new PlannedInteraction(
                    pi.type(), pi.stance(), pi.tone(), pi.content(), pi.reaction(), pi.option(),
                    newTarget);
        }
        return pi;
    }

    private List<PlannedInteraction> remapReplyTargets(
            List<PlannedInteraction> source,
            List<PlannedInteraction> repaired,
            List<Integer> keptOriginalIndexes
    ) {
        Set<Integer> removedIndexes = computeRemovedIndexes(source, keptOriginalIndexes);
        List<PlannedInteraction> remapped = new ArrayList<>();
        for (int j = 0; j < repaired.size(); j++) {
            PlannedInteraction pi = repaired.get(j);
            if (pi.type() != AutomationInteractionType.REPLY || pi.replyToIndex() == null) {
                remapped.add(pi);
                continue;
            }
            remapped.add(remapReply(pi, removedIndexes, remapped, j));
        }
        return remapped;
    }

    private Set<Integer> computeRemovedIndexes(List<PlannedInteraction> source, List<Integer> kept) {
        Set<Integer> removed = new HashSet<>();
        for (int i = 0; i < source.size(); i++) {
            if (!kept.contains(i)) {
                removed.add(i);
            }
        }
        return removed;
    }

    private PlannedInteraction remapReply(
            PlannedInteraction pi,
            Set<Integer> removedIndexes,
            List<PlannedInteraction> remapped,
            int j
    ) {
        int originalTarget = pi.replyToIndex();
        int removedBeforeTarget = 0;
        for (int removedIdx : removedIndexes) {
            if (removedIdx < originalTarget) {
                removedBeforeTarget++;
            }
        }
        int adjusted = originalTarget - removedBeforeTarget;
        Integer newTarget = null;
        if (adjusted >= 0 && adjusted < j && !removedIndexes.contains(originalTarget)) {
            newTarget = adjusted;
        } else {
            int nearest = findNearestCommentIndex(remapped, j);
            if (nearest >= 0) {
                newTarget = nearest;
            }
        }
        return new PlannedInteraction(
                pi.type(), pi.stance(), pi.tone(), pi.content(), pi.reaction(), pi.option(), newTarget);
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
            UserPick pick = pi.type() != AutomationInteractionType.REPLY
                    ? pickCommentUser(available, userCounts, maxPerUser, userIndex)
                    : pickReplyUser(pi, available, userCounts, maxPerUser, userIndex, result);
            userIndex = pick.nextUserIndex();

            result.add(new PlannedInteractionWithUser(
                    i, pi.type(), pick.userId(), pi.stance(), pi.tone(),
                    pi.content(), pi.reaction(), pi.option(), pi.replyToIndex()
            ));
        }

        return result;
    }

    private UserPick pickCommentUser(
            List<UserSelector.BotUser> available,
            Map<String, Integer> userCounts,
            int maxPerUser,
            int userIndex
    ) {
        for (int attempts = 0; attempts < available.size(); attempts++) {
            UserSelector.BotUser candidate = available.get(userIndex % available.size());
            int count = userCounts.getOrDefault(candidate.id(), 0);
            if (count < maxPerUser) {
                userCounts.merge(candidate.id(), 1, Integer::sum);
                return new UserPick(candidate.id(), userIndex + 1);
            }
            userIndex++;
        }
        return new UserPick(null, userIndex);
    }

    private UserPick pickReplyUser(
            PlannedInteraction pi,
            List<UserSelector.BotUser> available,
            Map<String, Integer> userCounts,
            int maxPerUser,
            int userIndex,
            List<PlannedInteractionWithUser> result
    ) {
        if (pi.replyToIndex() == null || pi.replyToIndex() >= result.size()) {
            return pickFallbackUser(available, userCounts, maxPerUser, userIndex);
        }
        String parentUserId = result.get(pi.replyToIndex()).userId();
        for (int attempts = 0; attempts < available.size() * 2; attempts++) {
            UserSelector.BotUser candidate = available.get(userIndex % available.size());
            int count = userCounts.getOrDefault(candidate.id(), 0);
            if (!candidate.id().equals(parentUserId) && count < maxPerUser) {
                userCounts.merge(candidate.id(), 1, Integer::sum);
                return new UserPick(candidate.id(), userIndex + 1);
            }
            userIndex++;
        }
        return new UserPick(pickLeastLoadedDistinct(available, userCounts, parentUserId), userIndex + 1);
    }

    private String pickLeastLoadedDistinct(
            List<UserSelector.BotUser> available,
            Map<String, Integer> userCounts,
            String parentUserId
    ) {
        int bestIdx = -1;
        int bestCount = Integer.MAX_VALUE;
        for (int k = 0; k < available.size(); k++) {
            UserSelector.BotUser candidate = available.get(k);
            if (candidate.id().equals(parentUserId)) {
                continue;
            }
            int count = userCounts.getOrDefault(candidate.id(), 0);
            if (count < bestCount) {
                bestCount = count;
                bestIdx = k;
            }
        }
        String chosen = bestIdx >= 0 ? available.get(bestIdx).id() : parentUserId;
        userCounts.merge(chosen, 1, Integer::sum);
        return chosen;
    }

    private UserPick pickFallbackUser(
            List<UserSelector.BotUser> available,
            Map<String, Integer> userCounts,
            int maxPerUser,
            int userIndex
    ) {
        for (int attempts = 0; attempts < available.size(); attempts++) {
            UserSelector.BotUser candidate = available.get(userIndex % available.size());
            int count = userCounts.getOrDefault(candidate.id(), 0);
            if (count < maxPerUser) {
                userCounts.merge(candidate.id(), 1, Integer::sum);
                return new UserPick(candidate.id(), userIndex + 1);
            }
            userIndex++;
        }
        if (!available.isEmpty()) {
            String forced = available.get(userIndex % available.size()).id();
            userCounts.merge(forced, 1, Integer::sum);
            return new UserPick(forced, userIndex + 1);
        }
        return new UserPick(null, userIndex);
    }

    private record UserPick(String userId, int nextUserIndex) {}
}