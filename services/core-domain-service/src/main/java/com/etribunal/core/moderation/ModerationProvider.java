package com.etribunal.core.moderation;

import reactor.core.publisher.Mono;

public interface ModerationProvider {
    Mono<ModerationResult> moderateText(String text);
    Mono<ModerationResult> moderateImage(String imageUrl);
}