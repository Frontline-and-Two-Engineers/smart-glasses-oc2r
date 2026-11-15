package org.example.smartglasses.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.example.smartglasses.blockentity.RadarBlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Tiered Entity Radar block connected to the OC2R bus.
 */
public class RadarBlock extends BaseEntityBlock {
    public static final MapCodec<RadarBlock> CODEC = RecordCodecBuilder.mapCodec(inst ->
            inst.group(
                    Codec.INT.fieldOf("tier").forGetter(RadarBlock::getTier),
                    propertiesCodec()
            ).apply(inst, RadarBlock::new)
    );

    private final int tier;

    public RadarBlock(int tier, Properties properties) {
        super(properties);
        this.tier = Math.clamp(tier, 1, 3);
    }

    public int getTier() {
        return tier;
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
        return new RadarBlockEntity(pos, state);
    }
}
