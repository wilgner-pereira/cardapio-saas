package com.wilgner.cardapio.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class StorageMigrationTest {
    @Test void upgradePreservesDataAndRequiresInventoryIncludingUnlinkedLegacyObjects() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:storage-migration-" + UUID.randomUUID()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        Flyway.configure().dataSource(source).target("12").load().migrate();
        var jdbc = new JdbcTemplate(source);
        String url = "https://example.supabase.co/storage/v1/object/public/images/old.png";
        jdbc.update("update estabelecimento set logo_url=? where slug='restaurante'", url);
        jdbc.update("update produtos set imagem_url=? where estabelecimento_id=(select id from estabelecimento where slug='restaurante')", url);
        var products = jdbc.queryForList("select * from produtos order by id");
        Flyway.configure().dataSource(source).target("13").load().migrate();
        assertThat(jdbc.queryForList("select * from produtos order by id")).isEqualTo(products);
        assertThat(jdbc.queryForObject("select count(*) from estabelecimento where storage_inventory_required=true", Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from storage_object where public_url=? and status='LEGACY' and size_bytes is null", Integer.class, url)).isEqualTo(1);
        jdbc.execute("SHUTDOWN");
    }
}
