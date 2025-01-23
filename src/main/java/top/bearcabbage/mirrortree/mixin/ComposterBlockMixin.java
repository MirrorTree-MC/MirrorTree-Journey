package top.bearcabbage.mirrortree.mixin;

import net.minecraft.block.ComposterBlock;
import net.minecraft.item.ItemConvertible;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static net.minecraft.block.ComposterBlock.ITEM_TO_LEVEL_INCREASE_CHANCE;

@Mixin(ComposterBlock.class)
public class ComposterBlockMixin {

    // 降低苔藓块堆肥效率
    @Inject(method = "registerCompostableItem", at = @At("HEAD"), cancellable = true)
    private static void registerCompostableItem(float levelIncreaseChance, ItemConvertible item, CallbackInfo ci) {
        if (item == Items.MOSS_BLOCK) {
            ITEM_TO_LEVEL_INCREASE_CHANCE.put(item.asItem(), 0.2F);
            ci.cancel();
        }
    }
}
