package de.neitzel.dnscheck.service.auth;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Test profile that runs DNSCheck against an in-memory HSQL database.
 */
public class HsqlTestProfile implements QuarkusTestProfile {

    /**
     * Returns the HSQL datasource, disabled schedule, and bootstrap operator credentials.
     *
     * @return the configuration overrides for this profile.
     */
    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.datasource.db-kind", "other",
                "quarkus.datasource.jdbc.driver", "org.hsqldb.jdbc.JDBCDriver",
                "quarkus.datasource.jdbc.url", "jdbc:hsql:mem:dnscheck-hsql-test;DB_CLOSE_DELAY=-1",
                "quarkus.datasource.username", "sa",
                "quarkus.datasource.password", "",
                "quarkus.scheduler.enabled", "false",
                "dnscheck.bootstrap.admin-username", "admin",
                "dnscheck.bootstrap.admin-password", "admin");
    }
}
