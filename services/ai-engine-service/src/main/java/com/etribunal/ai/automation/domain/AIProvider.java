package com.etribunal.ai.automation.domain;

import com.etribunal.ai.automation.domain.dtos.*;
import reactor.core.publisher.Mono;

public interface AIProvider {

    Mono<GeneratedCase> generateCase(GenerateCaseInput input);

    Mono<InteractionPlan> generateInteractionPlan(GenerateInteractionPlanInput input);

    Mono<GeneratedComment> generateComment(GenerateCommentInput input);

    Mono<GeneratedReply> generateReply(GenerateReplyInput input);
}