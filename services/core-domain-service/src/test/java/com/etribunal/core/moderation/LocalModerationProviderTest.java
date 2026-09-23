package com.etribunal.core.moderation;

import static org.assertj.core.api.Assertions.assertThat;

import com.etribunal.core.cases.ModerationStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

class LocalModerationProviderTest {

    private LocalModerationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new LocalModerationProvider();
        ReflectionTestUtils.setField(provider, "dictionaryResource",
                new ClassPathResource("moderation/moderation-dictionaries.json"));
        ReflectionTestUtils.setField(provider, "minRiskScore", 0.5);
        provider.init();
    }

    @Test
    void moderateText_flagsProfanityFromDictionary() {
        Mono<ModerationResult> result = provider.moderateText("esto es mierda, joder");
        ModerationResult res = result.block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.FLAGGED);
        assertThat(res.matchedRules()).contains("dict:profanity:mierda");
        assertThat(res.riskScore()).isEqualTo(0.5);
    }

    @Test
    void moderateText_flagsUrlShortener() {
        ReflectionTestUtils.setField(provider, "minRiskScore", 0.1);
        ModerationResult res = provider.moderateText("visita https://bit.ly/xyz ahora").block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.FLAGGED);
        assertThat(res.matchedRules()).anyMatch(r -> r.startsWith("regex:"));
    }

    @Test
    void moderateText_reportsMapperForApprovedContent() {
        ModerationResult res = provider.moderateText("un comentario completamente normal").block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(res.metadata()).containsKey("normalized_length");
        assertThat(res.metadata()).containsKey("original_length");
    }

    @Test
    void moderateText_returnsApprovedForBlankText() {
        ModerationResult res = provider.moderateText("   ").block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.APPROVED);
        assertThat(res.riskScore()).isEqualTo(0.0);
    }

    @Test
    void moderateImage_usesRiskScoreOverMinForShortenerHost() {
        ReflectionTestUtils.setField(provider, "minRiskScore", 0.2);
        ModerationResult res = provider.moderateImage("http://bit.ly/abc").block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.FLAGGED);
        assertThat(res.matchedRules()).contains("image:url_shortener");
    }

    @Test
    void moderateImage_approvesNullUrl() {
        ModerationResult res = provider.moderateImage(null).block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.APPROVED);
    }

    @Test
    void moderateImage_flagsInvalidUrl() {
        ReflectionTestUtils.setField(provider, "minRiskScore", 0.05);
        ModerationResult res = provider.moderateImage("not-a-url").block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.FLAGGED);
        assertThat(res.matchedRules()).contains("image:invalid_url");
    }

    @Test
    void moderateText_doesNotFlagShortNormalText() {
        ModerationResult res = provider.moderateText("hola que tal").block();
        assertThat(res).isNotNull();
        assertThat(res.status()).isEqualTo(ModerationStatus.APPROVED);
    }
}