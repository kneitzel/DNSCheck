package de.neitzel.dnscheck.service.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "dnscheck")
public interface DnsCheckConfig {

    Zones zones();

    Keys keys();

    Bootstrap bootstrap();

    @WithDefault("false")
    boolean dryRun();

    Schedule schedule();

  interface Bootstrap {
    Optional<String> adminUsername();

    Optional<String> adminPassword();
  }

  interface Zones {
    @WithDefault("./zones")
    String dir();
  }

  interface Keys {
    @WithDefault("./keys")
    String dir();
  }

  interface Schedule {
    @WithDefault("15m")
    Duration interval();
  }
}
