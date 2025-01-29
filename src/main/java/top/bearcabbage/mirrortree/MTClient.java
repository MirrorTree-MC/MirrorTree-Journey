package top.bearcabbage.mirrortree;

import com.terraformersmc.modmenu.ModMenu;
import com.terraformersmc.modmenu.util.mod.Mod;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.client.world.ClientWorld;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.mojang.text2speech.Narrator.LOGGER;
import static top.bearcabbage.mirrortree.MirrorTree.MOD_ID;

public class MTClient {
    public static final Map<String, String> modsServer = new HashMap<>();
    private static final int SCREENSHOT_INTERVAL = 300*20;
    public static boolean isUpdate_modsServer = false;
    public static final Map<String, String> modsURL = new HashMap<>();
    public static boolean isUpdate_modsURL = false;
    public static final Path SCREENSHOT_PATH = FabricLoader.getInstance().getGameDir().resolve("screenshots");
    public static int tick = 0;


    public static void onTick(ClientWorld client) {
    if (client.random.nextDouble() <= (double) 1 / SCREENSHOT_INTERVAL) {
        String playerName = MinecraftClient.getInstance().player.getName().getLiteralString();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");
        String timestamp = LocalDateTime.now().format(formatter);
        File shot = new File(SCREENSHOT_PATH.resolve(playerName + "-" + timestamp + ".png").toUri());
        try {
            if(!SCREENSHOT_PATH.toFile().exists()) {
                SCREENSHOT_PATH.toFile().mkdir();
            }
            LOGGER.info("[MirrorTree]Taking screenshot");
            ScreenshotRecorder.takeScreenshot(MinecraftClient.getInstance().getFramebuffer()).writeTo(shot);
        } catch (Exception e) {
            LOGGER.error("[MirrorTree]" + e);
        }
        CompletableFuture.runAsync(() -> TencentCloudCosUpload.upload(shot, shot.getName()));
    }
}

    public static void onStarted(MinecraftClient client) {
        Path modsDir = client.runDirectory.toPath().resolve("config/mirrortree");
        try {
            downloadFile("mod_version.json" ,"https://gitee.com/integrity_k/.github-private/releases/download/latest/mod_version.json", modsDir.toString());
        } catch (IOException e) {
            LOGGER.error("[MirrorTree]Updating error");
            return;
        }
        MTConfig config1 = new MTConfig(modsDir.resolve("mod_version.json"));
        MTClient.modsServer.putAll(config1.getOrDefault("mods", new HashMap<>()));
        MTClient.modsURL.putAll(config1.getOrDefault("urls", new HashMap<>()));
        isUpdate_modsServer = isUpdate_modsURL = true;
        MTClient.updateMods();

    }

    public static void updateMods() {
        if (!isUpdate_modsServer || !isUpdate_modsURL) {
            return;
        }
        Set<String> mods_needUpdate = new HashSet<>();
        Map<String, String> modsOutdated = new HashMap<>();
        modsServer.forEach((modid, version) -> {
            Mod mod = ModMenu.MODS.values().stream().filter(m -> m.getId().equals(modid)).findFirst().orElse(null);
            if (mod == null || mod.getVersion().compareTo(version) < 0) {
                mods_needUpdate.add(modid);
                if (mod != null && !modid.equals(MOD_ID)) {
                    modsOutdated.put(modid, mod.getVersion());
                }
            }
        });
        if (mods_needUpdate.isEmpty()) return;
        Map<String,String> updatedMods = new HashMap<>();
        Path modsDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods");
        mods_needUpdate.forEach(mod -> {
            try {
                downloadFile(mod.concat("-").concat(modsServer.get(mod)).concat(".jar"), modsURL.get(mod), modsDir.toString());
                LOGGER.info("Downloaded " + mod);
                if (modsOutdated.containsKey(mod)) {
                    File modOld = modsDir.resolve(mod.concat("-").concat(modsOutdated.get(mod)).concat(".jar")).toFile();
                    if (modOld.exists()) {
                        modOld.delete();
                        LOGGER.info("Deleted old " + modOld.getName());
                    }
                    modOld = modsDir.resolve(mod.concat(".jar")).toFile();
                    if (modOld.exists()) {
                        modOld.delete();
                        LOGGER.info("Deleted old " + modOld.getName());
                    }
                }
                updatedMods.put(mod, modsServer.get(mod));
            } catch (Exception e) {
                LOGGER.error(e.toString());
            }
        });
        MinecraftClient.getInstance().stop();
        throw new RuntimeException("[MirrorTree]客户端更新完成，请重启客户端");
    }

    public static void downloadFile(String filename, String fileURL, String saveDir) throws IOException {
        URL url = new URL(fileURL);
        try (InputStream in = url.openStream()) {
            Path targetPath = Paths.get(saveDir).resolve(filename);
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
