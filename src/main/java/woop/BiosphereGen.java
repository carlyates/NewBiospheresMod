package woop;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import akka.dispatch.sysmsg.Create;
import akka.japi.Creator;
import akka.japi.Predicate;
import scala.tools.nsc.doc.model.Public;
import net.minecraft.block.Block;
import net.minecraft.block.BlockSand;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EnumCreatureType;
import net.minecraft.init.Blocks;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IProgressUpdate;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.SpawnerAnimals;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.IChunkProvider;
import net.minecraft.world.gen.MapGenBase;
import net.minecraft.world.gen.NoiseGeneratorOctaves;
import net.minecraft.world.gen.feature.WorldGenBigMushroom;
import net.minecraft.world.gen.feature.WorldGenCactus;
import net.minecraft.world.gen.feature.WorldGenClay;
import net.minecraft.world.gen.feature.WorldGenFire;
import net.minecraft.world.gen.feature.WorldGenFlowers;
import net.minecraft.world.gen.feature.WorldGenMinable;
import net.minecraft.world.gen.feature.WorldGenPumpkin;
import net.minecraft.world.gen.feature.WorldGenReed;
import net.minecraft.world.gen.feature.WorldGenTallGrass;
import net.minecraft.world.gen.feature.WorldGenerator;

public class BiosphereGen implements IChunkProvider
{
	private static final File cfgFile = new File(Minecraft.getMinecraft().mcDataDir, "/config/Biosphere.cfg");

	public final World world;

	public static final int GRID_SIZE;
	private static final int BRIDGE_SIZE;
	public static final int SPECIAL_RADIUS;
	public static final int LAVA_LEVEL;
	public static final Block DOME_TYPE;
	public static final Block BRIDGE_SUPPORT;
	public static final Block BRIDGE_RAIL;
	public static final boolean NOISE;
	public static final boolean ENABLED;
	public static final boolean TALLGRASS;
	public static final boolean WATERWORLD;

	public static final float SCALE = 1.0F;
	public static final int SCALE_GRID;
	public static final int SCALED_SPECIAL;

	public static final int WORLD_HEIGHT = 128;
	public static final int WORLD_MAXY = WORLD_HEIGHT - 1;
	public static final int SEA_LEVEL = 63;

	public static final double MIN_RADIUS = 20;
	public static final double MAX_RADIUS = 50;

	public static final double MIN_LAKE_RATIO = 0.3d;
	public static final double MAX_LAKE_RATIO = 0.6d;

	/**
	 * Get whether the map features (e.g. strongholds) generation is enabled or disabled.
	 */
	public boolean getMapFeaturesEnabled()
	{
		return world.getWorldInfo().isMapFeaturesEnabled();
	}

	// public final Random rndSphere;
	// public final Random rndNoise;

	private MapGenBase caveGen = new BiosphereCaveGen();
	//
	private NoiseGeneratorOctaves noiseGen;
	// public double noiseMin = Double.MAX_VALUE;
	// public double noiseMax = Double.MIN_VALUE;
	// public double[] noise = new double[256];

	public static final int zShift = 7;
	public static final int xShift = 11;
	private final long worldSeed;

	public class SphereChunk
	{
		public final int chunkX, chunkZ;

		public final World world;
		public final ChunkCoordinates location;
		public final ChunkCoordinates oreLocation;
		public final ChunkCoordinates lakeLocation;

		private final long worldSeed;
		private final long seed;

		public final double radius;

		public final double lakeRadius;
		public final double lakeEdgeRadius;

		public final boolean lavaLake;
		public final boolean hasLake;

		public final BiomeGenBase biome;

