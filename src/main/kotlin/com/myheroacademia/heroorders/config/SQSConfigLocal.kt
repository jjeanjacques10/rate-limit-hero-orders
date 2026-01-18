package com.myheroacademia.heroorders.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import io.awspring.cloud.sqs.config.SqsMessageListenerContainerFactory
import io.awspring.cloud.sqs.operations.SqsTemplate
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.SqsClient
import java.net.URI


@Configuration
class SQSConfigLocal {

    @Value("\${spring.cloud.aws.region.static}")
    private lateinit var region: String

    @Value("\${spring.cloud.aws.endpoint}")
    private lateinit var localEndpoint: String

    @Value("\${spring.cloud.aws.credentials.access-key:test}")
    private lateinit var accessKey: String

    @Value("\${spring.cloud.aws.credentials.secret-key:test}")
    private lateinit var secretKey: String

    @Bean
    fun sqsClient(): SqsClient {
        return SqsClient.builder()
            .endpointOverride(URI.create(localEndpoint))
            .credentialsProvider(
                StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(accessKey, secretKey)
                )
            )
            .region(Region.of(region))
            .build()
    }

    @Bean
    fun sqsTemplate(sqsAsyncClient: SqsAsyncClient): SqsTemplate {
        return SqsTemplate.builder()
            .sqsAsyncClient(sqsAsyncClient)
            .build()
    }

    @Bean
    fun defaultSqsListenerContainerFactory(
        sqsAsyncClient: SqsAsyncClient
    ): SqsMessageListenerContainerFactory<Any?> {
        return SqsMessageListenerContainerFactory
            .builder<Any?>()
            .sqsAsyncClient(sqsAsyncClient)
            .configure { options ->
                options.maxConcurrentMessages(3)
                options.maxMessagesPerPoll(3)
            }
            .build()
    }


}
