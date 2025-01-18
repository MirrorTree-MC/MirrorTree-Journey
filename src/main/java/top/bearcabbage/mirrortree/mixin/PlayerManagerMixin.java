package top.bearcabbage.mirrortree.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtDouble;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Optional;

import static top.bearcabbage.mirrortree.MirrorTree.*;

@Mixin(PlayerManager.class)
public class PlayerManagerMixin {

    @Shadow
    @Final
    private MinecraftServer server;

    /**
     * Called everytime a player connects to the server,
     * and its profile is being loaded from disk
     * => Change the player's position as early as possible
     */
    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    @ModifyReturnValue(method = "loadPlayerData", at = @At("RETURN"))
    public Optional<NbtCompound> loadPlayerData(Optional<NbtCompound> original, ServerPlayerEntity player) {
        NbtCompound nbt = original.orElse(new NbtCompound());
        if (player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS)) == 0
                && player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM)) == 0) {
            nbt.putString("Dimension", bedroom.getValue().toString());
            nbt.putFloat("SpawnAngle", 90);
            NbtList listTag = new NbtList();
            listTag.addElement(0, NbtDouble.of(bedroomX_init + 0.5));
            listTag.addElement(1, NbtDouble.of(bedroomY_init));
            listTag.addElement(2, NbtDouble.of(bedroomZ_init + 0.5));
            nbt.put("Pos", listTag);
            player.readNbt(nbt);
        }
        return Optional.of(nbt);
    }
}
