package adris.altoclef.mixins;

import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(SimpleOption.class)
public interface SimpleOptionAccessor {
    // Bypasses the option's slider validation (e.g. gamma above 1.0 for fullbright).
    @Accessor("value")
    void setRawValue(Object value);
}
