package com.wilgner.cardapio.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class HeroImageMigrationTest {
    @Test void keepsAnExistingCoverWhenTheColumnWasCreatedBeforeTheMigration() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:existing-hero-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).target("14").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.execute("ALTER TABLE estabelecimento ADD COLUMN hero_image_url VARCHAR(2048)");
        jdbc.update("UPDATE estabelecimento SET hero_image_url='https://example.com/cover.jpg'");
        var original = jdbc.queryForList("SELECT id, hero_image_url FROM estabelecimento ORDER BY id");
        assertThat(original).isNotEmpty();
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("SELECT id, hero_image_url FROM estabelecimento ORDER BY id")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE version='15' AND success", Integer.class)).isEqualTo(1);
        jdbc.execute("SHUTDOWN");
    }

    @Test void addsAnEmptyIndependentCoverForExistingEstablishments() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:hero-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).target("14").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("UPDATE estabelecimento SET logo_url='https://example.com/logo.png'");
        var original = jdbc.queryForList("SELECT id, nome, logo_url, tema FROM estabelecimento ORDER BY id");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("SELECT id, nome, logo_url, tema FROM estabelecimento ORDER BY id")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM estabelecimento WHERE hero_image_url IS NOT NULL", Integer.class)).isZero();
        jdbc.execute("SHUTDOWN");
    }
}
