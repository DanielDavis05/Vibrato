package dev.vibrato.mixins;

import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestions;
import dev.vibrato.Vibrato;
import dev.vibrato.commandsystem.CommandExecutor;
import dev.vibrato.commandsystem.CommandSuggestions;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.command.CommandSource;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Keeps Vibrato completion entirely local, leaving ordinary chat and Baritone untouched. */
@Mixin(ChatInputSuggestor.class)
public abstract class ChatSuggestionsMixin {
    @Shadow @Final private Screen owner;
    @Shadow @Final TextFieldWidget textField;
    @Shadow @Final private List<OrderedText> messages;
    @Shadow private ParseResults<CommandSource> parse;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean windowActive;
    @Shadow boolean completingSuggestions;
    @Shadow public abstract void clearWindow();
    @Shadow public abstract void show(boolean narrateFirstSuggestion);

    @Inject(method = "refresh", at = @At("HEAD"), cancellable = true)
    private void vibrato$completeLocalCommand(CallbackInfo ci) {
        CommandExecutor executor = Vibrato.getCommandExecutor();
        String input = textField.getText();
        int cursor = textField.getCursor();
        if (!(owner instanceof ChatScreen) || executor == null || !executor.isClientCommand(input) || cursor < executor.getCommandPrefix().length()) return;

        ci.cancel();
        if (completingSuggestions) return;
        clearWindow();
        messages.clear();
        parse = null;
        pendingSuggestions = CommandSuggestions.suggest(executor, input, cursor);
        if (windowActive && !pendingSuggestions.join().isEmpty()) show(false);
    }
}
