package org.example.smartglasses.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.example.smartglasses.SmartGlasses;
import org.example.smartglasses.blockentity.GlassesBridgeBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Block that connects to an OC2R bus cable and bridges HUD/AR data to bound Smart Glasses.
 */
public class GlassesBridgeBlock extends BaseEntityBlock {
    public static final MapCodec<GlassesBridgeBlock> CODEC = simpleCodec(GlassesBridgeBlock::new);

    public GlassesBridgeBlock(Properties properties) {
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
        return new GlassesBridgeBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return createTickerHelper(type, SmartGlasses.GLASSES_BRIDGE_BE.get(),
                (lvl, pos, st, be) -> be.serverTick());
    }
}
