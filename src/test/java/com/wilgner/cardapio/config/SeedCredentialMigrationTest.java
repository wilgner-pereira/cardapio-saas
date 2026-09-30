package com.wilgner.cardapio.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class SeedCredentialMigrationTest {
    private DriverManagerDataSource source() {
        return new DriverManagerDataSource("jdbc:h2:mem:seed-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
    }

    @Test
    void newDatabaseDisablesAllFiveSeedPasswordsWithoutDeletingData() {
        var source = source();
        Flyway.configure().dataSource(source).target("10").load().migrate();
        var jdbc = new JdbcTemplate(source);
        var users = jdbc.queryForList("select id, username, email, estabelecimento_id from usuario order by id");
        var products = jdbc.queryForList("select * from produtos order by id");
        var memberships = jdbc.queryForList("select * from user_roles order by user_id, role_id");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("select password from usuario", String.class))
                .hasSize(5).containsOnly("!disabled-v4-seed");
        assertThat(jdbc.queryForList("select id, username, email, estabelecimento_id from usuario order by id")).isEqualTo(users);
        assertThat(jdbc.queryForList("select * from produtos order by id")).isEqualTo(products);
        assertThat(jdbc.queryForList("select * from user_roles order by user_id, role_id")).isEqualTo(memberships);
        assertThat(new BCryptPasswordEncoder().matches(UUID.randomUUID().toString(), "!disabled-v4-seed")).isFalse();
        jdbc.execute("SHUTDOWN");
    }

    @Test
    void existingDatabasePreservesChangedPasswordEmailUsernameAndTenant() {
        var source = source();
        Flyway.configure().dataSource(source).target("10").load().migrate();
        var jdbc = new JdbcTemplate(source);
        String changedPassword = new BCryptPasswordEncoder().encode(UUID.randomUUID().toString());
        jdbc.update("update usuario set password = ? where username = 'restaurante'", changedPassword);
        jdbc.update("update usuario set email = 'real-owner@example.test' where username = 'pizzaria'");
        jdbc.update("update usuario set username = 'real-hamburgueria' where username = 'hamburgueria'");
        jdbc.update("update usuario set estabelecimento_id = (select id from estabelecimento where slug='restaurante') where username='espetaria'");
        var preserved = jdbc.queryForList("select * from usuario where username <> 'bar' order by id");
        Flyway.configure().dataSource(source).load().migrate();
        assertThat(jdbc.queryForList("select * from usuario where username <> 'bar' order by id")).isEqualTo(preserved);
        assertThat(jdbc.queryForObject("select password from usuario where username='bar'", String.class)).isEqualTo("!disabled-v4-seed");
        assertThat(jdbc.queryForObject("select count(*) from usuario", Integer.class)).isEqualTo(5);
        jdbc.execute("SHUTDOWN");
    }
}
