package org.example.smartglasses.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * Expansion card allowing OC2R computers to safely fetch scripts from Pastebin and GitHub.
 */
public class PastebinCardItem extends Item {
    public PastebinCardItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        tooltipComponents.add(
                Component.translatable("tooltip.smartglasses.pastebin_card")
                        .withStyle(ChatFormatting.GRAY)
        );
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
