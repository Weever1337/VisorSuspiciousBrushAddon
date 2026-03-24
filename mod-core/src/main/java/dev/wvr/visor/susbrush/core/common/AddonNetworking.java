package dev.wvr.visor.susbrush.core.common;

import dev.wvr.visor.susbrush.core.common.network.NetworkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class AddonNetworking {
    public static final ResourceLocation BRUSH_BLOCK_C2S = new ResourceLocation(VRSusBrush.MOD_ID, "brush_block_c2s");

    public static void initCommon() {
        NetworkHelper.registerServerReceiver(BRUSH_BLOCK_C2S, (buf, player) -> {
            InteractionHand hand = buf.readEnum(InteractionHand.class);
            BlockPos pos = buf.readBlockPos();
            Direction hitDirection = buf.readEnum(Direction.class);
            ServerLevel level = (ServerLevel) player.level();
            ItemStack heldItem = player.getItemInHand(hand);
            BlockState state = level.getBlockState(pos);

            if (!(heldItem.getItem() instanceof BrushItem)) {
                return;
            }

            if (!(state.getBlock() instanceof BrushableBlock brushableBlock)) {
                return;
            }

            if (!(level.getBlockEntity(pos) instanceof BrushableBlockEntity brushableBlockEntity)) {
                return;
            }

            if (player.position().distanceToSqr(Vec3.atCenterOf(pos)) > AddonUtils.MAX_BRUSH_DISTANCE_SQR) {
                return;
            }

            level.playSound(player, pos, brushableBlock.getBrushSound(), SoundSource.BLOCKS);

            if (brushableBlockEntity.brush(level.getGameTime(), player, hitDirection)) {
                heldItem.hurtAndBreak(1, player, brokenPlayer -> brokenPlayer.broadcastBreakEvent(hand));
            }
        });
    }
}
