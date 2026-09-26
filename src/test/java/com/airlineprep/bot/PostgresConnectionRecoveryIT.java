package com.airlineprep.bot;

import java.sql.DriverManager;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

/** Explicit opt-in, loopback-only fault injection against this test's own backend. */
class PostgresConnectionRecoveryIT {
    @Test void staleIdleConnectionIsReplacedAfterServerClosesIt() throws Exception {
        String host=System.getenv("DB_HOST");
        assertThat(host).as("Recovery fault injection requires a disposable loopback database").isIn("127.0.0.1","localhost");
        String database=System.getenv("DB_NAME");
        assertThat(database).startsWith("airline_phase9_");
        var config=new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://"+host+":"+System.getenv("DB_PORT")+"/"+database+"?sslmode=require");
        config.setUsername(System.getenv("DB_USERNAME"));config.setPassword(System.getenv("DB_PASSWORD"));
        config.setMaximumPoolSize(1);config.setMinimumIdle(0);config.setConnectionTimeout(5000);
        config.setValidationTimeout(1000);config.setPoolName("LocalRecoveryProbe");
        try(var pool=new HikariDataSource(config)) {
            long oldPid;
            try(var connection=pool.getConnection();var query=connection.createStatement();var result=query.executeQuery("SELECT pg_backend_pid()")) {
                assertThat(result.next()).isTrue();oldPid=result.getLong(1);
            }
            try(var control=DriverManager.getConnection(config.getJdbcUrl(),config.getUsername(),config.getPassword());
                    var terminate=control.prepareStatement("SELECT pg_terminate_backend(?)")) {
                terminate.setInt(1,Math.toIntExact(oldPid));
                try(var result=terminate.executeQuery()) { assertThat(result.next()).isTrue();assertThat(result.getBoolean(1)).isTrue(); }
            }
            // Hikari deliberately bypasses validation for connections returned very recently.
            Thread.sleep(750);
            try(var connection=pool.getConnection();var query=connection.createStatement();var result=query.executeQuery("SELECT pg_backend_pid()")) {
                assertThat(result.next()).isTrue();assertThat(result.getLong(1)).isNotEqualTo(oldPid);
            }
        }
    }
}
