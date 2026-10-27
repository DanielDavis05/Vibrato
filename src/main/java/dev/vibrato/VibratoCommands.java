package dev.vibrato;

import dev.vibrato.commands.*;
import dev.vibrato.commandsystem.CommandException;

/**
 * Initializes vibrato's built in commands.
 */
public class VibratoCommands {

    public VibratoCommands() throws CommandException {
        // List commands here
        Vibrato.getCommandExecutor().registerNewCommand(
                new HelpCommand(),
                new PlayCommand(),
                new ProtocolCommand(),
                new GetCommand(),
                new FollowCommand(),
                new GiveCommand(),
                new EquipCommand(),
                new DepositCommand(),
                new StashCommand(),
                new GotoCommand(),
                new IdleCommand(),
                new CoordsCommand(),
                new StatusCommand(),
                new InventoryCommand(),
                new LocateStructureCommand(),
                new StopCommand(),
                new TestCommand(),
                new FoodCommand(),
                new ReloadSettingsCommand(),
                new GamerCommand(),
                new PunkCommand(),
                new SetGammaCommand(),
                new ListCommand(),
                new CustomCommand()
                //new TestMoveInventoryCommand(),
                //    new TestSwapInventoryCommand()
        );
    }
}
