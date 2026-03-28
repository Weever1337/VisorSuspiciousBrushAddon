package dev.wvr.visor.susbrush.core.client.tasks;

import dev.wvr.visor.susbrush.core.common.AddonNetworking;
import dev.wvr.visor.susbrush.core.common.AddonUtils;
import dev.wvr.visor.susbrush.core.common.network.NetworkHelper;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.BrushItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BrushableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.tasks.RegisterVisorTask;
import org.vmstudio.visor.api.client.tasks.TaskType;
import org.vmstudio.visor.api.client.tasks.VisorTask;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.addon.VisorAddon;

import java.util.EnumMap;

@RegisterVisorTask
public class TaskBrushSuspiciousBlock extends VisorTask {
    public static final String ID = "brush_suspicious_block";
    private final EnumMap<HandType, BrushHandState> brushHandStates = new EnumMap<>(HandType.class);

    public TaskBrushSuspiciousBlock(@NotNull VisorAddon owner) {
        super(owner);
        for (HandType handType : HandType.values()) {
            brushHandStates.put(handType, new BrushHandState());
        }
    }

    @Override
    protected void onRun(@Nullable LocalPlayer player) {
        Minecraft minecraft = Minecraft.getInstance();
        if (player == null || minecraft.level == null || minecraft.isPaused()) return;

        for (HandType handType : HandType.values()) {
            tickHand(minecraft.level, player, handType);
        }
    }

    private void tickHand(Level level, LocalPlayer player, HandType handType) {
        BrushHandState handState = brushHandStates.get(handType);
        InteractionHand interactionHand = toInteractionHand(handType);
        ItemStack heldItem = player.getItemInHand(interactionHand);

        if (!(heldItem.getItem() instanceof BrushItem brushItem) || !isUseActionPressed(handType)) {
            handState.reset();
            return;
        }

        var poseData = VisorAPI.client().getVRLocalPlayer().getPoseData(PlayerPoseType.RELATIVE);
        var handPose = handType == HandType.MAIN ? poseData.getMainHand() : poseData.getOffhand();
        Vec3 handDirection = normalizeOrZero(handPose.getDirection());
        Vec3 brushTip = toVec3(handPose.getCustomVector(new Vector3f(AddonUtils.BRUSH_TIP_OFFSET))).add(toVec3(handPose.getPosition()));

        if (handDirection.lengthSqr() < 1.0E-6D) {
            handState.reset();
            return;
        }

        Vec3 prevBrushTip = handState.lastBrushTip;
        handState.lastBrushTip = brushTip;

        BrushContact contact = findBrushContact(level, brushTip);
        if (contact == null) {
            handState.reset();
            return;
        }

        if (!contact.pos().equals(handState.targetPos)) {
            handState.startTracking(contact.pos(), contact.face(), brushTip);
            return;
        }

        Direction trackedFace = handState.hitFace != null ? handState.hitFace : contact.face();
        handState.hitFace = trackedFace;

        if (prevBrushTip != null) {
            Vec3 tipDelta = brushTip.subtract(prevBrushTip);
            double surfaceMovement = projectOntoFace(tipDelta, trackedFace).length();
            if (surfaceMovement >= AddonUtils.MIN_SURFACE_MOVEMENT) {
                handState.accumulatedMovement = Math.min(AddonUtils.REQUIRED_SURFACE_MOVEMENT, handState.accumulatedMovement + surfaceMovement);
            }
        }

        if (handState.accumulatedMovement < AddonUtils.REQUIRED_SURFACE_MOVEMENT) {
            return;
        }

        long gameTime = level.getGameTime();
        if (gameTime - handState.lastBrushTick < AddonUtils.BRUSH_INTERVAL_TICKS) {
            return;
        }

        handState.accumulatedMovement = 0.0D;
        handState.lastBrushTick = gameTime;

        BlockHitResult hitResult = new BlockHitResult(contact.hitLocation(), contact.face(), contact.pos(), false);
        brushItem.spawnDustParticles(level, hitResult, contact.state(), handDirection, getHumanoidArm(player, interactionHand));
        playBrushSound(level, contact);
        VisorAPI.client().getInputManager().triggerHapticPulse(handType, 0.08F);
        if (isBrushable(contact.state())) {
            sendBrushPacket(interactionHand, contact.pos(), trackedFace, contact.hitLocation());
        }
    }

