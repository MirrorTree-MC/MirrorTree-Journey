package top.bearcabbage.mirrortree;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.Dynamic4CommandExceptionType;
import com.sun.jdi.connect.Connector;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.command.argument.Vec2ArgumentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.predicate.entity.LocationPredicate;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.*;
import net.minecraft.world.World;
import net.minecraft.world.WorldAccess;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.world.ServerWorld;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.lanterninstorm.lantern.BeginningLanternEntity;
import top.bearcabbage.lanterninstorm.player.LiSPlayer;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.state.property.Properties.WATERLOGGED;
import static net.minecraft.world.World.OVERWORLD;

public class MirrorTree implements ModInitializer {
	public static final String MOD_ID = "mirrortree";
	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	public static RegistryKey<World> bedroom = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "bedroom"));
	public static int bedroomX = 0;
	public static int bedroomY = 100;
	public static int bedroomZ = 0;


	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment)->MTCommand.registerCommands(dispatcher)); // 调用静态方法注册命令


		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LanternInStormAPI.overrideRTPSpawnSetting();
			LanternInStormAPI.addSafeWorld(bedroom);
		});

		EntitySleepEvents.START_SLEEPING.register((entity, sleepingLocation) -> {
			entity.sendMessage(Texts.bracketed(Text.literal("[HHHor]离梦").styled((style) -> style.withColor(Formatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wakeup")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击在卧室中醒来"))))));
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient) return ActionResult.SUCCESS;
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BedBlock) {
					Dream.dreaming(player.getServer().getOverworld(), (ServerPlayerEntity) player);
				}
			}
            return ActionResult.PASS;
        });

	}

	private static class Dream{
		public static final int MAX_RANGE = 3000;
		public static BlockPos pos;
		public static long lastTime = 0;

		// 暂时没写储存
		public static final Map<UUID,BlockPos> dreamingPos = Maps.newHashMap();

		public static void dreaming(ServerWorld world, ServerPlayerEntity player) {
			if(((LiSPlayer)player).getLS().getRtpSpawn()==null) {
				if (lastTime==0 || System.currentTimeMillis() - lastTime > 3000) {
						lastTime = System.currentTimeMillis();
						pos = getRandomPos(world);
					} else {
						lastTime = System.currentTimeMillis();
					}
					player.teleport(world, pos.toCenterPos().getX(), pos.toCenterPos().getY(), pos.toCenterPos().getZ(), 0,0);
					LanternInStormAPI.setRTPSpawn(player, pos);
			} else {
				pos = ((LiSPlayer) player).getLS().getRtpSpawn();
				lastTime = System.currentTimeMillis();
				if (dreamingPos.containsKey(player.getUuid())) {
					((ServerPlayerEntity) player).teleport(world.getServer().getOverworld(), dreamingPos.get(player.getUuid()).toCenterPos().getX(), dreamingPos.get(player.getUuid()).toCenterPos().getY(), dreamingPos.get(player.getUuid()).toCenterPos().getZ(), 0, 0);
					dreamingPos.remove(player.getUuid());
				} else {
					((ServerPlayerEntity) player).teleport(world.getServer().getOverworld(), pos.toCenterPos().getX(), pos.toCenterPos().getY(), pos.toCenterPos().getZ(), 0, 0);
				}
			}
		}

		public static void redreaming(ServerWorld world, ServerPlayerEntity player) {
			((LiSPlayer)player).getLS().setRtpSpawn(null);
			List<BeginningLanternEntity> entities = (List<BeginningLanternEntity>) player.getServerWorld().getEntitiesByType(BeginningLanternEntity.BEGINNING_LANTERN, (entity)-> entity.getCustomName().getString().contains(player.getName().getString()));
			if (entities.size()>0) {
				entities.get(0).discard();
			}
			BlockPos pos1 = getRandomPos(world);
			player.teleport(world, pos1.toCenterPos().getX(), pos1.toCenterPos().getY(), pos1.toCenterPos().getZ(), 0,0);
			LanternInStormAPI.setRTPSpawn(player, pos1);
		}

		private static BlockPos getRandomPos(ServerWorld world) {
            BlockPos blockPos = null;
            BlockState blockState = null;
            do {
				Random random = new Random();
                int x,z;
                x = random.nextInt(-MAX_RANGE, MAX_RANGE);
                z = random.nextInt(-MAX_RANGE, MAX_RANGE);
                while (x*x+z*z>MAX_RANGE*MAX_RANGE){
                    x = random.nextInt(-MAX_RANGE, MAX_RANGE);
                    z = random.nextInt(-MAX_RANGE, MAX_RANGE);
                }
                blockPos = new BlockPos(x, world.getHeight(), z);
//				LOGGER.info(blockPos.toString());
                while(world.getBlockState(blockPos).isAir()){
                    blockPos = blockPos.down();
                }
                blockState = world.getBlockState(blockPos);
            }
			while(blockState.isLiquid() || blockState.isIn(BlockTags.FIRE) || blockState.isIn(BlockTags.LEAVES) || (blockState.contains(WATERLOGGED) && blockState.get(WATERLOGGED)));
			return blockPos;
		}


	}

	private static class MTCommand {

		public static void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
			dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("wakeup")
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player==null) return 0;
						if (!player.isSleeping()) return 0;
						player.wakeUp();
						Dream.dreamingPos.put(player.getUuid(), player.getBlockPos());
						ServerWorld bedroom = player.getServer().getWorld(MirrorTree.bedroom);
						player.teleport(bedroom, bedroomX, bedroomY, bedroomZ, 0,0);
						return 0;
					})
			);
			dispatcher.register(LiteralArgumentBuilder.<ServerCommandSource>literal("redream")
					.requires(source -> source.hasPermissionLevel(2))
					.executes(context -> {
						ServerPlayerEntity player = context.getSource().getPlayer();
						if (player==null) return 0;
						try {
							Dream.redreaming(player.getServer().getOverworld(), player);
						} catch (Exception e) {
							LOGGER.error(e.getMessage());
						}
						return 0;
					})
			);
		}
	}

}