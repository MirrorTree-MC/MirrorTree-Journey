package top.bearcabbage.mirrortree;

import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.BedBlock;
import net.minecraft.block.BlockState;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.BlockTags;
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
import net.minecraft.world.GameMode;
import net.minecraft.world.World;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.server.world.ServerWorld;
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import top.bearcabbage.lanterninstorm.lantern.BeginningLanternEntity;
import top.bearcabbage.lanterninstorm.player.LiSPlayer;
import xyz.nikitacartes.easyauth.utils.PlayerAuth;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;

import static net.minecraft.state.property.Properties.WATERLOGGED;
import static top.bearcabbage.lanterninstorm.LanternInStorm.MOD_ID;
import static xyz.nikitacartes.easyauth.EasyAuth.langConfig;

public class MirrorTree implements ModInitializer {
	public static final String MOD_ID = "mirrortree";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final Config config = new Config(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"));
	public static final Config dream = new Config(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("dream_data.json"));
	public static RegistryKey<World> bedroom = RegistryKey.of(RegistryKeys.WORLD, Identifier.of(MOD_ID, "bedroom"));
	public static int bedroomX;
	public static int bedroomY;
	public static int bedroomZ;
	public static int bedroomX_init;
	public static int bedroomY_init;
	public static int bedroomZ_init;

	public static final Map<ServerPlayerEntity, Integer> fresh_player = new ConcurrentHashMap<>();

	public static final Item FOX_TAIL_ITEM = Registry.register(Registries.ITEM, Identifier.of(MOD_ID, "fox_tail_item"), new Item(new Item.Settings().maxCount(99)));

	@Override
	public void onInitialize() {

		bedroomX = config.getInt("bedroomX", 0);
		bedroomY = config.getInt("bedroomY", 80);
		bedroomZ = config.getInt("bedroomZ", 0);
		bedroomX_init = config.getInt("bedroomX_init", 0);
		bedroomY_init = config.getInt("bedroomY_init", 100);
		bedroomZ_init = config.getInt("bedroomZ_init", 0);

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment)->MTCommand.registerCommands(dispatcher)); // 调用静态方法注册命令

