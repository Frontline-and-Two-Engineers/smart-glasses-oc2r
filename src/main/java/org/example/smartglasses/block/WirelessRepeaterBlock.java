package org.example.smartglasses.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.example.smartglasses.blockentity.WirelessRepeaterBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Energy-powered Wireless Repeater block that relays wireless_card packets.
 */
public class WirelessRepeaterBlock extends BaseEntityBlock {
    public static final MapCodec<WirelessRepeaterBlock> CODEC = simpleCodec(WirelessRepeaterBlock::new);

    public WirelessRepeaterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new WirelessRepeaterBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (!level.isClientSide && level.getBlockEntity(pos) instanceof WirelessRepeaterBlockEntity repeater) {
            var energy = repeater.getEnergyStorage();
            player.displayClientMessage(
                    Component.translatable("message.smartglasses.repeater_energy", energy.getEnergyStored(), energy.getMaxEnergyStored())
                            .withStyle(ChatFormatting.AQUA),
                    true
            );
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
