package top.bearcabbage.mirrortree;

import com.google.common.collect.Maps;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.GameMode;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.lanterninstorm.lantern.BeginningLanternEntity;
import top.bearcabbage.lanterninstorm.player.LiSPlayer;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;

import static net.minecraft.state.property.Properties.WATERLOGGED;
import static top.bearcabbage.mirrortree.MirrorTree.*;

public class MTDream {
    public static final int MAX_RANGE = 3000;
    private static final int DREAM_RANDOM_RANGE = 256;
    public static BlockPos pos;
    public static long lastTime = 0;

    private static final LinkedBlockingQueue<Runnable> dreamQueue = new LinkedBlockingQueue<>();
    private static final LinkedBlockingQueue<Runnable> redreamQueue = new LinkedBlockingQueue<>();
    private static final ExecutorService dreamExecutor = Executors.newSingleThreadExecutor();
    private static final ExecutorService redreamExecutor = Executors.newSingleThreadExecutor();

    public static void queueDreamingTask(ServerWorld world, ServerPlayerEntity player) {
        dreamQueue.add(() -> dreaming(world, player));
        processDreamQueue();
    }

    private static void processDreamQueue() {
        dreamExecutor.submit(() -> {
            try {
                while (!dreamQueue.isEmpty()) {
                    Runnable task = dreamQueue.take();
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    public static void queueRedreamingTask(ServerWorld world, ServerPlayerEntity player) {
        redreamQueue.add(() -> redreaming(world, player));
        processRedreamQueue();
    }

    private static void processRedreamQueue() {
        redreamExecutor.submit(() -> {
            try {
                while (!redreamQueue.isEmpty()) {
                    Runnable task = redreamQueue.take();
                    task.run();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
    }

    // 正常关服时写文件储存数据
    public static final Map<UUID, double[]> dreamingPos = Maps.newHashMap();
    // 这个不写入
    public static final Map<UUID, Collection<StatusEffectInstance>> dreamingEffects = Maps.newHashMap();
    public static final Map<UUID, ArrayList<Float>> dreamingHealthAndHunger = Maps.newHashMap();

    public static void dreaming(ServerWorld world, ServerPlayerEntity player) {
        if(LanternInStormAPI.getRTPSpawn(player)==null) {
            BlockPos pos_tmp;
            if (lastTime==0 || System.currentTimeMillis() - lastTime > 3000) {
                lastTime = System.currentTimeMillis();
                pos_tmp = pos = getRandomPos(0, 0, MAX_RANGE);
            } else {
                lastTime = System.currentTimeMillis();
                pos_tmp = getRandomPos(pos.getX(), pos.getZ(), DREAM_RANDOM_RANGE);
            }
            player.teleport(world, pos_tmp.toCenterPos().getX(), pos_tmp.toCenterPos().getY(), pos_tmp.toCenterPos().getZ(), 0,0);
            LanternInStormAPI.setRTPSpawn(player, pos);
        } else {
            pos = LanternInStormAPI.getRTPSpawn(player);
            lastTime = System.currentTimeMillis();
            if (dreamingPos.containsKey(player.getUuid())) {
                ((ServerPlayerEntity) player).teleport(world.getServer().getOverworld(), dreamingPos.get(player.getUuid())[0]+0.5, dreamingPos.get(player.getUuid())[1]+0.5, dreamingPos.get(player.getUuid())[2]+0.5, 0, 0);
                dreamingPos.remove(player.getUuid());
            } else {
                ((ServerPlayerEntity) player).teleport(world.getServer().getOverworld(), pos.toCenterPos().getX(), pos.toCenterPos().getY(), pos.toCenterPos().getZ(), 0, 0);
            }
        }
        player.changeGameMode(GameMode.SURVIVAL);
        if (dreamingEffects.containsKey(player.getUuid())) {
            for (StatusEffectInstance effect : dreamingEffects.get(player.getUuid())) {
                player.addStatusEffect(effect);
            }
            dreamingEffects.remove(player.getUuid());
        }
        if (dreamingHealthAndHunger.containsKey(player.getUuid())) {
            ArrayList<Float> healthAndHunger = dreamingHealthAndHunger.get(player.getUuid());
            player.setHealth(healthAndHunger.get(0));
            player.getHungerManager().setFoodLevel((int) Math.floor(healthAndHunger.get(1)));
            player.getHungerManager().setSaturationLevel(healthAndHunger.get(2));
            player.getHungerManager().setExhaustion(healthAndHunger.get(3));
            dreamingHealthAndHunger.remove(player.getUuid());
        }
    }

    public static void redreaming(ServerWorld world, ServerPlayerEntity player) {
        ((LiSPlayer)player).getLS().setRtpSpawn(null);
        List<BeginningLanternEntity> entities = (List<BeginningLanternEntity>) player.getServerWorld().getEntitiesByType(BeginningLanternEntity.BEGINNING_LANTERN, (entity)-> entity.getCustomName().getString().contains("入梦点["+player.getName().getString()+"]"));
        if (!entities.isEmpty()) entities.forEach(Entity::discard);
        dreamingPos.remove(player.getUuid());
        dreamingEffects.remove(player.getUuid());
        dreamingHealthAndHunger.remove(player.getUuid());
        player.teleport(world.getServer().getWorld(bedroom), bedroomX_init, bedroomY_init, bedroomZ_init, 90,0);
        player.clearStatusEffects();
        player.changeGameMode(GameMode.ADVENTURE);
        player.setSpawnPoint(bedroom, new BlockPos(bedroomX_init, bedroomY_init, bedroomZ_init), 90, true, false);
    }

    private static BlockPos getRandomPos(int xx, int zz, int range) {
        return (xx==0 && zz==0) ? MTDreamingPoint.getRandomPos() : MTDreamingPoint.getNearbyRandomPos(xx, zz, range);
    }
}