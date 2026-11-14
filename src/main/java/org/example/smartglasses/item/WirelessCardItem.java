package org.example.smartglasses.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import org.example.smartglasses.SmartGlasses;

import java.util.List;
import java.util.UUID;

/**
 * Expansion card providing range-based wireless packet messaging for OC2R computers and robots.
 */
public class WirelessCardItem extends Item {
    public WirelessCardItem(Properties properties) {
        super(properties);
    }

    public static UUID getOrCreateAddress(ItemStack stack) {
        UUID addr = stack.get(SmartGlasses.WIRELESS_ADDRESS.get());
        if (addr == null) {
            addr = UUID.randomUUID();
            stack.set(SmartGlasses.WIRELESS_ADDRESS.get(), addr);
        }
        return addr;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        UUID addr = stack.get(SmartGlasses.WIRELESS_ADDRESS.get());
        if (addr != null) {
            tooltipComponents.add(
                    Component.translatable("tooltip.smartglasses.wireless_address", addr.toString().substring(0, 8))
                            .withStyle(ChatFormatting.AQUA)
            );
        } else {
            tooltipComponents.add(
                    Component.translatable("tooltip.smartglasses.wireless_card")
                            .withStyle(ChatFormatting.GRAY)
            );
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
