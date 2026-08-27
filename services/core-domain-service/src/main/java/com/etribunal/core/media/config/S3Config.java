package com.etribunal.core.media.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
public class S3Config {

    @Value("${etribunal.s3.endpoint:}")
    private String endpoint;

    @Value("${etribunal.s3.region:us-east-1}")
    private String region;

    @Value("${etribunal.s3.access-key:}")
    private String accessKey;

    @Value("${etribunal.s3.secret-key:}")
    private String secretKey;

    @Value("${etribunal.s3.bucket:etribunal-media}")
    private String bucket;

    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
                .region(Region.of(region))
                .forcePathStyle(true);

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        if (accessKey != null && !accessKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }

        return builder.build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        var builder = S3Presigner.builder()
                .region(Region.of(region));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        if (accessKey != null && !accessKey.isBlank()) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey, secretKey)));
        }

        return builder.build();
    }

    @Bean
    public String s3Bucket() {
        return bucket;
    }
}