		public SphereChunk(World world, long worldSeed, int chunkX, int chunkZ)
		{
			this.chunkX = chunkX;
			this.chunkZ = chunkZ;

			this.world = world;
			this.worldSeed = worldSeed;

			// Set sphere location
			this.location = GetSphereCenter(world, chunkX, chunkZ);

			// Seed local random number generator
			Random rnd = new Random(worldSeed);
			long xm = rnd.nextLong() / 2L * 2L + 1L;
			long zm = rnd.nextLong() / 2L * 2L + 1L;
			long _seed = ((long)location.posX * xm + (long)location.posZ * zm) * 2512576L ^ worldSeed;
			rnd.setSeed(_seed);

			double minRad = MIN_RADIUS * SCALE;
			double maxRad = MAX_RADIUS * SCALE;

			double radRange = (maxRad - minRad);

			// Get sphere radius
			this.radius = Math.round(minRad + (rnd.nextDouble() * radRange));

			// Get lake radius
			double lakeRatio = MIN_LAKE_RATIO + ((MAX_LAKE_RATIO - MIN_LAKE_RATIO) * rnd.nextDouble());
			this.lakeRadius = (double)Math.round(this.radius * lakeRatio);
			this.lakeEdgeRadius = lakeRadius + 2.0d;

			this.biome = world.getWorldChunkManager().getBiomeGenAt(location.posX, location.posZ);

			this.lavaLake = this.biome == BiomeGenBase.hell || this.biome != BiomeGenBase.swampland
					&& this.biome != BiomeGenBase.taiga && this.biome != BiomeGenBase.icePlains
					&& this.biome != BiomeGenBase.sky && rnd.nextInt(10) == 0;
			this.hasLake = this.biome == BiomeGenBase.swampland || this.biome != BiomeGenBase.sky
					&& rnd.nextInt(2) == 0;

			oreLocation = new ChunkCoordinates();
			oreLocation.posY = SCALED_SPECIAL + 1 + rnd.nextInt(WORLD_MAXY - (SCALED_SPECIAL + 1));
			oreLocation.posX = this.location.posX + SCALE_GRID / 2 * 16 - SCALED_SPECIAL;
			oreLocation.posZ = this.location.posZ + SCALE_GRID / 2 * 16 - SCALED_SPECIAL;

			lakeLocation = new ChunkCoordinates();
			lakeLocation.posX = location.posX;
			lakeLocation.posY = location.posY;
			lakeLocation.posZ = location.posZ;

			// if (NOISE)
			// {
			// this.setNoise(this.midX >> 4, this.midZ >> 4);
			// this.noiseMin = Double.MAX_VALUE;
			//
			// for (int k = 0; k < this.noise.length; ++k)
			// {
			// if (this.noise[k] < this.noiseMin)
			// {
			// this.noiseMin = this.noise[k];
			// }
			// }
			//
			// lake.posY = (int)Math.round(seaLevel + this.noiseMin * 8.0D * 1.0D);
			// this.setNoise(chunkX, chunkZ);
			// }

			// Reseed random generator
			xm = rnd.nextLong() / 2L * 2L + 1L;
			zm = rnd.nextLong() / 2L * 2L + 1L;
			this.seed = ((long)chunkX * xm + (long)chunkZ * zm) * 3168045L ^ worldSeed;
		}

		public Random GetPhaseRandom(String phase)
		{
			Random rnd = new Random(this.seed);

			long xm = rnd.nextLong() / 2L * 2L + 1L;
			long zm = rnd.nextLong() / 2L * 2L + 1L;

			long _seed = ((long)chunkX * xm + (long)chunkZ * zm) * (long)phase.hashCode() ^ worldSeed;

			rnd.setSeed(_seed);
			return rnd;
		}

		private ChunkCoordinates GetSphereCenter(World word, int chunkX, int chunkZ)
		{
			int chunkOffsetToCenterX = -(int)Math.floor(Math.IEEEremainder((double)chunkX, (double)SCALE_GRID));
			int chunkOffsetToCenterZ = -(int)Math.floor(Math.IEEEremainder((double)chunkZ, (double)SCALE_GRID));

			ChunkCoordinates cc = new ChunkCoordinates();

			cc.posX = ((chunkX + chunkOffsetToCenterX) << 4) + 8;
			cc.posY = SEA_LEVEL; // getSurfaceLevel(8, 8);
			cc.posZ = ((chunkZ + chunkOffsetToCenterZ) << 4) + 8;

			return cc;
		}

		public double getMainDistance(int rawX, int rawY, int rawZ)
		{
			return (double)Math.round(getDistance(
				(double)rawX,
				(double)rawY,
				(double)rawZ,
				(double)this.location.posX,
				(double)this.location.posY,
				(double)this.location.posZ));
		}

		public double getOreDistance(int rawX, int rawY, int rawZ)
		{
			return (double)Math.round(getDistance(
				(double)rawX,
				(double)rawY,
				(double)rawZ,
				(double)this.oreLocation.posX,
				(double)this.oreLocation.posY,
				(double)this.oreLocation.posZ));
		}

		public int getSurfaceLevel(int x, int z)
		{
			return SEA_LEVEL;
			// return NOISE ? (int)Math.round(seaLevel + this.noise[z + (x * 16)] * 8.0D * scale) : seaLevel;
		}
	}

	private LruCacheList<SphereChunk> chunks = new LruCacheList<SphereChunk>(10);

	public synchronized SphereChunk GetSphereChunk(final int chunkX, final int chunkZ)
	{
		return chunks.FindOrAdd(new Predicate<SphereChunk>()
		{
			public boolean test(SphereChunk chunk)
			{
				return chunk.chunkX == chunkX && chunk.chunkZ == chunkZ;
			}
		}, new Creator<SphereChunk>()
		{
			public SphereChunk create()
			{
				return new SphereChunk(world, worldSeed, chunkX, chunkZ);
			}
		});
	}

