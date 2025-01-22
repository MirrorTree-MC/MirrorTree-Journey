package top.bearcabbage.mirrortree;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.List;

import static net.minecraft.state.property.Properties.WATERLOGGED;
import static top.bearcabbage.mirrortree.MTDream.MAX_RANGE;
import static top.bearcabbage.mirrortree.MirrorTree.*;

public abstract class MTDreamingPoint {
    public static final int INTERVAL = 16;
    public static final int RADIUS = MAX_RANGE;
    public static final List<Hor_Pos> dreamingPointList = new ArrayList<>();
    public static final Hor_Pos[][] dreamingPointMatrix = new Hor_Pos[RADIUS/INTERVAL*2+1][RADIUS/INTERVAL*2+1];
    public static final MTConfig config = new MTConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("dreaming_points.json"));

    static {
        for (int i = 0; i < dreamingPointMatrix.length; i++) {
            for (int j = 0; j < dreamingPointMatrix[i].length; j++) {
                dreamingPointMatrix[i][j] = new Hor_Pos(-1, -100, -1);
            }
        }
    }

    public static void init(ServerWorld world) {
        LOGGER.info("init dreamingPoint");
        int height = world.getHeight();
        dreamingPointList.clear();
        List<Hor_Pos> dreamingPoint_tmp = new ArrayList<>();
        boolean generated = config.getOrDefault("generated", false);
        if (generated) {
            dreamingPoint_tmp = config.get("dreamingPoint", List.class);
            dreamingPointList.addAll(dreamingPoint_tmp);
            for (Hor_Pos pos : dreamingPointList) {
                dreamingPointMatrix[(pos.x+RADIUS)/INTERVAL][(pos.z+RADIUS)/INTERVAL] = pos;
            }
            LOGGER.info("loaded dreamingPoint.size() = " + dreamingPointList.size());
            return;
        }
        LOGGER.info("generating dreamingPoint");
        for (int x = -RADIUS; x <= RADIUS; x += INTERVAL) {
            for (int z = -RADIUS; z <= RADIUS; z += INTERVAL) {
                if (x * x + z * z <= RADIUS * RADIUS) {
                    dreamingPoint_tmp.add(new Hor_Pos(x, height, z));
                }
            }
        }
        LOGGER.info("dreamingPoint_tmp.size() = " + dreamingPoint_tmp.size());
        BlockPos blockPos = null;
        BlockState blockState = null;
        for (Hor_Pos pos : dreamingPoint_tmp) {
            blockPos = new BlockPos(pos.x, pos.y, pos.z);
            while(world.getBlockState(blockPos).isAir()){
                blockPos = blockPos.down();
            }
            pos = new Hor_Pos(pos.x, blockPos.getY()+1, pos.z);
            blockState = world.getBlockState(blockPos);
            if (!(blockState.isLiquid() || blockState.isIn(BlockTags.FIRE) || blockState.isIn(BlockTags.LEAVES) || (blockState.contains(WATERLOGGED) && blockState.get(WATERLOGGED)))) {
                dreamingPointList.add(pos);
                dreamingPointMatrix[(pos.x+RADIUS)/INTERVAL][(pos.z+RADIUS)/INTERVAL] = pos;
                if (dreamingPointList.size() % 100 == 0) {
                    LOGGER.info("dreamingPoint.size() = " + dreamingPointList.size());
                    config.set("dreamingPoint", dreamingPointList);
                    config.save();
                }
            }
        }
        config.set("dreamingPoint", dreamingPointList);
        config.set("generated", true);
        config.save();
        LOGGER.info("generated dreamingPoint.size() = " + dreamingPointList.size());
    }

    public static BlockPos getRandomPos () {
        Hor_Pos pos = dreamingPointList.get((int) (Math.random() * dreamingPointList.size()));
        return pos.toBlockPos();
    }

    public static BlockPos getNearbyRandomPos (int x, int z, int distance) {
        int x_index = (x+RADIUS)/INTERVAL;
        int z_index = (z+RADIUS)/INTERVAL;
        int x_min = Math.max(0, x_index - distance/INTERVAL);
        int x_max = Math.min(dreamingPointMatrix.length-1, x_index + distance/INTERVAL);
        int z_min = Math.max(0, z_index - distance/INTERVAL);
        int z_max = Math.min(dreamingPointMatrix[0].length-1, z_index + distance/INTERVAL);
        List<Hor_Pos> nearbyPos = new ArrayList<>();
        for (int i = x_min; i <= x_max; i++) {
            for (int j = z_min; j <= z_max; j++) {
                if (dreamingPointMatrix[i][j].y != -100) {
                    nearbyPos.add(dreamingPointMatrix[i][j]);
                }
            }
        }
        Hor_Pos randomPos = nearbyPos.get((int) (Math.random() * nearbyPos.size()));
        return randomPos.toBlockPos();
    }


    public static class Hor_Pos {
        public int x;
        public int y;
        public int z;
        public Hor_Pos(int x, int y, int z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        public BlockPos toBlockPos() {
            return new BlockPos(x, y, z);
        }
    }
}
