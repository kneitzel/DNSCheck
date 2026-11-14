package de.neitzel.dnscheck.service.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

/**
 * Deployment settings for the zone catalog, key files, the first operator, dry-run, and the reconciliation schedule.
 */
@ConfigMapping(prefix = "dnscheck")
public interface DnsCheckConfig {

    /**
     * Returns the zone catalog location.
     *
     * @return Settings for the directory scanned for owner folders and zone definitions.
     */
    Zones zones();

    /**
     * Returns the key-file location.
     *
     * @return Settings for the directory that holds TSIG key files under owner folders.
     */
    Keys keys();

    /**
     * Returns optional credentials used to create the first operator with the admin global right.
     *
     * @return Bootstrap username and password settings.
     */
    Bootstrap bootstrap();

    /**
     * Returns whether a reconciliation run detects drift without sending {@code nsupdate} commands.
     * The default is {@code false}.
     *
     * @return {@code true} when the service runs in dry-run mode.
     */
    @WithDefault("false")
    boolean dryRun();

    /**
     * Returns how often the service starts a reconciliation run.
     *
     * @return The schedule settings.
     */
    Schedule schedule();

  /**
   * Optional username and password for the operator created when the database has no operators.
   */
  interface Bootstrap {
    /**
     * Returns the optional username of the bootstrapped operator.
     *
     * @return The configured username, or empty when unset.
     */
    Optional<String> adminUsername();

    /**
     * Returns the optional initial password of the bootstrapped operator.
     * The value is secret material supplied by configuration and is stored only as a hash after bootstrap.
     *
     * @return The configured password, or empty when unset.
     */
    Optional<String> adminPassword();
  }

  /**
   * Location of the zone catalog.
   */
  interface Zones {
    /**
     * Returns the directory scanned for owner folders and zone definitions.
     * The default is {@code ./zones}.
     *
     * @return The zone catalog directory.
     */
    @WithDefault("./zones")
    String dir();
  }

  /**
   * Location of TSIG key files.
   */
  interface Keys {
    /**
     * Returns the directory that holds key files under owner folders.
     * The default is {@code ./keys}.
     *
     * @return The key-file directory.
     */
    @WithDefault("./keys")
    String dir();
  }

  /**
   * Interval between automatic reconciliation runs.
   */
  interface Schedule {
    /**
     * Returns how often an automatic reconciliation run starts.
     * The default is 15 minutes.
     *
     * @return The interval between runs.
     */
    @WithDefault("15m")
    Duration interval();
  }
}
