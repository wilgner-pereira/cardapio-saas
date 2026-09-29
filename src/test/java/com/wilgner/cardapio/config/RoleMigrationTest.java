package com.wilgner.cardapio.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class RoleMigrationTest {
    @Test
    void upgradesExistingRoleIdsAndMembershipsWithoutPromotingTenants() {
        String url = "jdbc:h2:mem:roles-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        var source = new DriverManagerDataSource(url, "sa", "");
        Flyway.configure().dataSource(source).target("9").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(source);
        // One platform-only account, one dual-role account, and unchanged tenant-only accounts.
        jdbc.update("delete from user_roles where user_id = (select id from usuario where username = 'restaurante')");
        jdbc.update("insert into user_roles (user_id, role_id) select u.id, r.id from usuario u cross join roles r "
                + "where u.username in ('restaurante', 'pizzaria') and r.role_name = 'ROLE_ADMIN'");
        var memberships = jdbc.queryForList("select user_id, role_id from user_roles order by user_id, role_id");
        Long platformId = jdbc.queryForObject("select id from roles where role_name = 'ROLE_ADMIN'", Long.class);
        Long tenantId = jdbc.queryForObject("select id from roles where role_name = 'ROLE_USER'", Long.class);

        Flyway.configure().dataSource(source).load().migrate();

        assertThat(jdbc.queryForList("select user_id, role_id from user_roles order by user_id, role_id")).isEqualTo(memberships);
        assertThat(jdbc.queryForObject("select role_name from roles where id = ?", String.class, platformId))
                .isEqualTo("ROLE_PLATFORM_ADMIN");
        assertThat(jdbc.queryForObject("select role_name from roles where id = ?", String.class, tenantId))
                .isEqualTo("ROLE_TENANT_ADMIN");
        assertThat(jdbc.queryForObject("select count(*) from roles where role_name in ('ROLE_ADMIN', 'ROLE_USER')", Integer.class)).isZero();
        assertThat(jdbc.queryForList("select r.role_name from usuario u join user_roles ur on u.id=ur.user_id "
                + "join roles r on r.id=ur.role_id where u.username='hamburgueria'", String.class))
                .containsExactly("ROLE_TENANT_ADMIN");
        jdbc.execute("SHUTDOWN");
    }
}
