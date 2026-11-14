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

  /**
   * Picocli model for this command, used to print usage.
   */
  @CommandLine.Spec CommandLine.Model.CommandSpec spec;

  /**
   * Starts the CLI and exits with the picocli status code.
   *
   * @param args the command-line arguments
   */
  public static void main(String[] args) {
    int exitCode = new CommandLine(new DnsCheckCli()).execute(args);
    System.exit(exitCode);
  }

  /**
   * Prints usage when the operator does not choose a subcommand.
   */
  @Override
  public void run() {
    spec.commandLine().usage(spec.commandLine().getOut());
  }
}
