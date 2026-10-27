package dev.vibrato.packetcoverage;

import dev.vibrato.Vibrato;
import dev.vibrato.tasksystem.Task;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.DisconnectedScreen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.MultiplayerServerListPinger;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import java.io.DataInputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

/** A real connection task, ticked before joining a world: status, legacy probe, then native login. */
public final class ProtocolConnectTask extends Task {
    private enum Stage { STATUS, LEGACY, CONNECT, DONE }
    private final String address;
    private final String label;
    private final MultiplayerServerListPinger pinger = new MultiplayerServerListPinger();
    private Stage stage;
    private long ticks;
    private long stageTick;
    private volatile boolean pingCompleted;
    private CompletableFuture<Boolean> legacy;

    public ProtocolConnectTask(String address, String label) {
        ProtocolCoverageController.requireLoopback(address);
        this.address = address;
        this.label = label;
    }
    @Override protected void onStart(Vibrato mod) {
        stage = Stage.STATUS; ticks = 0; stageTick = 0;
        PacketCoverage.beginScenario(label);
        try {
            pinger.add(new ServerInfo("Local protocol fixture", address, ServerInfo.ServerType.OTHER), () -> {}, () -> pingCompleted = true);
        } catch (Exception e) { pingCompleted = true; }
    }
    @Override protected Task onTick(Vibrato mod) {
        ticks++;
        if (stage == Stage.STATUS) {
            pinger.tick();
            if (ticks >= 40 && pingCompleted || ticks >= 160) {
                pinger.cancel();
                stage = Stage.LEGACY; stageTick = ticks;
                var context = PacketCoverage.capture();
                legacy = CompletableFuture.supplyAsync(() -> legacyProbe(context));
            }
        } else if (stage == Stage.LEGACY && (legacy.isDone() || ticks - stageTick > 100)) {
            stage = Stage.CONNECT; stageTick = ticks;
            MinecraftClient client = MinecraftClient.getInstance();
            ServerInfo info = new ServerInfo("Local protocol fixture", address, ServerInfo.ServerType.OTHER);
            info.setResourcePackPolicy(ServerInfo.ResourcePackPolicy.DISABLED);
            ConnectScreen.connect(new TitleScreen(), client, ServerAddress.parse(address), info, false, null);
        } else if (stage == Stage.CONNECT) {
            var client = MinecraftClient.getInstance();
            if (Vibrato.inGame() || client.currentScreen instanceof DisconnectedScreen && ticks - stageTick > 20 || ticks - stageTick > 1200) stage = Stage.DONE;
        }
        return null;
    }
    private boolean legacyProbe(PacketCoverage.Context context) {
        var parsed = ServerAddress.parse(address);
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(parsed.getAddress(), parsed.getPort()), 3000);
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(new byte[] {(byte) 0xfe, 1});
            socket.getOutputStream().flush();
            DataInputStream input = new DataInputStream(socket.getInputStream());
            if (input.readUnsignedByte() != 0xff) return false;
            int length = input.readUnsignedShort();
            if (length <= 0 || length > 32767) return false;
            byte[] bytes = input.readNBytes(length * 2);
            if (bytes.length != length * 2) return false;
            String response = new String(bytes, StandardCharsets.UTF_16BE);
            if (!response.startsWith("\u00a71\u0000") || response.split("\u0000", -1).length < 6) return false;
            PacketCoverage.wireSuccess(context, "HANDSHAKING", "SERVERBOUND", "0xfe");
            return true;
        } catch (Exception e) { return false; }
    }
    @Override protected void onStop(Vibrato mod, Task interrupted) { pinger.cancel(); }
    @Override public boolean isFinished(Vibrato mod) { return stage == Stage.DONE; }
    @Override protected boolean isEqual(Task other) { return other == this; }
    @Override protected String toDebugString() { return "Protocol connection " + label + " (" + stage + ")"; }
}
