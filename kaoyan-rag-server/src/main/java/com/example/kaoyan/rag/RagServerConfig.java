package com.example.kaoyan.rag;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RagServerConfig {

    @Value("${langchain4j.open-ai.embedding-model.base-url}")
    private String embeddingBaseUrl;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String embeddingApiKey;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String embeddingModelName;

    @Value("${langchain4j.open-ai.embedding-model.dimensions}")
    private int dimensions;

    @Value("${langchain4j.pgvector.host}")
    private String pgHost;

    @Value("${langchain4j.pgvector.port}")
    private int pgPort;

    @Value("${langchain4j.pgvector.database}")
    private String pgDatabase;

    @Value("${langchain4j.pgvector.user}")
    private String pgUser;

    @Value("${langchain4j.pgvector.password}")
    private String pgPassword;

    @Value("${langchain4j.pgvector.table}")
    private String pgTable;

    @Value("${langchain4j.pgvector.dimension}")
    private int pgDimension;

    @Bean
    public EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .modelName(embeddingModelName)
                .dimensions(dimensions)
                .build();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        return PgVectorEmbeddingStore.builder()
                .host(pgHost).port(pgPort).database(pgDatabase)
                .user(pgUser).password(pgPassword)
                .table(pgTable).dimension(pgDimension)
                .createTable(false).dropTableFirst(false)
                .build();
    }
}
