package com.banking.account_service.infrastructure.config;

import com.banking.account_service.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The client imports its database location and the product terms from Config Server and its
 * database credentials from Vault, both through spring.config.import, and migrates
 * accounts_db with Flyway over JDBC.
 */
class ConfigServerClientIntegrationTest extends PostgresIntegrationTest {

    @Autowired
    private Environment environment;

    @Autowired
    private AccountProperties properties;

    @Test
    void importsDatabaseAndTermsFromConfigServer() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("account-service");
        assertThat(CONFIG_SERVER.requestedPaths()).contains("/account-service/default");
        assertThat(environment.getProperty("spring.r2dbc.url")).isEqualTo(r2dbcUrl());
        assertThat(properties.businessZone()).isEqualTo(ZoneId.of("America/Lima"));
        assertThat(properties.products().vipSavings().minimumDailyAverageBalance())
                .containsEntry(com.banking.account_service.domain.model.CurrencyCode.PEN, new BigDecimal("1000.00"));
        assertThat(properties.integration().timeout()).isEqualTo(Duration.ofSeconds(2));
        assertThat(properties.integration().gatewayBaseUrl()).hasToString(GATEWAY.url());
    }

    /** The database user and password come from Vault; the Config Server never serves them. */
    @Test
    void readsTheDatabaseCredentialsFromVault() {
        for (String key : java.util.List.of("spring.r2dbc.username", "spring.r2dbc.password",
                "spring.flyway.user", "spring.flyway.password")) {
            assertThat(sourceOf(key)).as(key).contains("secret/account-service");
        }
        assertThat(sourceOf("spring.r2dbc.url")).contains("configserver");
        assertThat(sourceOf("spring.flyway.url")).contains("configserver");
        assertThat(environment.getProperty("spring.r2dbc.password")).isEqualTo(POSTGRES.getPassword());
    }

    /** Name of the first (winning) property source that defines {@code key}. */
    private String sourceOf(String key) {
        return ((ConfigurableEnvironment) environment).getPropertySources().stream()
                // Boot's view over all the other sources, not a source of its own.
                .filter(source -> !"configurationProperties".equals(source.getName()))
                .filter(source -> source.containsProperty(key))
                .map(PropertySource::getName)
                .findFirst()
                .orElse("none");
    }

    @Test
    void flywayCreatedTheSchemaInAccountsDatabase() {
        assertThat(databaseClient.sql("SELECT current_database()").map(row -> row.get(0, String.class)).one().block())
                .isEqualTo(DATABASE);
        assertThat(count("SELECT COUNT(*) FROM flyway_schema_history WHERE success", java.util.Map.of()))
                .isEqualTo(1);
        assertThat(count("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = 'public'
                   AND table_name IN ('accounts', 'account_holders', 'account_signers', 'operations', 'account_movements')
                """, java.util.Map.of())).isEqualTo(5);
    }
}
