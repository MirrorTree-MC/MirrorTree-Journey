package top.bearcabbage.mirrortree;

import net.fabricmc.api.ModInitializer;

import net.fabricmc.fabric.api.entity.event.v1.EntitySleepEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.minecraft.block.BedBlock;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Text;
import net.minecraft.text.Texts;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;

public class MirrorTree implements ModInitializer {
	public static final String MOD_ID = "mirrortree";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// This code runs as soon as Minecraft is in a mod-load-ready state.
		// However, some things (like resources) may still be uninitialized.
		// Proceed with mild caution.

		EntitySleepEvents.START_SLEEPING.register((entity, sleepingLocation) -> {
			entity.sendMessage(Texts.bracketed(Text.literal("[HHHor]离梦").styled((style) -> style.withColor(Formatting.GREEN).withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/wakeup")).withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal("点击在卧室中醒来"))))));
		});
		AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
			if (world.isClient) return ActionResult.SUCCESS;
			if (world.getRegistryKey().getRegistry().equals(Identifier.of(MOD_ID,"bedroom"))) {
				if (world.getBlockState(pos).getBlock() instanceof BedBlock) {
					// add Dreaming method
					return ActionResult.SUCCESS;
				}
			}
            return ActionResult.PASS;
        });

	}

	private static class Dream
}