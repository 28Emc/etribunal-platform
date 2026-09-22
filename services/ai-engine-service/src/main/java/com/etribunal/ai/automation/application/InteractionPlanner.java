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
            if (pi.type() == null) {
                errors.add(INTERACTION_PREFIX + i + ": missing type");
                continue;
            }

            switch (pi.type()) {
                case COMMENT -> {
                    if (pi.content() == null || pi.content().isBlank()) {
                        errors.add(INTERACTION_PREFIX + i + ": COMMENT requires content");
                    }
                }
                case REPLY -> {
                    if (pi.content() == null || pi.content().isBlank()) {
                        errors.add(INTERACTION_PREFIX + i + ": REPLY requires content");
                    }
                    if (pi.replyToIndex() == null || pi.replyToIndex() < 0 || pi.replyToIndex() >= i) {
                        errors.add(INTERACTION_PREFIX + i + ": REPLY requires valid replyToIndex < " + i);
                    }
                }
                case REACTION -> {
                    if (pi.reaction() == null || !List.of("LIKE", "LOVE", "ANGRY").contains(pi.reaction())) {
                        errors.add(INTERACTION_PREFIX + i + ": REACTION requires valid emoji (LIKE/LOVE/ANGRY)");
                    }
                }
                case VOTE -> {
                    if (pi.option() == null || !List.of("A", "B", "BOTH_WRONG").contains(pi.option())) {
                        errors.add(INTERACTION_PREFIX + i + ": VOTE requires valid option (A/B/BOTH_WRONG)");
                    }
                }
            }
        }
        return errors;
    }

    private InteractionPlan repairPlan(InteractionPlan plan) {
        List<PlannedInteraction> source = plan.interactions();
        List<PlannedInteraction> repaired = new ArrayList<>();
        List<Integer> keptOriginalIndexes = new ArrayList<>();

        for (int i = 0; i < source.size(); i++) {
            PlannedInteraction pi = source.get(i);
            // REPLY sin contenido no es reparable: se elimina.
            if (pi.type() == AutomationInteractionType.REPLY
                    && (pi.content() == null || pi.content().isBlank())) {
                continue;
            }
            PlannedInteraction out = pi;
            // REPLY apuntando hacia adelante se re-dirige al COMMENT más cercano previo;
            // si no hay ninguno al que anclar, no es reparable y se elimina.
            if (pi.type() == AutomationInteractionType.REPLY
                    && pi.replyToIndex() != null && pi.replyToIndex() >= i) {
                int newTarget = findNearestCommentIndex(source, i);
                if (newTarget < 0) {
                    continue;
                }
                out = new PlannedInteraction(
                        pi.type(), pi.stance(), pi.tone(), pi.content(), pi.reaction(), pi.option(),
                        newTarget);
            }
            repaired.add(out);
            keptOriginalIndexes.add(i);
        }

        // Si hubo remociones, los replyToIndex originales ya no apuntan bien:
        // re-indexar contra la posición real de cada padre (o re-dirigir si el
        // padre fue removido).
        if (keptOriginalIndexes.size() != source.size()) {
            Set<Integer> removedIndexes = new HashSet<>();
            for (int i = 0; i < source.size(); i++) {
                if (!keptOriginalIndexes.contains(i)) {
                    removedIndexes.add(i);
                }
            }
            List<PlannedInteraction> remapped = new ArrayList<>();
            for (int j = 0; j < repaired.size(); j++) {
                PlannedInteraction pi = repaired.get(j);
                if (pi.type() != AutomationInteractionType.REPLY || pi.replyToIndex() == null) {
                    remapped.add(pi);
                    continue;
                }
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
                remapped.add(new PlannedInteraction(
                        pi.type(), pi.stance(), pi.tone(), pi.content(), pi.reaction(), pi.option(), newTarget));
            }
            return new InteractionPlan(remapped);
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
                // REPLY: responder distinto al autor del COMMENT padre (evitar self-reply).
                if (pi.replyToIndex() != null && pi.replyToIndex() < result.size()) {
                    String parentUserId = result.get(pi.replyToIndex()).userId();
                    int attempts = 0;
                    while (attempts < available.size() * 2 && userId == null) {
                        UserSelector.BotUser candidate = available.get(userIndex % available.size());
                        int count = userCounts.getOrDefault(candidate.id(), 0);
                        if (!candidate.id().equals(parentUserId) && count < maxPerUser) {
                            userId = candidate.id();
                            userCounts.merge(userId, 1, Integer::sum);
                        }
                        userIndex++;
                        attempts++;
                    }
                    if (userId == null) {
                        // Respeta el cap eligiendo el usuario menos cargado distinto al padre;
                        // solo cae al padre como último recurso (nunca deja userId nulo).
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
                        userId = bestIdx >= 0 ? available.get(bestIdx).id() : parentUserId;
                        userCounts.merge(userId, 1, Integer::sum);
                        userIndex++;
                    }
                } else {
                    // REPLY sin padre válido (defensivo): asignar siguiente usuario round-robin.
                    int attempts = 0;
                    while (attempts < available.size() && userId == null) {
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
                    if (userId == null && !available.isEmpty()) {
                        userId = available.get(userIndex % available.size()).id();
                        userCounts.merge(userId, 1, Integer::sum);
                        userIndex++;
                    }
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