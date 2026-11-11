package com.mitchej123.hodgepodge.mixins.early.minecraft.fastload;

import net.minecraft.block.Block;
import net.minecraft.world.World;
import net.minecraft.world.WorldProvider;
import net.minecraft.world.chunk.Chunk;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.mitchej123.hodgepodge.mixins.hooks.ChunkGenScheduler;

/**
 * With throttled chunk generation, chunks are sent to the client before they are populated, and after a population only
 * the populated chunk is resent. Worldgen that writes into a neighbor chunk without notifying the client (flag 0, e.g.
 * RWG trees) leaves the client with a stale copy of that neighbor: trees appear cut off at chunk borders until the
 * chunk is reloaded. Queue the block update that vanilla would send with flag 2.
 */
@Mixin(World.class)
public abstract class MixinWorld_SyncPopulationWrites {

    @Shadow
    @Final
    public WorldProvider provider;

    @Shadow
    public boolean isRemote;

    @Shadow
    public abstract Chunk getChunkFromChunkCoords(int x, int z);

    @Shadow
    public abstract void markBlockForUpdate(int x, int y, int z);

    @Inject(method = "setBlock(IIILnet/minecraft/block/Block;II)Z", at = @At("RETURN"))
    private void syncNeighborWriteDuringPopulation(int x, int y, int z, Block block, int meta, int flags,
            CallbackInfoReturnable<Boolean> cir) {
        if (isRemote || !cir.getReturnValueZ()) return;
        final ChunkGenScheduler scheduler = ChunkGenScheduler.getForDimension(provider.dimensionId);
        if (scheduler == null || !scheduler.isPopulatingNeighborOf(x >> 4, z >> 4)) return;
        // vanilla already queued it
        if ((flags & 2) != 0 && getChunkFromChunkCoords(x >> 4, z >> 4).func_150802_k()) return;
        markBlockForUpdate(x, y, z);
    }
}
