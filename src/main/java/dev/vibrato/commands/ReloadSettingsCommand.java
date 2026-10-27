package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.util.helpers.ConfigHelper;

public class ReloadSettingsCommand extends Command {
    public ReloadSettingsCommand() {
        super("reload_settings", "Reloads bot settings and butler whitelist/blacklist.");
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) {
        ConfigHelper.reloadAllConfigs();
        mod.log("Reload successful!");
        finish();
    }
}