	// private LinkedList<SphereChunk> chunks = new LinkedList<SphereChunk>();
	//
	// public synchronized SphereChunk GetSphereChunk(int chunkX, int chunkZ)
	// {
	// boolean first = true;
	// for (SphereChunk chunk: chunks)
	// {
	// if (chunk != null)
	// {
	// if (chunk.chunkX == chunkX && chunk.chunkZ == chunkZ)
	// {
	// if (!first)
	// {
	// // move element to the front of the list
	// chunks.remove(chunk);
	// chunks.push(chunk);
	// }
	//
	// return chunk;
	// }
	// }
	//
	// first = false;
	// }
	//
	// SphereChunk chunk = new SphereChunk(this.world, this.worldSeed, chunkX, chunkZ);
	//
	// chunks.push(chunk);
	// while (chunks.size() > 10)
	// {
	// chunks.removeLast();
	// }
	//
	// return chunk;
	// }

	public BiosphereGen(World world)
	{
		this.world = world;
		this.worldSeed = world.getSeed();

		// if (NOISE)
		// {
		// this.rndNoise = new Random(seed);
		// this.noiseGen = new NoiseGeneratorOctaves(this.rndNoise, 4);
		// }
		// else
		// {
		// this.rndNoise = null;
		// }
	}

	// public void setRand(int chunkX, int chunkZ)
	// {
	// ChunkCoordinates cc = GetSphereCenter(chunkX, chunkZ);
	//
	// this.midX = cc.posX;
	// this.midZ = cc.posZ;
	//
	// this.oreMidX = this.midX + this.scaledGrid / 2 * 16 - this.scaledSpecial;
	// this.oreMidZ = this.midZ + this.scaledGrid / 2 * 16 - this.scaledSpecial;
	//
	// this.rndSphere.setSeed(this.world.getSeed());
	// long l = this.rndSphere.nextLong() / 2L * 2L + 1L;
	// long l1 = this.rndSphere.nextLong() / 2L * 2L + 1L;
	// long l2 = ((long)this.midX * l + (long)this.midZ * l1) * 2512576L ^ this.world.getSeed();
	// this.rndSphere.setSeed(l2);
	//
	// this.sphereRadius = (double)((float)Math.round(16.0D + this.rndSphere.nextDouble() * 32.0D
	// + this.rndSphere.nextDouble() * 16.0D) * scale);
	// this.lakeRadius = (double)Math.round(this.sphereRadius / 4.0D);
	// this.lakeEdgeRadius = this.lakeRadius + 2.0D;
	// this.biome = this.world.getWorldChunkManager().getBiomeGenAt(chunkX << 4, chunkZ << 4);
	// this.lavaLake = this.biome == BiomeGenBase.hell || this.biome != BiomeGenBase.swampland
	// && this.biome != BiomeGenBase.taiga && this.biome != BiomeGenBase.icePlains
	// && this.biome != BiomeGenBase.sky && this.rndSphere.nextInt(10) == 0;
	// this.hasLake = this.biome == BiomeGenBase.swampland || this.biome != BiomeGenBase.sky
	// && this.rndSphere.nextInt(2) == 0;
	// this.oreMidY = this.scaledSpecial + 1 + this.rndSphere.nextInt(worldMaxY - (this.scaledSpecial + 1));
	//
	// if (NOISE)
	// {
	// this.setNoise(this.midX >> 4, this.midZ >> 4);
	// this.noiseMin = Double.MAX_VALUE;
	//
	// for (int k = 0; k < this.noise.length; ++k)
	// {
	// if (this.noise[k] < this.noiseMin)
	// {
	// this.noiseMin = this.noise[k];
	// }
	// }
	//
	// this.lakeMidY = (int)Math.round(seaLevel + this.noiseMin * 8.0D * 1.0D);
	// this.setNoise(chunkX, chunkZ);
	// }
	// else
	// {
	// this.lakeMidY = this.midY;
	// }
	// }
	//

	public void setNoise(int x, int z)
	{
		// if (NOISE)
		// {
		// double d = 0.0078125D;
		// this.noise = this.noiseGen.generateNoiseOctaves(this.noise, x * 16, worldHeight, z * 16, 16, 1, 16, d, 1.0D,
		// d);
		// }
	}