		ServerLifecycleEvents.SERVER_STARTED.register(server -> {
			LanternInStormAPI.overrideRTPSpawnSetting();
			LanternInStormAPI.addSafeWorld(bedroom);
			Dream.dreamingPos.clear();
            try {
				dream.getAll(Map.class).forEach((uuid, pos) -> {
					ArrayList<Double> posList = (ArrayList<Double>) pos;
					double[] posArray = new double[3];
					for (int i = 0; i < 3; i++) {
						posArray[i] = posList.get(i);
					}
					Dream.dreamingPos.put(UUID.fromString((String) uuid), posArray);
				});
            } catch (Exception e) {
				LOGGER.error(e.getMessage());
            }
        });

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			Dream.dreamingPos.forEach((uuid, pos) -> dream.set(String.valueOf(uuid), pos));
			dream.save();
		});

		EntitySleepEvents.START_SLEEPING.register((entity, sleepingLocation) -> {
			entity.sendMessage(Texts.bracketed(Text.literal("=======§l[醒来走走]§r=======").styled((style) -> style.withColor(Formatting.LIGHT_PURPLE).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wakeup")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击回到卧室（出生点大厅）维度"))))));
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.isClient) return ActionResult.SUCCESS;
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BedBlock) {
					Dream.queueDreamingTask(player.getServer().getOverworld(), (ServerPlayerEntity) player);
					return ActionResult.SUCCESS;
				}
			}
            return ActionResult.PASS;
        });

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			if (!player.getServerWorld().getRegistryKey().equals(bedroom) && LanternInStormAPI.getRTPSpawn(player) == null) {
				player.teleport(player.getServer().getWorld(bedroom), bedroomX_init, bedroomY_init, bedroomZ_init, 90, 0);
				player.changeGameMode(GameMode.ADVENTURE);
				player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("你来到了狐狸的生前住所").formatted(Formatting.BOLD)));
				player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("原来这里就是梦境的入口…").formatted(Formatting.GRAY).formatted(Formatting.ITALIC)));
				fresh_player.put(player, 0);
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
			ServerPlayerEntity player = handler.player;
			if (((PlayerAuth) player).easyAuth$isAuthenticated() && !((PlayerAuth) player).easyAuth$canSkipAuth()) {
				((PlayerAuth) player).easyAuth$setAuthenticated(false);
				((PlayerAuth) player).easyAuth$saveLastLocation(true);
			}
		});

		ServerTickEvents.END_WORLD_TICK.register(world -> {
			if (world.getRegistryKey().equals(bedroom)) {
				Iterator<Map.Entry<ServerPlayerEntity, Integer>> iterator = fresh_player.entrySet().iterator();
				while (iterator.hasNext()) {
					Map.Entry<ServerPlayerEntity, Integer> entry = iterator.next();
					ServerPlayerEntity player = entry.getKey();
					int time = entry.getValue();
					if (time < 100) {
						fresh_player.put(player, time + 1);
					} else {
						player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("现在，进去吧…").formatted(Formatting.BOLD).formatted(Formatting.BLUE)));
						player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("去床上睡一觉").formatted(Formatting.GRAY).formatted(Formatting.ITALIC)));
						iterator.remove(); // 使用 Iterator 进行删除操作
					}
				}
			}
		});



	}

	private static class Dream{
		public static final int MAX_RANGE = 3000;
		public static BlockPos pos;
		public static long lastTime = 0;

		private static final LinkedBlockingQueue<Runnable> dreamQueue = new LinkedBlockingQueue<>();
		private static final LinkedBlockingQueue<Runnable> redreamQueue = new LinkedBlockingQueue<>();
		private static final ExecutorService dreamExecutor = Executors.newSingleThreadExecutor();
		private static final ExecutorService redreamExecutor = Executors.newSingleThreadExecutor();

		public static void queueDreamingTask(ServerWorld world, ServerPlayerEntity player) {
			dreamQueue.add(() -> Dream.dreaming(world, player));
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
			redreamQueue.add(() -> Dream.redreaming(world, player));
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
					((ServerPlayerEntity) player).teleport(world.getServer().getOverworld(), dreamingPos.get(player.getUuid())[0]+0.5, dreamingPos.get(player.getUuid())[1]+0.5, dreamingPos.get(player.getUuid())[2]+0.5, 0, 0);
					dreamingPos.remove(player.getUuid());
				} else {
					((ServerPlayerEntity) player).teleport(world.getServer().getOverworld(), pos.toCenterPos().getX(), pos.toCenterPos().getY(), pos.toCenterPos().getZ(), 0, 0);
				}
			}
			player.changeGameMode(GameMode.SURVIVAL);
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
						double[] pos = {player.getBlockPos().getX(), player.getBlockPos().getY(), player.getBlockPos().getZ()};
						Dream.dreamingPos.put(player.getUuid(), pos);
						ServerWorld bedroom = player.getServer().getWorld(MirrorTree.bedroom);
						player.teleport(bedroom, bedroomX, bedroomY, bedroomZ, 0,0);
						player.changeGameMode(GameMode.ADVENTURE);
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
							Dream.queueRedreamingTask(player.getServer().getOverworld(), player);
						} catch (Exception e) {
							LOGGER.error(e.getMessage());
						}
						return 0;
					})
			);
		}
	}

	public static class Config {
		private final Path filePath;
		private JsonObject jsonObject;
		private final Gson gson;

		public Config(Path filePath) {
			this.filePath = filePath;
			this.gson = new GsonBuilder().setPrettyPrinting().create();
			try {
				if (Files.notExists(filePath.getParent())) {
					Files.createDirectories(filePath.getParent());
				}
				if (Files.notExists(filePath)) {
					Files.createFile(filePath);
					try (FileWriter writer = new FileWriter(filePath.toFile())) {
						writer.write("{}");
					}
				}

			} catch (IOException e) {
				LOGGER.error(e.toString());
			}
			load();
		}

		public void load() {
			try (FileReader reader = new FileReader(filePath.toFile())) {
				this.jsonObject = JsonParser.parseReader(reader).getAsJsonObject();
			} catch (IOException e) {
				this.jsonObject = new JsonObject();
			}
		}

		public void save() {
			try (FileWriter writer = new FileWriter(filePath.toFile())) {
				gson.toJson(jsonObject, writer);
			} catch (IOException e) {
				LOGGER.error(e.toString());
			}
		}

		public void set(String key, Object value) {
			jsonObject.add(key, gson.toJsonTree(value));
		}

		public <T> T get(String key, Class<T> clazz) {
			return gson.fromJson(jsonObject.get(key), clazz);
		}

		public int getInt(String key, int defaultValue) {
			if (jsonObject.has(key)) {
				return jsonObject.get(key).getAsInt();
			}
			else {
				set(key, defaultValue);
				save();
				return defaultValue;
			}
		}

		public <T> T getAll(Class<T> clazz) {
			return gson.fromJson(jsonObject, clazz);
		}

	}

}