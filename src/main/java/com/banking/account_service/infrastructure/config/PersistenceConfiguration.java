package com.banking.account_service.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.DialectResolver;

/**
 * R2DBC conversions. Enum columns are VARCHAR with CHECK constraints, not PostgreSQL enum
 * types. Entity inserts already write enums by name, but values in {@code Criteria} and
 * {@code Update} are converted to the property type and reach the driver as Java enums,
 * which it cannot encode. This writing converter sends every enum by name on all paths.
 */
@Configuration(proxyBeanMethods = false)
public class PersistenceConfiguration {

    @Bean
    R2dbcCustomConversions r2dbcCustomConversions(ConnectionFactory connectionFactory) {
        return R2dbcCustomConversions.of(DialectResolver.getDialect(connectionFactory), new EnumToNameConverter());
    }

    @WritingConverter
    static final class EnumToNameConverter implements Converter<Enum<?>, String> {

        @Override
        public String convert(Enum<?> source) {
            return source.name();
        }
    }
}