	public void preGenerateChunk(int chunkX, int chunkZ, Block[] blocks)
	{
		SphereChunk chunk = GetSphereChunk(chunkX, chunkZ);
		Random rnd = chunk.GetPhaseRandom("preGenerateChunk");

		int rawX = chunkX << 4;
		int rawZ = chunkZ << 4;

		for (int zo = 0; zo < 16; ++zo)
		{
			for (int xo = 0; xo < 16; ++xo)
			{
				int midY = chunk.getSurfaceLevel(xo, zo);

				for (int rawY = WORLD_MAXY; rawY >= 0; --rawY)
				{
					int idx = (xo << xShift) | (zo << zShift) | rawY;
					// Block block = (rawY <= (SEA_LEVEL - 10)) ? Blocks.water : Blocks.air;
					Block block = Blocks.air;

					double sphereDistance = chunk.getMainDistance(rawX + xo, rawY, rawZ + zo);
					double oreDistance = chunk.getOreDistance(rawX + xo, rawY, rawZ + zo);

					if (rawY > midY)
					{
						if (sphereDistance == chunk.radius)
						{
							if (rawY >= midY + 4 || Math.abs(rawX + xo - chunk.location.posX) > BRIDGE_SIZE
									&& Math.abs(rawZ + zo - chunk.location.posZ) > BRIDGE_SIZE)
							{
								block = DOME_TYPE;
							}
						}
						else if (chunk.hasLake && NOISE && chunk.biome != BiomeGenBase.desert
								&& (sphereDistance > chunk.lakeRadius && sphereDistance <= chunk.lakeEdgeRadius))
						{
							if (rawY == chunk.lakeLocation.posY)
							{
								block = chunk.biome.topBlock;
							}
							else if (rawY < chunk.lakeLocation.posY)
							{
								block = chunk.biome.fillerBlock;
							}
						}
						else if (chunk.hasLake && NOISE && chunk.biome != BiomeGenBase.desert
								&& sphereDistance <= chunk.lakeRadius)
						{
							if (rawY == chunk.lakeLocation.posY && chunk.biome == BiomeGenBase.icePlains)
							{
								block = Blocks.ice;
							}
							else if (rawY <= chunk.lakeLocation.posY)
							{
								block = (chunk.lavaLake ? Blocks.flowing_lava : Blocks.flowing_water);
							}
						}
						else if (WATERWORLD
								&& rawY <= midY + 4
								&& sphereDistance > chunk.radius
								&& (Math.abs(rawX + xo - chunk.location.posX) == BRIDGE_SIZE || Math.abs(rawZ + zo
										- chunk.location.posZ) == BRIDGE_SIZE))
						{
							block = DOME_TYPE;
						}
						else if (WATERWORLD
								&& rawY == midY + 4
								&& sphereDistance > chunk.radius
								&& (Math.abs(rawX + xo - chunk.location.posX) < BRIDGE_SIZE || Math.abs(rawZ + zo
										- chunk.location.posZ) < BRIDGE_SIZE))
						{
							block = DOME_TYPE;
						}
						else if (WATERWORLD
								&& rawY < midY + 4
								&& sphereDistance > chunk.radius
								&& (Math.abs(rawX + xo - chunk.location.posX) < BRIDGE_SIZE || Math.abs(rawZ + zo
										- chunk.location.posZ) < BRIDGE_SIZE))
						{
							block = Blocks.air;
						}
						else if (WATERWORLD && sphereDistance > chunk.radius)
						{
							block = Blocks.water;
						}
						else if (rawY == midY + 1
								&& sphereDistance > chunk.radius
								&& (Math.abs(rawX + xo - chunk.location.posX) == BRIDGE_SIZE || Math.abs(rawZ + zo
										- chunk.location.posZ) == BRIDGE_SIZE))
						{
							block = BRIDGE_RAIL;
						}
					}
					else if (sphereDistance == chunk.radius)
					{
						block = Blocks.stone;
					}
					else if (chunk.hasLake && chunk.biome != BiomeGenBase.desert && sphereDistance <= chunk.lakeRadius)
					{
						if (rawY == chunk.lakeLocation.posY && chunk.biome == BiomeGenBase.icePlains)
						{
							block = Blocks.ice;
						}
						else if (rawY <= chunk.lakeLocation.posY)
						{
							block = (chunk.lavaLake ? Blocks.flowing_lava : Blocks.flowing_water);
						}
					}
					else if (chunk.hasLake && rawY < chunk.lakeLocation.posY - 1 && chunk.biome != BiomeGenBase.desert
							&& sphereDistance <= chunk.lakeEdgeRadius)
					{
						block = (chunk.lavaLake ? Blocks.gravel : Blocks.sand);
					}
					else if (sphereDistance < chunk.radius)
					{
						if (rawY == midY)
						{
							block = chunk.biome.topBlock;
						}
						else if (rawY == midY - 1)
						{
							block = chunk.biome.fillerBlock;
						}
						else
						{
							block = Blocks.stone;
						}
					}
					else if (rawY == midY
							&& sphereDistance > chunk.radius
							&& (Math.abs(rawX + xo - chunk.location.posX) < BRIDGE_SIZE + 1 || Math.abs(rawZ + zo
									- chunk.location.posZ) < BRIDGE_SIZE + 1))
					{
						block = BRIDGE_SUPPORT;
					}
					else if (WATERWORLD && sphereDistance > chunk.radius)
					{
						block = Blocks.water;
					}

					if (oreDistance == (double)this.SCALED_SPECIAL + 1)
					{
						block = Blocks.glass;
					}
					else if (oreDistance <= (double)this.SCALED_SPECIAL)
					{
						int oreChance = rnd.nextInt(500);

						if (oreChance < 5) // 1%
						{
							block = Blocks.lapis_ore;
						}
						else if (oreChance < 10) // 1%
						{
							block = Blocks.emerald_ore;
						}
						else if (oreChance < 15) // 1%
						{
							block = Blocks.diamond_ore;
						}
						else if (oreChance < 25) // 2%
						{
							block = Blocks.iron_ore;
						}
						else if (oreChance < 35) // 2%
						{
							block = Blocks.gold_ore;
						}
						else if (oreChance < 50) // 3%
						{
							block = Blocks.coal_ore;
						}
						else if (oreChance < 65) // 3%
						{
							block = Blocks.redstone_ore;
						}
						else if (oreChance < 75) // 2%
						{
							block = Blocks.quartz_ore;
						}
						else if (oreChance < 175) // 20%
						{
							block = Blocks.gravel;
						}
						else if (oreChance < 190) // 3%
						{
							block = Blocks.lava;
						}
						else
						// 62%
						{
							block = Blocks.stone;
						}
					}

					blocks[idx] = block;
				}
			}
		}
	}

