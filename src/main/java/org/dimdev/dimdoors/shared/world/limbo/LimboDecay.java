package org.dimdev.dimdoors.shared.world.limbo;

import net.minecraft.block.Block;
import net.minecraft.block.BlockContainer;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.ForgeChunkManager;
import net.minecraftforge.fml.common.registry.ForgeRegistries;
import org.dimdev.dimdoors.DimDoors;
import org.dimdev.dimdoors.shared.ModConfig;
import org.dimdev.dimdoors.shared.blocks.ModBlocks;
import org.dimdev.dimdoors.shared.world.ModDimensions;

import java.util.ArrayList;
import java.util.Random;

/**
 * Provides methods for applying Limbo decay. Limbo decay refers to the effect that most blocks placed in Limbo
 * naturally change into stone, then cobble, then gravel, and finally Unraveled Fabric as time passes.
 */
public final class LimboDecay {

    private static final int MAX_DECAY_SPREAD_CHANCE = 100;
    private static final int DECAY_SPREAD_CHANCE = 50;
    private static final int CHUNK_SIZE = 16;
    private static final int SECTION_HEIGHT = 16;

    //Provides a reversed list of the block IDs that blocks cycle through during decay.
    private static IBlockState[] decaySequence = null;

    private static final Random random = new Random();
    public static Block[] blocksImmuneToDecay = null;

    public static IBlockState[] getDecaySequence() {
        if (decaySequence == null) {
            decaySequence = new IBlockState[]{
                    ModBlocks.UNRAVELLED_FABRIC.getDefaultState(),
                    Blocks.GRAVEL.getDefaultState(),
                    Blocks.COBBLESTONE.getDefaultState(),
                    Blocks.STONE.getDefaultState()
            };
        }

        return decaySequence;
    }

    public static Block[] getBlocksImmuneToDecay() {
        if (blocksImmuneToDecay == null) {
            ArrayList<Block> temp = new ArrayList<>();
            temp.add(ModBlocks.UNRAVELLED_FABRIC);
            temp.add(ModBlocks.ETERNAL_FABRIC);
            temp.add(ModBlocks.DIMENSIONAL_PORTAL);
            temp.add(ModBlocks.IRON_DIMENSIONAL_DOOR);
            temp.add(ModBlocks.WARP_DIMENSIONAL_DOOR);
            temp.add(ModBlocks.RIFT);
            temp.add(ModBlocks.GOLD_DOOR);
            temp.add(ModBlocks.QUARTZ_DOOR);
            temp.add(ModBlocks.GOLD_DIMENSIONAL_DOOR);
            for(String blocked : ModConfig.limbo.blockDecayLimboBlackList) {
                Block fromString = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(blocked));
                if(fromString!=null) temp.add(fromString);
                else DimDoors.log.error("Invalid block name for limbo decay blacklist! \""+blocked+"\"");
            }
            blocksImmuneToDecay = temp.toArray(new Block[0]);
        }

        return blocksImmuneToDecay;
    }

    /**
     * Checks the blocks orthogonally around a given location (presumably the location of an Unraveled Fabric block)
     * and applies Limbo decay to them. This gives the impression that decay spreads outward from Unraveled Fabric.
     */
    public static void applySpreadDecay(World world, BlockPos pos) {
        //Check if we randomly apply decay spread or not. This can be used to moderate the frequency of
        //full spread decay checks, which can also shift its performance impact on the game.
        if (random.nextInt(MAX_DECAY_SPREAD_CHANCE) < DECAY_SPREAD_CHANCE) {
            //Apply decay to the blocks above, below, and on all four sides.
            //World.getBlockId() implements bounds checking, so we don't have to worry about reaching out of the world
            decayBlock(world, pos.up());
            decayBlock(world, pos.down());
            decayBlock(world, pos.north());
            decayBlock(world, pos.south());
            decayBlock(world, pos.west());
            decayBlock(world, pos.east());
        }
    }

    /**
     * Picks random blocks from each active chunk in Limbo and, if decay is applicable, converts them directly to Unraveled Fabric.
     * This decay method is designed to stop players from avoiding Limbo decay by building floating structures.
     */
    public static void applyRandomFastDecay() {
        int sectionY;
        int limboHeight;
        int[] limbo = DimensionManager.getDimensions(ModDimensions.LIMBO);

        for (int i : limbo) {
            World world = DimensionManager.getWorld(i);

            limboHeight = world.getHeight();

            //Obtain the coordinates of active chunks in Limbo. For each section of each chunk,
            //pick a random block and try to apply fast decay.
            for (ChunkPos chunkPos : ForgeChunkManager.getPersistentChunksFor(world).keySet()) {
                //Loop through each chunk section and fast-decay a random block
                //Apply the changes using the world object instead of directly to the chunk so that clients are always notified.
                for (sectionY = 0; sectionY < limboHeight; sectionY += SECTION_HEIGHT) {
                    BlockPos pos = new BlockPos(
                            chunkPos.x * CHUNK_SIZE + random.nextInt(CHUNK_SIZE),
                            chunkPos.z * CHUNK_SIZE + random.nextInt(CHUNK_SIZE),
                            sectionY + random.nextInt(SECTION_HEIGHT));
                    decayBlockFast(world, pos);
                }
            }
        }
    }

    /**
     * Checks if a block can be decayed and, if so, changes it directly into Unraveled Fabric.
     */
    private static boolean decayBlockFast(World world, BlockPos pos) {
        IBlockState block = world.getBlockState(pos);
        if (canDecayBlock(block, world, pos)) {
            if (block.isNormalCube()) {
                world.setBlockState(pos, ModBlocks.UNRAVELLED_FABRIC.getDefaultState());
            } else {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if a block can be decayed and, if so, changes it to the next block ID along the decay sequence.
     */
    private static boolean decayBlock(World world, BlockPos pos) {
        int index;
        IBlockState block = world.getBlockState(pos);
        if (canDecayBlock(block, world, pos)) {
            //Loop over the block IDs that decay can go through.
            //Find an index matching the current blockID, if any.
            if (block.isNormalCube()) {
                for (index = 0; index < getDecaySequence().length; index++) {
                    if (getDecaySequence()[index].equals(block)) {
                        break;
                    }
                }

                //Since the decay sequence is a reversed list, the block ID in the index before our match
                //is the block ID we should change this block into. A trick in this approach is that if
                //we loop over the array without finding a match, then (index - 1) will contain the
                //last ID in the array, which is the first one that all blocks decay into.
                //We assume that Unraveled Fabric is NOT decayable. Otherwise, this will go out of bounds!

                world.setBlockState(pos, getDecaySequence()[index - 1]);
            } else {
                world.setBlockState(pos, Blocks.AIR.getDefaultState());
            }
            return true;
        }
        return false;
    }

    /**
     * Checks if a block can decay. We will not decay air, certain DD blocks, containers, or blacklisted blocks.
     */
    private static boolean canDecayBlock(IBlockState state, World world, BlockPos pos) {
        if (world.isAirBlock(pos)) {
            return false;
        }

        for (int k = 0; k < getBlocksImmuneToDecay().length; k++) {
            if (state.getBlock().equals(getBlocksImmuneToDecay()[k])) {
                return false;
            }
        }

        return !(state instanceof BlockContainer);
    }
}
