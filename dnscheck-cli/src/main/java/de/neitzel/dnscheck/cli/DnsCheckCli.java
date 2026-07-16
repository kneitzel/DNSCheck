package de.neitzel.dnscheck.cli;

import de.neitzel.dnscheck.cli.command.ReconcileCommand;
import picocli.CommandLine;

/**
 * DNSCheck command-line entry point.
 */
@CommandLine.Command(
        name = "dnscheck",
        mixinStandardHelpOptions = true,
        version = "dnscheck 0.1.0-SNAPSHOT",
        description = "DNS zone reconciliation and enforcement",
        subcommands = {ReconcileCommand.class})
public final class DnsCheckCli implements Runnable {

  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  public static void main(String[] args) {
    int exitCode = new CommandLine(new DnsCheckCli()).execute(args);
    System.exit(exitCode);
  }

  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }
}