	public static final double getInverseDistance(double x1, double y1, double z1, double x2, double y2, double z2)
	{
		return Math.sqrt(-Math.pow(y2 - y1, 2.0D) + Math.pow(x2 - x1, 2.0D) + Math.pow(z2 - z1, 2.0D));
	}

	public static final double getDistance(double x1, double y1, double z1, double x2, double y2, double z2)
	{
		return Math.sqrt(Math.pow(y2 - y1, 2.0D) + Math.pow(x2 - x1, 2.0D) + Math.pow(z2 - z1, 2.0D));
	}

	/**
	 * loads or generates the chunk at the chunk location specified
	 */
	public Chunk loadChunk(int x, int z)
	{
		return this.provideChunk(x, z);
	}

	/**
	 * Will return back a chunk, if it doesn't exist and its not a MP client it will generates all the blocks for the
	 * specified chunk from the map seed and chunk seed
	 */
	public Chunk provideChunk(int x, int z)
	{
		// this.setRand(x, z);
		Block[] blocks = new Block[16 * 16 * WORLD_HEIGHT];

		this.preGenerateChunk(x, z, blocks);
		this.caveGen.func_151539_a(this, this.world, x, z, blocks); // func_151539_a == generate

		Chunk chunk = new Chunk(this.world, blocks, x, z);
		chunk.generateSkylightMap();

		return chunk;
	}

	/**
	 * Checks to see if a chunk exists at x, z
	 */
	public boolean chunkExists(int x, int z)
	{
		return true;
	}

	/**
	 * Populates chunk with ores etc etc
	 */
	public void populate(IChunkProvider chunkProvider, int chunkX, int chunkZ)
	{
		SphereChunk chunk = GetSphereChunk(chunkX, chunkZ);
		Random rnd = chunk.GetPhaseRandom("populate");

		BlockSand.fallInstantly = true;
		int absX = chunkX << 4;
		int absZ = chunkZ << 4;

		for (int i = 0; i < 10; i++)
		{
			int x = absX + rnd.nextInt(16);
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16);
			(new WorldGenClay(4)).generate(this.world, rnd, x, y, z);
		}

