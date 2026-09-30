package com.wilgner.cardapio.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class CategoryOrderMigrationTest {
    @Test void upgradeRepairsLegacyDuplicatesAndKeepsInternalProductOrder() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:category-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).target("13").load().migrate();
        var jdbc = new JdbcTemplate(source);
        jdbc.update("UPDATE produtos SET categoria_ordem = 17");
        var original = jdbc.queryForList("SELECT id, categoria, ordem FROM produtos ORDER BY id");
        assertThat(original).isNotEmpty();
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("SELECT id, categoria, ordem FROM produtos ORDER BY id")).isEqualTo(original);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT estabelecimento_id, categoria_ordem FROM produtos GROUP BY estabelecimento_id, categoria_ordem HAVING COUNT(DISTINCT categoria) > 1) inconsistent", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM (SELECT estabelecimento_id, categoria FROM produtos GROUP BY estabelecimento_id, categoria HAVING MIN(categoria_ordem) <> MAX(categoria_ordem)) inconsistent", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM produtos WHERE categoria_ordem IS NULL", Integer.class)).isZero();
        jdbc.execute("SHUTDOWN");
    }
}
