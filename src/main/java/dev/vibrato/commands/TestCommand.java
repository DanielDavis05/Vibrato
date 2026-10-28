package dev.vibrato.commands;

import dev.vibrato.Vibrato;
import dev.vibrato.Playground;
import dev.vibrato.commandsystem.Arg;
import dev.vibrato.commandsystem.ArgParser;
import dev.vibrato.commandsystem.Command;
import dev.vibrato.commandsystem.CommandException;
import dev.vibrato.tasks.build.Blueprints;
import dev.vibrato.tasks.construction.PlaceBlockTask;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class TestCommand extends Command {

    public TestCommand() throws CommandException {
        super("test", "Generic command for testing", new Arg(String.class, "extra", "", 0));
    }

    // Reproduces the upper first-column block of the 4x3 oak wall in the captured failure.
    static Set<BlockPos> wallFootprint(BlockPos target) {
        BlockPos origin = target.down(4);
        return Blueprints.wall(4, 3).placements().stream()
                .map(p -> origin.add(p.dx(), p.dy(), p.dz())).collect(Collectors.toUnmodifiableSet());
    }

    @Override
    protected void call(Vibrato mod, ArgParser parser) throws CommandException {
        String[] args = parser.getArgUnits();
        if (args.length > 0 && args[0].equals("place_wall_at")) {
            if (args.length != 4) throw new CommandException("Usage: @test place_wall_at x y z");
            final BlockPos target;
            try { target = new BlockPos(Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3])); }
            catch (NumberFormatException error) { throw new CommandException("Expected integer target coordinates", error); }
            String telemetry = System.getProperty("vibrato.serverTelemetryFile");
            if (telemetry == null || telemetry.isBlank()) throw new CommandException("Server telemetry is required for this diagnostic");
            try {
                Path request = Path.of(telemetry).resolveSibling("telemetry-request.json");
                Path temp = request.resolveSibling(request.getFileName() + ".tmp");
                new ObjectMapper().writeValue(temp.toFile(), Map.of("requestId", UUID.randomUUID().toString(),
                        "blocks", List.of(List.of(target.getX(), target.getY(), target.getZ()))));
                Files.move(temp, request, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception error) { throw new CommandException("Could not request server block observation", error); }
            mod.runUserTask(new PlaceBlockTask(target, Blocks.OAK_PLANKS, wallFootprint(target)), this::finish);
            return;
        }
        Playground.TEMP_TEST_FUNCTION(mod, parser.get(String.class));
        finish();
    }
}