package newBiospheresMod;

import net.minecraft.util.WeightedRandom;
import net.minecraft.world.biome.BiomeGenBase;

public class BiomeEntry extends WeightedRandom.Item
{
	public final BiomeGenBase biome;

	public BiomeEntry(BiomeGenBase biomegenbase, int i)
	{
		super(i);
		this.biome = biomegenbase;
	}
}