		for (int i = 0; i < 20; i++)
		{
			int x = absX + rnd.nextInt(16);
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16);
			(new WorldGenMinable(Blocks.coal_ore, 16)).generate(this.world, rnd, x, y, z);
		}

		for (int i = 0; i < 20; i++)
		{
			int x = absX + rnd.nextInt(16);
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16);
			(new WorldGenMinable(Blocks.iron_ore, 8)).generate(this.world, rnd, x, y, z);
		}

		for (int i = 0; i < 2; i++)
		{
			int x = absX + rnd.nextInt(16);
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16);
			(new WorldGenMinable(Blocks.gold_ore, 8)).generate(this.world, rnd, x, y, z);
		}

		for (int i = 0; i < 8; i++)
		{
			int x = absX + rnd.nextInt(16);
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16);
			(new WorldGenMinable(Blocks.redstone_ore, 7)).generate(this.world, rnd, x, y, z);
		}

		int treesPerChunk = chunk.biome.theBiomeDecorator.treesPerChunk;

		if (rnd.nextInt(10) == 0)
		{
			treesPerChunk++;
		}

		for (int i = 0; i < treesPerChunk; i++)
		{
			int x = absX + rnd.nextInt(16) + 8;
			int z = absZ + rnd.nextInt(16) + 8;
			int y = this.world.getHeightValue(x, z);

			// func_150567_a == getRandomWorldGenForTrees
			WorldGenerator gen = chunk.biome.func_150567_a(rnd);

			gen.setScale(SCALE, SCALE, SCALE);
			gen.generate(this.world, rnd, x, y, z);
		}

		for (int i = 0; i < 2; i++)
		{
			int x = absX + rnd.nextInt(16) + 8;
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16) + 8;

			(new WorldGenFlowers(Blocks.yellow_flower)).generate(this.world, rnd, x, y, z);
		}

		if (rnd.nextInt(2) == 0)
		{
			int x = absX + rnd.nextInt(16) + 8;
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16) + 8;
			(new WorldGenFlowers(Blocks.red_flower)).generate(this.world, rnd, x, y, z);
		}

		if (rnd.nextInt(4) == 0)
		{
			int x = absX + rnd.nextInt(16) + 8;
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16) + 8;
			(new WorldGenFlowers(Blocks.brown_mushroom)).generate(this.world, rnd, x, y, z);
		}

		if (rnd.nextInt(8) == 0)
		{
			int x = absX + rnd.nextInt(16) + 8;
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16) + 8;
			(new WorldGenFlowers(Blocks.red_mushroom)).generate(this.world, rnd, x, y, z);
		}

		int l13;

		if (TALLGRASS)
		{
			int grassPerChunk = chunk.biome.theBiomeDecorator.grassPerChunk;

			for (int i = 0; i < grassPerChunk; i++)
			{
				byte metadata = 1; // grass height maybe?

				if (chunk.biome == BiomeGenBase.desert && rnd.nextInt(3) != 0)
				{
					metadata = 2;
				}

				int x = absX + rnd.nextInt(16) + 8;
				int y = rnd.nextInt(WORLD_HEIGHT);
				int z = absZ + rnd.nextInt(16) + 8;

				(new WorldGenTallGrass(Blocks.tallgrass, metadata)).generate(this.world, rnd, x, y, z);
			}
		}

		for (int i = 0; i < 20; i++)
		{
			int x = absX + rnd.nextInt(16) + 8;
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16) + 8;
			(new WorldGenReed()).generate(this.world, rnd, x, y, z);
		}

		if (rnd.nextInt(32) == 0)
		{
			int x = absX + rnd.nextInt(16) + 8;
			int y = rnd.nextInt(WORLD_HEIGHT);
			int z = absZ + rnd.nextInt(16) + 8;
			(new WorldGenPumpkin()).generate(this.world, rnd, x, y, z);
		}

		if (chunk.biome == BiomeGenBase.desert)
		{
			int count = rnd.nextInt(5);

			for (int i = 0; i < count; i++)
			{
				int x = absX + rnd.nextInt(16) + 8;
				int z = absZ + rnd.nextInt(16) + 8;
				int y = this.world.getHeightValue(x, z);

				(new WorldGenCactus()).generate(this.world, rnd, x, y, z);
			}
		}
		else if (chunk.biome == BiomeGenBase.hell)
		{
			if (rnd.nextBoolean())
			{
				int x = absX + rnd.nextInt(16) + 8;
				int z = absZ + rnd.nextInt(16) + 8;
				int y = this.world.getHeightValue(x, z);

				(new WorldGenFire()).generate(this.world, rnd, x, y, z);
			}
		}
		else if (chunk.biome == BiomeGenBase.mushroomIsland)
		{
			for (int i = 0; i < 2; i++)
			{
				int x = absX + rnd.nextInt(16) + 8;
				int z = absZ + rnd.nextInt(16) + 8;
				int y = this.world.getHeightValue(x, z);

				(new WorldGenBigMushroom()).generate(this.world, rnd, x, y, z);
			}

			for (int i = 0; i < 1; i++)
			{
				if (rnd.nextInt(4) == 0)
				{
					int x = absX + rnd.nextInt(16) + 8;
					int z = absZ + rnd.nextInt(16) + 8;
					int y = this.world.getHeightValue(x, z);

					(new WorldGenFlowers(Blocks.yellow_flower)).generate(this.world, rnd, x, y, z);
				}

				if (rnd.nextInt(8) == 0)
				{
					int x = absX + rnd.nextInt(16) + 8;
					int z = absZ + rnd.nextInt(16) + 8;
					int y = rnd.nextInt(WORLD_HEIGHT);

					(new WorldGenFlowers(Blocks.red_flower)).generate(this.world, rnd, x, y, z);
				}
			}
		}
		else if (chunk.biome == BiomeGenBase.taiga || chunk.biome == BiomeGenBase.icePlains)
		{
			// this.setNoise(chunkX, chunkZ);

			for (int zo = 0; zo < 16; zo++)
			{
				for (int xo = 0; xo < 16; xo++)
				{
					int midY = chunk.getSurfaceLevel(xo, zo);

					int x = xo + absX;
					int z = zo + absZ;
					int y = midY + 1;

					double distance = chunk.getMainDistance(x, midY, z);

					if (distance <= chunk.radius && this.world.isBlockFreezable(x, y, z))
					{
						this.world.setBlock(x, y, z, Blocks.snow);
					}
				}
			}
		}

		// if (!EXPLOITBUG)
		{
			SpawnerAnimals.performWorldGenSpawning(this.world, chunk.biome, absX + 8, absZ + 8, 16, 16, rnd);
		}

		BlockSand.fallInstantly = false;
	}

	/**
	 * Two modes of operation: if passed true, save all Chunks in one go. If passed false, save up to two chunks. Return
	 * true if all chunks have been saved.
	 */
	public boolean saveChunks(boolean flag, IProgressUpdate iprogressupdate)
	{
		return true;
	}

	/**
	 * Unloads chunks that are marked to be unloaded. This is not guaranteed to unload every such chunk.
	 */
	public boolean unloadQueuedChunks()
	{
		return false;
	}

	/**
	 * Returns if the IChunkProvider supports saving.
	 */
	public boolean canSave()
	{
		return true;
	}

	/**
	 * Converts the instance data to a readable string.
	 */
	public String makeString()
	{
		return "RandomLevelSource";
	}

	/**
	 * Returns a list of creatures of the specified type that can spawn at the given location.
	 */
	public List getPossibleCreatures(EnumCreatureType enumcreaturetype, int i, int j, int k)
	{
		BiomeGenBase biomegenbase = this.world.getBiomeGenForCoords(i, k);
		return biomegenbase == null ? null : biomegenbase.getSpawnableList(enumcreaturetype);
	}

	/**
	 * Returns the location of the closest structure of the specified type. If not found returns null.
	 */
	public ChunkPosition findClosestStructure(World world1, String s, int i, int j, int k)
	{
		return null;
	}

	public int getLoadedChunkCount()
	{
		return 0;
	}

	public void recreateStructures(int var1, int var2)
	{}

	public void func_104112_b()
	{}

	static
	{
		BiomeGenBase.hell.topBlock = BiomeGenBase.hell.fillerBlock = Blocks.netherrack;
		BiomeGenBase.sky.topBlock = BiomeGenBase.sky.fillerBlock = Blocks.end_stone;

		Block domeBlock = Block.getBlockById(20); // 20 == glass(?)
		Block bridgeSupportBlock = Blocks.planks;
		Block bridgeRailBlock = Blocks.fence;

		boolean noiseEnabled = false;
		boolean enabled = true;
		boolean tallGrassEnabled = true;
		boolean waterWorldEnabled = false;
		// boolean exploitBugEnabled = false;
		int grid = 9;
		int special = 7;
		int lavaLevel = 24;
		int bridgeSize = 2;

		try
		{
			cfgFile.getParentFile().mkdirs();

			if (cfgFile.exists() || cfgFile.createNewFile())
			{
				Properties props = new Properties();

				if (cfgFile.canRead())
				{
					FileInputStream fs = null;
					try
					{
						fs = new FileInputStream(cfgFile);
						props.load(fs);

						domeBlock = Utils.ParseBlock(props.getProperty("dome", Utils.GetNameOrIdForBlock(domeBlock)));
						noiseEnabled = Boolean.parseBoolean(props.getProperty("noise", Boolean.toString(noiseEnabled)));
						enabled = Boolean.parseBoolean(props.getProperty("enabled", Boolean.toString(enabled)));
						tallGrassEnabled = Boolean.parseBoolean(props.getProperty(
							"tall_grass",
							Boolean.toString(tallGrassEnabled)));
						waterWorldEnabled = Boolean.parseBoolean(props.getProperty(
							"water_world",
							Boolean.toString(waterWorldEnabled)));
						// exploitBugEnabled = Boolean.parseBoolean(props.getProperty(
						// "exploit_bug",
						// Boolean.toString(exploitBugEnabled)));
						grid = Integer.parseInt(props.getProperty("grid", "9"));
						special = Integer.parseInt(props.getProperty("special", "7"));
						lavaLevel = Integer.parseInt(props.getProperty("lavaLevel", "24"));
						bridgeSize = Integer.parseInt(props.getProperty("bridge_size", "2"));
						bridgeSupportBlock = Utils.ParseBlock(props.getProperty(
							"bridge_support",
							Utils.GetNameOrIdForBlock(bridgeSupportBlock)));
						bridgeRailBlock = Utils.ParseBlock(props.getProperty(
							"bridge_rail",
							Utils.GetNameOrIdForBlock(bridgeRailBlock)));

						for (Object _biome: BiosphereWeather.biomeList)
						{
							BiomeEntry biome = (BiomeEntry)_biome;

							biome.itemWeight = Integer.parseInt(props.getProperty(
								"weight_" + biome.biome.biomeName,
								Integer.toString(biome.itemWeight)));
						}

						// BiomeEntry iterator1;
						// for (Iterator biomeentry1 =
						// BiosphereWeather.biomeList.iterator();
						// biomeentry1.hasNext(); iterator1.itemWeight =
						// Integer.parseInt(props.getProperty(
						// "weight_" + iterator1.biome.biomeName,
						// Integer.toString(iterator1.itemWeight))))
						// {
						// iterator1 = (BiomeEntry)biomeentry1.next();
						// }
					}
					finally
					{
						if (fs != null)
						{
							fs.close();
						}
					}
				}

				// This works fine, but it's annoying during debugging.
				// TODO: RE-ENABLE THIS.

				// if (cfgFile.canWrite())
				// {
				// FileOutputStream fs = null;
				// try
				// {
				// fs = new FileOutputStream(cfgFile);
				//
				// props.setProperty("dome",
				// WoopMod.GetNameOrIdForBlock(domeBlock));
				// props.setProperty("noise", Boolean.toString(noiseEnabled));
				// props.setProperty("enabled", Boolean.toString(flag1));
				// props.setProperty("tall_grass",
				// Boolean.toString(tallGrassEnabled));
				// props.setProperty("water_world",
				// Boolean.toString(waterWorldEnabled));
				// props.setProperty("exploit_bug",
				// Boolean.toString(exploitBugEnabled));
				// props.setProperty("grid", Integer.toString(grid));
				// props.setProperty("special", Integer.toString(special));
				// props.setProperty("lavaLevel", Integer.toString(lavaLevel));
				// props.setProperty("bridge_size",
				// Integer.toString(bridgeSize));
				// props.setProperty("bridge_support",
				// WoopMod.GetNameOrIdForBlock(bridgeSupportBlock));
				// props.setProperty("bridge_rail",
				// WoopMod.GetNameOrIdForBlock(bridgeRailBlock));
				//
				//
				// for(Object _biomeEntry: BiosphereWeather.biomeList)
				// {
				// BiomeEntry biomeEntry = (BiomeEntry)_biomeEntry;
				// props.setProperty("weight_" + biomeEntry.biome.biomeName,
				// Integer.toString(biomeEntry.itemWeight));
				// }
				//
				// props.store(fs, "Biosphere Config");
				// }
				// finally
				// {
				// if (fs != null)
				// {
				// fs.close();
				// }
				// }
				// }
			}
		}
		catch (Throwable ignore)
		{ /* do nothing */
		}

		DOME_TYPE = domeBlock;
		NOISE = noiseEnabled;
		ENABLED = enabled;
		TALLGRASS = tallGrassEnabled;
		WATERWORLD = waterWorldEnabled;
		// EXPLOITBUG = exploitBugEnabled;
		GRID_SIZE = grid;
		SPECIAL_RADIUS = special;
		LAVA_LEVEL = lavaLevel;
		BRIDGE_SIZE = bridgeSize;
		BRIDGE_SUPPORT = bridgeSupportBlock;
		BRIDGE_RAIL = bridgeRailBlock;

		if (WATERWORLD)
		{
			Blocks.water.setLightOpacity(0);
			Blocks.flowing_water.setLightOpacity(0);
		}

		SCALE_GRID = (int)((float)GRID_SIZE * SCALE);
		SCALED_SPECIAL = (int)((float)SPECIAL_RADIUS * SCALE);
	}

	public void saveExtraData()
	{
		/* do nothing */
	}

	public ChunkPosition func_147416_a(World p_147416_1_, String p_147416_2_, int p_147416_3_, int p_147416_4_,
			int p_147416_5_)
	{

		return null;
	}
}
