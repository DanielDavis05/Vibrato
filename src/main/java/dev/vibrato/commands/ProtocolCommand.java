package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.*;
import dev.vibrato.packetcoverage.*;
import dev.vibrato.protocol.scenarios.ClientProtocolScenarioTask;
import java.io.IOException;

/** Explicit diagnostics; packet coverage never follows from a declared target alone. */
public final class ProtocolCommand extends Command {
    public ProtocolCommand() throws CommandException {
        super("protocol", "Inspect and exercise protocol 767 coverage on an isolated fixture",
                new Arg(String.class, "report|verify|missing|list|run|fixture|suite|reset", "report", 0),
                new Arg(String.class, "scenario", "", 1));
    }
    @Override protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String verb = parser.get(String.class), target = parser.get(String.class);
        try {
            if (verb.equals("list")) {
                mod.log("Client protocol scenarios: " + String.join(", ", ClientProtocolScenarioTask.supportedActions().stream().sorted().toList()));
                mod.log("All 224 requested packets remain in the coverage report, including unobserved entries.");
            } else if (verb.equals("report") || verb.equals("verify") || verb.equals("missing")) {
                var report = ProtocolReports.current();
                var path = ProtocolReports.write();
                mod.log(ProtocolReports.summary(report) + ". Report: " + path);
                if (verb.equals("missing")) mod.log("Still unobserved: " + String.join(", ", report.packets().stream().filter(row -> !row.covered()).limit(12).map(row -> row.packet().state() + "/" + row.packet().name()).toList()));
                if (verb.equals("verify") && (ProtocolReports.covered(report) != report.packets().size()
                        || !report.unknownPackets().isEmpty() || !report.unknownWireEvidence().isEmpty()))
                    throw new CommandException("Protocol coverage is incomplete or contains unrecognized evidence; inspect the report");
            } else {
                if (!PacketCoverage.enabled()) throw new CommandException("Relaunch with -Dvibrato.packetCoverage=true to record actual observations");
                if (verb.equals("reset")) PacketCoverage.reset();
                else if (verb.equals("suite")) { ProtocolCoverageController.requireLocalFixture(); mod.runUserTask(new ProtocolSuiteTask(), this::finish); return; }
                else if (verb.equals("run") || verb.equals("fixture")) { mod.runUserTask(new ProtocolScenarioTask(target, verb.equals("fixture")), this::finish); return; }
                else throw new CommandException("Unknown protocol action: " + verb);
            }
        } catch (IOException | IllegalArgumentException | IllegalStateException e) { throw new CommandException(e.getMessage()); }
        finish();
    }
}
