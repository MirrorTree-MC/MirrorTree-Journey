package top.bearcabbage.mirrortree;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.ArrayList;

import static top.bearcabbage.mirrortree.MirrorTree.*;

public class MTCommand {

    public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("wakeup")
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player==null) return 0;
                    if (!player.isSleeping()) return 0;
                    player.wakeUp();
                    double[] pos = {player.getBlockPos().getX(), player.getBlockPos().getY(), player.getBlockPos().getZ()};
                    MTDream.dreamingPos.put(player.getUuid(), pos);
                    ServerWorld bedroom = player.getServer().getWorld(MirrorTree.bedroom);
                    player.teleport(bedroom, bedroomX, bedroomY, bedroomZ, 0,0);
                    player.changeGameMode(GameMode.ADVENTURE);
                    MTDream.dreamingEffects.put(player.getUuid(), player.getStatusEffects());
                    ArrayList<Float> healthAndHunger = new ArrayList<>();
                    healthAndHunger.add(player.getHealth());
                    healthAndHunger.add((float) player.getHungerManager().getFoodLevel());
                    healthAndHunger.add(player.getHungerManager().getSaturationLevel());
                    healthAndHunger.add(player.getHungerManager().getExhaustion());
                    MTDream.dreamingHealthAndHunger.put(player.getUuid(), healthAndHunger);
                    player.clearStatusEffects();
                    player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("你醒来了").formatted(Formatting.BOLD).formatted(Formatting.BLUE)));
                    return 0;
                })
        );
        dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("redream")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player==null) return 0;
                    try {
                        MTDream.queueRedreamingTask(player.getServer().getOverworld(), player);
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage());
                    }
                    return 0;
                })
        );
    }
}