    private BrushContact findBrushContact(Level level, Vec3 brushTip) {
        BlockPos centerPos = BlockPos.containing(brushTip.x, brushTip.y, brushTip.z);
        BrushContact bestContact = null;
        double bestDistanceSqr = AddonUtils.MAX_TIP_TO_BLOCK_DISTANCE_SQR;

        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) { // bro wtf are you doing here?
                    BlockPos pos = centerPos.offset(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }

                    VoxelShape shape = state.getShape(level, pos);
                    if (shape.isEmpty()) {
                        continue;
                    }

                    AABB blockBounds = shape.bounds().move(pos);
                    Vec3 hitLocation = new Vec3(
                            clamp(brushTip.x, blockBounds.minX, blockBounds.maxX),
                            clamp(brushTip.y, blockBounds.minY, blockBounds.maxY),
                            clamp(brushTip.z, blockBounds.minZ, blockBounds.maxZ)
                    );
                    double distanceSqr = brushTip.distanceToSqr(hitLocation);
                    if (distanceSqr > bestDistanceSqr) {
                        continue;
                    }

                    bestDistanceSqr = distanceSqr;
                    bestContact = new BrushContact(pos.immutable(), state, findNearestFace(blockBounds, brushTip), hitLocation);
                }
            }
        }

        return bestContact;
    }

    private Direction findNearestFace(AABB bounds, Vec3 point) {
        double west = Math.abs(point.x - bounds.minX);
        double east = Math.abs(point.x - bounds.maxX);
        double down = Math.abs(point.y - bounds.minY);
        double up = Math.abs(point.y - bounds.maxY);
        double north = Math.abs(point.z - bounds.minZ);
        double south = Math.abs(point.z - bounds.maxZ);

        Direction bestFace = Direction.WEST;
        double bestDistance = west;

        if (east < bestDistance) {
            bestDistance = east;
            bestFace = Direction.EAST;
        }
        if (down < bestDistance) {
            bestDistance = down;
            bestFace = Direction.DOWN;
        }
        if (up < bestDistance) {
            bestDistance = up;
            bestFace = Direction.UP;
        }
        if (north < bestDistance) {
            bestDistance = north;
            bestFace = Direction.NORTH;
        }
        if (south < bestDistance) {
            bestFace = Direction.SOUTH;
        }

        return bestFace;
    }

    private boolean isUseActionPressed(HandType handType) {
        String actionId = handType == HandType.MAIN ? "mouse_right_main" : "mouse_right_offhand";
        return VisorAPI.client().getInputManager().getActiveSet().getAction(actionId).isActive();
    }

    private Vec3 projectOntoFace(Vec3 movement, Direction face) {
        Vec3 normal = Vec3.atLowerCornerOf(face.getNormal());
        return movement.subtract(normal.scale(movement.dot(normal)));
    }

    private void playBrushSound(Level level, BrushContact contact) {
        SoundEvent sound = isBrushable(contact.state()) ? ((BrushableBlock) contact.state().getBlock()).getBrushSound() : SoundEvents.BRUSH_GENERIC;
        level.playLocalSound(contact.hitLocation().x, contact.hitLocation().y, contact.hitLocation().z, sound, SoundSource.BLOCKS, 1.0F, 1.0F, false);
    }
    private boolean isBrushable(BlockState state) {
        return state.getBlock() instanceof BrushableBlock;
    }

    private void sendBrushPacket(InteractionHand hand, BlockPos pos, Direction hitFace, Vec3 hitLocation) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeEnum(hand);
        buf.writeBlockPos(pos);
        buf.writeEnum(hitFace);
        buf.writeDouble(hitLocation.x);
        buf.writeDouble(hitLocation.y);
        buf.writeDouble(hitLocation.z);
        NetworkHelper.sendToServer(AddonNetworking.BRUSH_BLOCK_C2S, buf);
    }

    private InteractionHand toInteractionHand(HandType handType) {
        return handType == HandType.MAIN ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    private HumanoidArm getHumanoidArm(LocalPlayer player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
    }

    private Vec3 toVec3(Vector3fc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private Vec3 normalizeOrZero(Vector3fc vector) {
        Vector3f normalized = new Vector3f(vector);
        if (normalized.lengthSquared() < 1.0E-6F) {
            return Vec3.ZERO;
        }

        normalized.normalize();
        return new Vec3(normalized.x(), normalized.y(), normalized.z());
    }

    @Override
    protected void onClear(@Nullable LocalPlayer player) {
        brushHandStates.values().forEach(BrushHandState::reset);
    }

    @Override
    public boolean isActive(@Nullable LocalPlayer player) {
        return true;
    }

    @Override
    public @NotNull TaskType getType() {
        return TaskType.VR_PLAYER_TICK;
    }

    @Override
    public @NotNull String getId() {
        return ID;
    }

    private static final class BrushHandState {
        private BlockPos targetPos;
        private Direction hitFace;
        private Vec3 lastBrushTip = null;
        private double accumulatedMovement;
        private long lastBrushTick = -AddonUtils.BRUSH_INTERVAL_TICKS;

        private void startTracking(BlockPos pos, Direction face, Vec3 brushTip) {
            targetPos = pos.immutable();
            hitFace = face;
            lastBrushTip = brushTip;
            accumulatedMovement = 0.0D;
        }

        private void reset() {
            targetPos = null;
            hitFace = null;
            lastBrushTip = null;
            accumulatedMovement = 0.0D;
            lastBrushTick = -AddonUtils.BRUSH_INTERVAL_TICKS;
        }
    }

    private record BrushContact(BlockPos pos, BlockState state, Direction face, Vec3 hitLocation) { }
}
