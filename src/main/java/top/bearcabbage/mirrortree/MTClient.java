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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static com.mojang.text2speech.Narrator.LOGGER;

public class MTClient {
    public static final Map<String, String> modsServer = new HashMap<>();
    private static final int SCREENSHOT_INTERVAL = 300*20;
    public static boolean isUpdate_modsServer = false;
    public static final Map<String, String> modsURL = new HashMap<>();
    public static boolean isUpdate_modsURL = false;
    public static final Path SCREENSHOT_PATH = FabricLoader.getInstance().getGameDir().toAbsolutePath().resolve("screenshots");
    public static int tick = 0;


    public static void onTick(ClientWorld client){

        if (client.random.nextDouble()<= (double) 1 /SCREENSHOT_INTERVAL /*tick++%SCREENSHOT_INTERVAL==0*/) {
            File shot = new File(SCREENSHOT_PATH.resolve(MinecraftClient.getInstance().player.getName().getLiteralString() + "-" + LocalDateTime.now() + ".png").toUri());
            try {
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
            downloadFile("mod_version.json" ,"https://cos-mirror.bearcabbage.top/MirrorTree-Journey/mods/mod_version.json", modsDir.toString());
        } catch (IOException e) {
            LOGGER.error("[MirrorTree]Updating error");
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
        Set<Mod> mods_needUpdate = new HashSet<>();
        modsServer.forEach((modid, version) -> {
            Mod mod = ModMenu.MODS.values().stream().filter(m -> m.getId().equals(modid)).findFirst().orElse(null);
            if (mod == null || !mod.getVersion().equals(version)) {
                mods_needUpdate.add(mod);
            }
        });
        if (mods_needUpdate.isEmpty()) return;
        Map<String,String> updatedMods = new HashMap<>();
        Path modsDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("mods");
        mods_needUpdate.forEach(mod -> {
            try {
                downloadFile(mod.getId(), modsURL.get(mod.getId()), modsDir.toString());
                LOGGER.info("Downloaded " + mod.getId());
                updatedMods.put(mod.getId(), modsServer.get(mod.getId()));
            } catch (Exception e) {
                LOGGER.error(e.toString());
            }
        });
        MinecraftClient.getInstance().close();
        throw new RuntimeException("[MirrorTree]客户端更新完成，请重启客户端");
    }

    public static void downloadFile(String modID, String fileURL, String saveDir) throws IOException {
        URL url = new URL(fileURL);
        try (InputStream in = url.openStream()) {
            Path targetPath = Paths.get(saveDir).resolve(modID+".jar");
            Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
