package com.banking.account_service.support;

import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountBalanceResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.AccountResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.MovementPageResponse;
import com.banking.account_service.infrastructure.adapter.in.rest.generated.model.OperationResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.cloud.circuitbreaker.resilience4j.ReactiveResilience4JCircuitBreakerFactory;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the whole service on a random port against a real PostgreSQL in Docker.
 *
 * <p>The application keeps its real {@code spring.config.import}. A stub Config Server serves
 * the product terms of {@code stub-config/account-service.yml}, the R2DBC and Flyway
 * locations of the container and the URL of a stub Gateway that plays Customer Service and
 * Credit Service; a real Vault holds the database user and password, which the Config
 * Server never serves. All of them start once, before any Spring context.
 *
 * <p>Every test starts with empty tables, a fresh Gateway, closed circuit breakers and
 * the clock at {@link MutableClockConfiguration#START}, and ends with
 * {@link LedgerAssertions#assertConsistent}: balances, movements and operations agree.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@Import(MutableClockConfiguration.class)
public abstract class PostgresIntegrationTest {

    protected static final String DATABASE = "accounts_db";
    protected static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:16-alpine").withDatabaseName(DATABASE);
    protected static final StubGateway GATEWAY;
    protected static final StubConfigServer CONFIG_SERVER;
    protected static final TestVault VAULT;

    static {
        POSTGRES.start();
        GATEWAY = StubGateway.start();
        // Read by application.yaml through VAULT_URI and VAULT_APPROLE_FILE.
        VAULT = TestVault.start("account-service", Map.of(
                "spring.r2dbc.username", POSTGRES.getUsername(),
                "spring.r2dbc.password", POSTGRES.getPassword(),
                "spring.flyway.user", POSTGRES.getUsername(),
                "spring.flyway.password", POSTGRES.getPassword()));
        Map<String, String> properties = new HashMap<>(yaml("stub-config/account-service.yml"));
        properties.put("spring.r2dbc.url", r2dbcUrl());
        properties.put("spring.flyway.url", POSTGRES.getJdbcUrl());
        properties.put("account.integration.gateway-base-url", GATEWAY.url());
        CONFIG_SERVER = StubConfigServer.start(properties);
        // Read by application.yaml: configserver:${CONFIG_SERVER_URL:http://localhost:8888}
        System.setProperty("CONFIG_SERVER_URL", CONFIG_SERVER.url());
    }

    @Autowired
    protected WebTestClient webTestClient;

    @Autowired
    protected DatabaseClient databaseClient;

    @Autowired
    protected MutableClock clock;

    @Autowired
    private ReactiveResilience4JCircuitBreakerFactory circuitBreakers;

    @BeforeEach
    void startClean() {
        databaseClient.sql("TRUNCATE account_movements, operations, account_holders, account_signers, accounts")
                .then()
                .block();
        GATEWAY.reset();
        clock.set(MutableClockConfiguration.START);
        circuitBreakers.getCircuitBreakerRegistry().getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        webTestClient = webTestClient.mutate().responseTimeout(Duration.ofSeconds(15)).build();
    }

    @AfterEach
    void ledgerStaysConsistent() {
        LedgerAssertions.assertConsistent(databaseClient);
    }

    protected static String r2dbcUrl() {
        return "r2dbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + DATABASE;
    }

    // ----- customers in the stub Gateway

    protected UUID personalCustomer() {
        return GATEWAY.addCustomer("PERSONAL", "STANDARD", true);
    }

    protected UUID vipCustomer() {
        return GATEWAY.addCustomer("PERSONAL", "VIP", true);
    }

    protected UUID businessCustomer(String profile) {
        return GATEWAY.addCustomer("BUSINESS", profile, true);
    }

    // ----- HTTP helpers

    protected WebTestClient.ResponseSpec post(String uri, Object body) {
        return webTestClient.post().uri(uri).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    protected WebTestClient.ResponseSpec openRequest(String product, String currency, UUID holder, String deposit) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("product", product);
        body.put("currency", currency);
        body.put("primaryHolderId", holder);
        body.put("initialDeposit", new BigDecimal(deposit));
        return post("/accounts", body);
    }

    protected AccountResponse open(String product, String currency, UUID holder, String deposit) {
        return openRequest(product, currency, holder, deposit)
                .expectStatus().isCreated()
                .expectBody(AccountResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected AccountResponse openSavings(UUID holder, String deposit) {
        return open("SAVINGS", "PEN", holder, deposit);
    }

    protected WebTestClient.ResponseSpec depositRequest(UUID accountId, UUID operationId, String amount) {
        return post("/accounts/" + accountId + "/deposits", Map.of("operationId", operationId,
                "amount", new BigDecimal(amount)));
    }

    protected WebTestClient.ResponseSpec withdrawRequest(UUID accountId, UUID operationId, String amount) {
        return post("/accounts/" + accountId + "/withdrawals", Map.of("operationId", operationId,
                "amount", new BigDecimal(amount)));
    }

    protected WebTestClient.ResponseSpec transferRequest(UUID source, UUID operationId, UUID destination, String amount) {
        return post("/accounts/" + source + "/transfers", Map.of("operationId", operationId,
                "destinationAccountId", destination, "amount", new BigDecimal(amount)));
    }

    protected OperationResponse deposit(UUID accountId, String amount) {
        return created(depositRequest(accountId, UUID.randomUUID(), amount));
    }

    protected OperationResponse withdraw(UUID accountId, String amount) {
        return created(withdrawRequest(accountId, UUID.randomUUID(), amount));
    }

    protected OperationResponse transfer(UUID source, UUID destination, String amount) {
        return created(transferRequest(source, UUID.randomUUID(), destination, amount));
    }

    protected static OperationResponse created(WebTestClient.ResponseSpec response) {
        return response.expectStatus().isCreated()
                .expectHeader().doesNotExist("Idempotent-Replayed")
                .expectBody(OperationResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected static void assertRuleViolation(WebTestClient.ResponseSpec response, String code) {
        response.expectStatus().isEqualTo(422).expectBody().jsonPath("$.code").isEqualTo(code);
    }

    protected AccountBalanceResponse balance(UUID accountId) {
        return webTestClient.get().uri("/accounts/{id}/balance", accountId).exchange()
                .expectStatus().isOk()
                .expectBody(AccountBalanceResponse.class)
                .returnResult()
                .getResponseBody();
    }

    protected MovementPageResponse movements(UUID accountId) {
        return webTestClient.get().uri("/accounts/{id}/movements?size=100", accountId).exchange()
                .expectStatus().isOk()
                .expectBody(MovementPageResponse.class)
                .returnResult()
                .getResponseBody();
    }

    // ----- direct database reads, independent of the API

    protected BigDecimal storedBalance(UUID accountId) {
        return databaseClient.sql("SELECT balance FROM accounts WHERE id = :id")
                .bind("id", accountId)
                .map(row -> row.get("balance", BigDecimal.class))
                .one()
                .block();
    }

    protected long count(String sql, Map<String, Object> binds) {
        DatabaseClient.GenericExecuteSpec spec = databaseClient.sql(sql);
        for (Map.Entry<String, Object> bind : binds.entrySet()) {
            spec = spec.bind(bind.getKey(), bind.getValue());
        }
        return spec.map(row -> row.get(0, Long.class)).one().block();
    }

    protected long movementCount(UUID accountId) {
        return count("SELECT COUNT(*) FROM account_movements WHERE account_id = :id", Map.of("id", accountId));
    }

    protected long operationCount() {
        return count("SELECT COUNT(*) FROM operations", Map.of());
    }

    protected static void assertMoney(BigDecimal actual, String expected) {
        assertThat(actual).isEqualByComparingTo(expected);
    }

    protected static List<UUID> ids(AccountResponse... accounts) {
        return java.util.Arrays.stream(accounts).map(AccountResponse::getId).toList();
    }

    private static Map<String, String> yaml(String resource) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(resource));
        Properties properties = factory.getObject();
        Map<String, String> values = new HashMap<>();
        properties.forEach((key, value) -> values.put(key.toString(), value.toString()));
        return values;
    }
}
