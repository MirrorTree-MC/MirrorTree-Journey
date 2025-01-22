package top.bearcabbage.mirrortree;

import eu.pb4.universalshops.registry.TradeShopBlock;
import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.block.*;
import net.minecraft.item.Item;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.Stats;
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
import top.bearcabbage.lanterninstorm.LanternInStormAPI;
import xyz.nikitacartes.easyauth.utils.PlayerAuth;

import java.util.*;
import java.util.concurrent.*;

public class MirrorTree implements ModInitializer {
	public static final String MOD_ID = "mirrortree";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
	public static final MTConfig config = new MTConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("config.json"));
	public static final MTConfig dream = new MTConfig(FabricLoader.getInstance().getConfigDir().resolve(MOD_ID).resolve("dream_data.json"));
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
			MTDream.dreamingPos.clear();
            try {
				dream.getAll(Map.class).forEach((uuid, pos) -> {
					ArrayList<Double> posList = (ArrayList<Double>) pos;
					double[] posArray = new double[3];
					for (int i = 0; i < 3; i++) {
						posArray[i] = posList.get(i);
					}
					MTDream.dreamingPos.put(UUID.fromString((String) uuid), posArray);
				});
            } catch (Exception e) {
				LOGGER.error(e.getMessage());
            }
			CompletableFuture.runAsync(() -> MTDreamingPoint.init(server.getOverworld()));
        });

		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			MTDream.dreamingPos.forEach((uuid, pos) -> dream.set(String.valueOf(uuid), pos));
			dream.save();
		});

		EntitySleepEvents.START_SLEEPING.register((entity, sleepingLocation) -> {
			entity.sendMessage(Texts.bracketed(Text.literal("=======§l[醒来走走]§r=======").styled((style) -> style.withColor(Formatting.LIGHT_PURPLE).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wakeup")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击回到卧室（出生点大厅）维度"))))));
		});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (!world.isClient && !player.isCreative() && !(world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BedBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof DoorBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof TradeShopBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof GrassBlock || world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof LecternBlock)) return ActionResult.FAIL;
				if (world.getBlockState(hitResult.getBlockPos()).getBlock() instanceof BedBlock) {
					if (world.isClient) return ActionResult.SUCCESS;
					MTDream.queueDreamingTask(player.getServer().getOverworld(), (ServerPlayerEntity) player);
					return ActionResult.SUCCESS;
				}
			}
            return ActionResult.PASS;
        });

		UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (!player.isCreative()) return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
			if (world.getRegistryKey().getValue().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (!player.isCreative()) return ActionResult.FAIL;
			}
			return ActionResult.PASS;
		});

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
			ServerPlayerEntity player = handler.player;
			if (player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.DEATHS)) == 0
					&& player.getStatHandler().getStat(Stats.CUSTOM.getOrCreateStat(Stats.WALK_ONE_CM)) == 0) {
				player.setYaw(90);
				player.setSpawnPoint(bedroom, new BlockPos(bedroomX_init, bedroomY_init, bedroomZ_init), 90, true, false);
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
						player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal("现在，进到房子里去吧…").formatted(Formatting.BOLD).formatted(Formatting.BLUE)));
						player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal("去床上睡一觉").formatted(Formatting.GRAY).formatted(Formatting.ITALIC)));
						iterator.remove(); // 使用 Iterator 进行删除操作
					}
				}
			}
		});



	}
}