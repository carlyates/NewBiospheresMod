/*
 * This is free software. It comes without any warranty, to the extent permitted by applicable law. You can redistribute
 * it and/or modify it under the terms of the Do What The Fuck You Want To Public License, Version 2, as published by
 * Sam Hocevar. See http://www.wtfpl.net/ for more details.
 */

package newBiospheresMod.Configuration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.block.Block;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;
import net.minecraft.world.biome.BiomeGenBase;
import net.minecraftforge.common.config.ConfigCategory;
import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.common.config.Property;
import newBiospheresMod.BiomeEntry;
import newBiospheresMod.BiosphereWorldType;
import newBiospheresMod.BlockData;
import newBiospheresMod.BlockEntry;
import newBiospheresMod.Helpers.Blx;
import newBiospheresMod.Helpers.Creator;
import newBiospheresMod.Helpers.IKeyProvider;
import newBiospheresMod.Helpers.LruCacheList;
import newBiospheresMod.Helpers.ModConsts;
import newBiospheresMod.Helpers.Predicate;
import newBiospheresMod.Helpers.Utils;

public class ModConfig
{
	// #region Caching

	private static LruCacheList<ModConfig> modConfigs = new LruCacheList<ModConfig>(10, new IKeyProvider<ModConfig>()
	{
		@Override
		public Object provideKey(ModConfig item)
		{
			if (item == null) { return null; }
			return item.World;
		}
	});

	public static ModConfig get(final World world)
	{
		return modConfigs.FindOrAdd(world, new Creator<ModConfig>()
		{
			@Override
			public ModConfig create()
			{
				return new ModConfig(world);
			}
		});
	}

	// #endregion

	// #region Static Fields and Methods

	private static Configuration cfgFile = null;

	public static Configuration getConfigFile()
	{
		return cfgFile;
	}

	public static void setConfigFile(Configuration value)
	{
		cfgFile = value;

		if (cfgFile != null)
		{
			cfgFile.setCategoryComment
			(
				Categories.General,
				ModConsts.ModId + " " + ModConsts.ModVersion
				+ ": Note, these settings only affect new Worlds; previously created Worlds will persist with their existing settings."
			);
		}
	}

	public static void updateFile()
	{
		setConfigFile(getConfigFile());
		ModConfig.get(null).update();
	}

	// #endregion

	// #region Read/Write Delegates

	private abstract class WorldProperty<T>
	{
		public final Property Property;
		public final T DefaultValue;
		public final T CurrentValue;

		protected abstract T Convert(String input, CustomWorldData data) throws Throwable;

		protected abstract String Convert(T input, CustomWorldData data);

		protected T getFallbackValue(CustomWorldData data)
		{
			if (data == null) { return this.CurrentValue; }
			return data.getIsNew() ? this.CurrentValue : this.DefaultValue;
		}

		WorldProperty(Property property, T currentValue, T defaultValue)
		{
			this.Property = property;
			this.CurrentValue = currentValue;
			this.DefaultValue = defaultValue;
		}

		public T ReadWorldValue(CustomWorldData data)
		{
			if (data != null)
			{
				String keyName = GetNewWorldProperty(this.Property);

				if (data.ContainsKey(keyName))
				{
					try
					{
						return Convert(data.get(keyName), data);
					}
					catch (Throwable ignore)
					{
						// ignore
					}
				}
			}
			return getFallbackValue(data);
		}

		public void WriteWorldValue(CustomWorldData data)
		{
			if (data != null)
			{
				String keyName = GetNewWorldProperty(this.Property);
				data.put(keyName, Convert(this.CurrentValue, data));
			}
		}
	}

	private class BooleanWorldProperty extends WorldProperty<Boolean>
	{
		BooleanWorldProperty(Property property, Boolean currentValue, Boolean defaultValue)
		{
			super(property, currentValue, defaultValue);
		}

		@Override
		protected Boolean Convert(String input, CustomWorldData data) throws Throwable
		{
			return Boolean.parseBoolean(input);
		}

		@Override
		protected String Convert(Boolean input, CustomWorldData data)
		{
			return Boolean.toString(input);
		}
	}

	private class IntegerWorldProperty extends WorldProperty<Integer>
	{
		IntegerWorldProperty(Property property, Integer currentValue, Integer defaultValue)
		{
			super(property, currentValue, defaultValue);
		}

		@Override
		protected Integer Convert(String input, CustomWorldData data) throws Throwable
		{
			return Integer.parseInt(input);
		}

		@Override
		protected String Convert(Integer input, CustomWorldData data)
		{
			return Integer.toString(input);
		}
	}

	private class FloatWorldProperty extends WorldProperty<Float>
	{
		FloatWorldProperty(Property property, Float currentValue, Float defaultValue)
		{
			super(property, currentValue, defaultValue);
		}

		@Override
		protected Float Convert(String input, CustomWorldData data) throws Throwable
		{
			return Float.parseFloat(input);
		}

		@Override
		protected String Convert(Float input, CustomWorldData data)
		{
			return Float.toString(input);
		}
	}

	private class DoubleWorldProperty extends WorldProperty<Double>
	{
		DoubleWorldProperty(Property property, Double currentValue, Double defaultValue)
		{
			super(property, currentValue, defaultValue);
		}

		@Override
		protected Double Convert(String input, CustomWorldData data) throws Throwable
		{
			return Double.parseDouble(input);
		}

		@Override
		protected String Convert(Double input, CustomWorldData data)
		{
			return Double.toString(input);
		}
	}

	private class BlockWorldProperty extends WorldProperty<BlockData>
	{
		BlockWorldProperty(Property property, BlockData currentValue, BlockData defaultValue)
		{
			super(property, currentValue, defaultValue);
		}

		@Override
		protected BlockData Convert(String input, CustomWorldData data) throws Throwable
		{
			return BlockData.Parse(input, this.getFallbackValue(data));
		}

		@Override
		protected String Convert(BlockData input, CustomWorldData data)
		{
			return input == null ? BlockData.Empty.toString() : input.toString();
		}
	}

	// #endregion

	// #region Fields & Properties

	public final World World;
	public final List<BiomeEntry> AllBiomes;

	private final static int BLOCK_COUNT = 20;
	public final List<BlockEntry> OreOrbBlocks = new ArrayList<BlockEntry>();
	public final List<BlockEntry> StairwayBlocks = new ArrayList<BlockEntry>();

	public final static int DOMETYPE_COUNT = 4;
	public final static int DOMETYPE_BLOCK_COUNT = 4;
	public final static List<BlockEntry>[] DomeBlocks = new ArrayList[DOMETYPE_COUNT];

	// #region boolean NoiseEnabled

	private static final boolean defaultNoiseEnabled = true;
	private boolean noiseEnabled = defaultNoiseEnabled;

	public boolean isNoiseEnabled()
	{
		return noiseEnabled;
	}

	public void setNoiseEnabled(boolean noiseEnabled)
	{
		this.noiseEnabled = noiseEnabled;
	}

	private static Property getNoiseEnabledProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.General, "Noise Enabled", defaultNoiseEnabled,
			"Controls whether a noise generator is used to generate terrain heights or if the World should be flat.");
	}

	private BooleanWorldProperty getNoiseEnabledWorldProperty()
	{
		return new BooleanWorldProperty(getNoiseEnabledProperty(), isNoiseEnabled(), defaultNoiseEnabled);
	}

	// #endregion

	// #region float Scale

	private static final float minScale = .2f;
	private static final float maxScale = 10f;
	private static final float defaultScale = 1.0f;
	private float scale = defaultScale;

	public float getScale()
	{
		return scale;
	}

	public void setScale(float value)
	{
		if (value < minScale) value = minScale;
		else if (value > maxScale) value = maxScale;

		this.scale = value;
		this.scaledGridSize = 0;
		this.scaledOrbRadius = 0;
	}

	private static Property getScaleProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.General, "Scale", defaultScale,
			"The scale of the world to generate.", minScale, maxScale);
	}

	private FloatWorldProperty getScaleWorldProperty()
	{
		return new FloatWorldProperty(getScaleProperty(), getScale(), defaultScale);
	}

	// #endregion

	// #region Block OrbBlock

	private static final BlockData defaultOrbBlock = new BlockData(Blx.glass);
	private BlockData orbBlock = defaultOrbBlock;

	public BlockData getOrbBlock()
	{
		return orbBlock;
	}

	public void setOrbBlock(BlockData value)
	{
		if (value == null) { value = defaultOrbBlock; }
		this.orbBlock = value;
	}

	private static Property getOrbBlockProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.OreOrbs, "Ore Orb Shell Block", defaultOrbBlock.toString(),
			"The Block to use for the shell of the generated Ore Orbs.");
	}

	private BlockWorldProperty getOrbBlockWorldProperty()
	{
		return new BlockWorldProperty(getOrbBlockProperty(), getOrbBlock(), defaultOrbBlock);
	}

	// #endregion

	// #region Block BridgeSupportBlock

	private static final BlockData defaultBridgeSupportBlock = new BlockData(Blx.planks);
	private BlockData bridgeSupportBlock = defaultBridgeSupportBlock;

	public BlockData getBridgeSupportBlock()
	{
		return bridgeSupportBlock;
	}

	public void setBridgeSupportBlock(BlockData value)
	{
		if (value == null) { value = defaultBridgeSupportBlock; }
		this.bridgeSupportBlock = value;
	}

	private static Property getBridgeSupportBlockProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get
		(
			Categories.General, "Bridge Support Block",
			defaultBridgeSupportBlock.toString(),
			"The Block to use for bridges between bio-domes and stairways to ore-orbs."
		);
	}

	private BlockWorldProperty getBridgeSupportBlockWorldProperty()
	{
		return new BlockWorldProperty
		(
			getBridgeSupportBlockProperty(),
			getBridgeSupportBlock(),
			defaultBridgeSupportBlock
		);
	}

	// #endregion

	// #region Block BridgeRailBlock

	private static final BlockData defaultBridgeRailBlock = new BlockData(Blx.fence);
	private BlockData bridgeRailBlock = defaultBridgeRailBlock;

	public BlockData getBridgeRailBlock()
	{
		return bridgeRailBlock;
	}

	public void setBridgeRailBlock(BlockData value)
	{
		if (value == null) { value = defaultBridgeRailBlock; }
		this.bridgeRailBlock = value;
	}

	private static Property getBridgeRailBlockProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get
		(
			Categories.General,
			"Bridge Rail Block",
			defaultBridgeRailBlock.toString(),
			"The Block to use for the rails on the bridges between bio-domes."
		);
	}

	private BlockWorldProperty getBridgeRailBlockWorldProperty()
	{
		return new BlockWorldProperty
		(
			getBridgeRailBlockProperty(),
			getBridgeRailBlock(),
			defaultBridgeRailBlock
		);
	}

	// #endregion

	// #region Block OutsideFillerBlock

	private static final BlockData defaultOutsideFillerBlock = new BlockData(Blx.air);
	private BlockData outsideFillerBlock = defaultOutsideFillerBlock;

	public BlockData getOutsideFillerBlock()
	{
		return outsideFillerBlock;
	}

	public void setOutsideFillerBlock(BlockData value)
	{
		if (value == null) { value = defaultOutsideFillerBlock; }

		//if (value.Block == Blx.lava) { value = value.setBlock(Blx.flowing_lava); }
		//else if (value.Block == Blx.water) { value = value.setBlock(Blx.flowing_water); }

		outsideFillerBlock = value;
	}

	private static Property getOutsideFillerBlockProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get
		(
			Categories.General,
			"Outside Filler Block",
			defaultOutsideFillerBlock.toString(),
			"The block used to fill the area outside of the domes [air, water, and lava are good choices]."
		);
	}

	private BlockWorldProperty getOutsideFillerBlockWorldProperty()
	{
		return new BlockWorldProperty
		(
			getOutsideFillerBlockProperty(),
			getOutsideFillerBlock(),
			defaultOutsideFillerBlock
		);
	}

	// #endregion

	// #region boolean TallGrassEnabled

	private static final boolean defaultTallGrassEnabled = true;
	private boolean tallGrassEnabled = defaultTallGrassEnabled;

	public boolean isTallGrassEnabled()
	{
		return tallGrassEnabled;
	}

	public void setTallGrassEnabled(boolean tallGrass)
	{
		this.tallGrassEnabled = tallGrass;
	}

	private static Property getTallGrassEnabledProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.Biospheres, "Tall Grass Enabled", defaultTallGrassEnabled,
			"Controls whether tall grass is generated or not.");
	}

	private BooleanWorldProperty getTallGrassEnabledWorldProperty()
	{
		return new BooleanWorldProperty(getTallGrassEnabledProperty(), isTallGrassEnabled(), defaultTallGrassEnabled);
	}

	// #endregion

	// #region int GridSize

	private static final int minGridSize = 5;
	private static final int maxGridSize = 25;
	private static final int defaultGridSize = 9;
	private int gridSize = defaultGridSize;

	public int getGridSize()
	{
		return gridSize;
	}

	public void setGridSize(int value)
	{
		if (value < minGridSize) value = minGridSize;
		else if (value > maxGridSize) value = maxGridSize;

		this.gridSize = value;
		this.scaledGridSize = 0;
	}

	private static Property getGridSizeProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.General, "Grid Size", defaultGridSize,
			"The size of the grid (for one sphere and orb) in chunks (pre-scaled)[a 'chunk' is 16 blocks square].",
			minGridSize, maxGridSize);
	}

	private IntegerWorldProperty getGridSizeWorldProperty()
	{
		return new IntegerWorldProperty(getGridSizeProperty(), getGridSize(), defaultGridSize);
	}

	// #endregion

	// #region int BridgeWidth

	private static final int minBridgeWidth = 1;
	private static final int maxBridgeWidth = 15;
	private static final int defaultBridgeWidth = 2;
	private int bridgeWidth = defaultBridgeWidth;

	public int getBridgeWidth()
	{
		return bridgeWidth;
	}

	public void setBridgeWidth(int value)
	{
		if (value < minBridgeWidth) value = minBridgeWidth;
		else if (value > maxBridgeWidth) value = maxBridgeWidth;

		this.bridgeWidth = value;
	}

	private static Property getBridgeWidthProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.General, "Bridge Width", defaultBridgeWidth,
			"Bridge Width: the width of the bridge [from the center to the edge].", minBridgeWidth, maxBridgeWidth);
	}

	private IntegerWorldProperty getBridgeWidthWorldProperty()
	{
		return new IntegerWorldProperty(getBridgeWidthProperty(), getBridgeWidth(), defaultBridgeWidth);
	}

	// #endregion

	// #region Min & Max Sphere Radius

	private static final double sphereRadiusMinimumValue = 15d;
	private static final double sphereRadiusMaximumValue = 80d;

	// #region double MinSphereRadius

	private static final double defaultMinSphereRadius = 20;
	private double minSphereRadius = defaultMinSphereRadius;

	public double getMinSphereRadius()
	{
		return minSphereRadius;
	}

	public void setMinSphereRadius(double value)
	{
		if (value < sphereRadiusMinimumValue) value = sphereRadiusMinimumValue;
		else if (value > sphereRadiusMaximumValue) value = sphereRadiusMaximumValue;

		this.minSphereRadius = value;
	}

	private static Property getMinSphereRadiusProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.Biospheres, "Sphere Radius (Minimum)", defaultMinSphereRadius,
			"The minimum (pre-scaled) sphere radius to generate.", sphereRadiusMinimumValue, sphereRadiusMaximumValue);
	}

	private DoubleWorldProperty getMinSphereRadiusWorldProperty()
	{
		return new DoubleWorldProperty(getMinSphereRadiusProperty(), getMinSphereRadius(), defaultMinSphereRadius);
	}

	// #endregion

	// #region double MaxSphereRadius

	private static final double defaultMaxSphereRadius = 50;
	private double maxSphereRadius = defaultMaxSphereRadius;

	public double getMaxSphereRadius()
	{
		return maxSphereRadius;
	}

	public void setMaxSphereRadius(double value)
	{
		if (value < sphereRadiusMinimumValue) value = sphereRadiusMinimumValue;
		else if (value > sphereRadiusMaximumValue) value = sphereRadiusMaximumValue;

		this.maxSphereRadius = value;
	}

	private static Property getMaxSphereRadiusProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.Biospheres, "Sphere Radius (Maximum)", defaultMaxSphereRadius,
			"The maximum (pre-scaled) sphere radius to generate.", sphereRadiusMinimumValue, sphereRadiusMaximumValue);
	}

	private DoubleWorldProperty getMaxSphereRadiusWorldProperty()
	{
		return new DoubleWorldProperty(getMaxSphereRadiusProperty(), getMaxSphereRadius(), defaultMaxSphereRadius);
	}

	// #endregion

	// #endregion

	// #region double OrbRadius

	private static final double minOrbRadius = 1d;
	private static final double maxOrbRadius = 25d;
	private static final double defaultOrbRadius = 7;
	private double orbRadius = defaultOrbRadius;

	public double getOrbRadius()
	{
		return orbRadius;
	}

	public void setOrbRadius(double value)
	{
		if (value < minOrbRadius) value = minOrbRadius;
		else if (value > maxOrbRadius) value = maxOrbRadius;

		this.orbRadius = value;
		this.scaledOrbRadius = 0;
	}

	private static Property getOrbRadiusProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.OreOrbs, "Ore Orb Radius", defaultOrbRadius,
			"The radius (pre-scaled) of the ore orbs to generate.", minOrbRadius, maxOrbRadius);
	}

	private DoubleWorldProperty getOrbRadiusWorldProperty()
	{
		return new DoubleWorldProperty(getOrbRadiusProperty(), getOrbRadius(), defaultOrbRadius);
	}

	// #endregion

	// #region Min & Max Lake Ratio

	private static final double lakeRatioMinimumValue = 0.1d;
	private static final double lakeRatioMaximumValue = 0.75d;

	// #region double MinLakeRatio

	private static final double defaultMinLakeRatio = 0.3d;
	private double minLakeRatio = defaultMinLakeRatio;

	public double getMinLakeRatio()
	{
		return minLakeRatio;
	}

	public void setMinLakeRatio(double value)
	{
		if (value < lakeRatioMinimumValue) value = lakeRatioMinimumValue;
		else if (value > lakeRatioMaximumValue) value = lakeRatioMaximumValue;

		this.minLakeRatio = value;
	}

	private static Property getMinLakeRatioProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.Biospheres, "Lake Ratio (Minimum)", defaultMinLakeRatio,
			"The minimum ratio of lake size to sphere size.", lakeRatioMinimumValue, lakeRatioMaximumValue);
	}

	private DoubleWorldProperty getMinLakeRatioWorldProperty()
	{
		return new DoubleWorldProperty(getMinLakeRatioProperty(), getMinLakeRatio(), defaultMinLakeRatio);
	}

	// #endregion

	// #region double MaxLakeRatio

	private static final double defaultMaxLakeRatio = 0.6d;
	private double maxLakeRatio = defaultMaxLakeRatio;

	public double getMaxLakeRatio()
	{
		return maxLakeRatio;
	}

	public void setMaxLakeRatio(double value)
	{
		if (value < lakeRatioMinimumValue) value = lakeRatioMinimumValue;
		else if (value > lakeRatioMaximumValue) value = lakeRatioMaximumValue;

		this.maxLakeRatio = value;
	}

	private static Property getMaxLakeRatioProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.Biospheres, "Lake Ratio (Maximum)", defaultMaxLakeRatio,
			"The maximum ratio of lake size to sphere size.", lakeRatioMinimumValue, lakeRatioMaximumValue);
	}

	private DoubleWorldProperty getMaxLakeRatioWorldProperty()
	{
		return new DoubleWorldProperty(getMaxLakeRatioProperty(), getMaxLakeRatio(), defaultMaxLakeRatio);
	}

	// #endregion

	// #endregion

	// #region int SeaLevel

	private static final int seaLevelMinimumValue = 15;
	private static final int seaLevelMaximumValue = 111;

	private static final int defaultSeaLevel = 63;
	private int seaLevel = defaultSeaLevel;

	public int getSeaLevel()
	{
		return seaLevel;
	}

	public void setSeaLevel(int value)
	{
		if (value < seaLevelMinimumValue) value = seaLevelMinimumValue;
		else if (value > seaLevelMaximumValue) value = seaLevelMaximumValue;

		//System.out.println("New Sea Level: " + value + ", Old: " + seaLevel);

		this.seaLevel = value;
	}

	private static Property getSeaLevelProperty()
	{
		if (cfgFile == null) { return null; }

		return cfgFile.get(Categories.General, "Sea Level", defaultSeaLevel,
			"Sea Level (the default vertical center of the Biospheres).", seaLevelMinimumValue, seaLevelMaximumValue);
	}

	private IntegerWorldProperty getSeaLevelWorldProperty()
	{
		return new IntegerWorldProperty(getSeaLevelProperty(), getSeaLevel(), defaultSeaLevel);
	}

	// #endregion

	// #region int ScaledGridSize

	private int scaledGridSize = 0;

	public int getScaledGridSize()
	{
		if (scaledGridSize == 0)
		{
			scaledGridSize = (int)(gridSize * scale);
		}

		return scaledGridSize;
	}

	// #endregion

	// #region int ScaledOrbRadius

	private int scaledOrbRadius = 0;

	public int getScaledOrbRadius()
	{
		if (scaledOrbRadius == 0)
		{
			scaledOrbRadius = (int)((float)orbRadius * scale);
		}

		return scaledOrbRadius;
	}

	// #endregion

	public boolean doesNeedProtectionGlass()
	{
		BlockData block = getOutsideFillerBlock();
		if (block != null)
		{
			return block.Block != Blx.air;
		}

		return false;
	}

	// #endregion

	// #region Migrations

	private String GetCategoryName(Property input)
	{
		return GetCategoryName(input, cfgFile);
	}

	private static String GetCategoryName(Property input, Configuration cfgFile)
	{
		String fallback = null;
		String propName = input.getName();

		for (String catName: cfgFile.getCategoryNames())
		{
			ConfigCategory cat = cfgFile.getCategory(catName);
			if (cat != null)
			{
				if (cat.containsKey(propName))
				{
					if (fallback == null) fallback = catName;
					if (cat.get(propName) == input) { return catName; }
				}
			}
		}

		return fallback;
	}

	private String GetOldWorldProperty(Property input)
	{
		return GetOldWorldProperty(input.getName());
	}

	private static String GetOldWorldProperty(String propName)
	{
		return ModConsts.ModId + "." + propName;
	}

	private String GetNewWorldProperty(Property input)
	{
		return GetNewWorldProperty(input, cfgFile);
	}

	private static String GetNewWorldProperty(Property input, Configuration cfgFile)
	{
		return GetNewWorldProperty(GetCategoryName(input, cfgFile), input.getName());
	}

	private static String GetNewWorldProperty(String category, String propName)
	{
		String result = category + "." + propName;
		result = result.toLowerCase().replace(" ", "");
		return result;
	}

	private static abstract class MigrationAction
	{
		public abstract void PerformConfigMigration(Configuration cfgFile);
		public abstract void PerformWorldMigration(CustomWorldData data, Configuration cfgFile);
	}

	private static class PropertyRenamedMigration extends MigrationAction
	{
		public final String Category;
		public final String OldPropertyName;
		public final String NewPropertyName;

		public PropertyRenamedMigration(String category, String oldPropertyName, String newPropertyName)
		{
			this.Category = category;
			this.OldPropertyName = oldPropertyName;
			this.NewPropertyName = newPropertyName;
		}

		@Override
		public void PerformConfigMigration(Configuration cfgFile)
		{
			if (cfgFile.hasCategory(this.Category))
			{
				if (cfgFile.hasKey(this.Category, this.OldPropertyName))
				{
					cfgFile.renameProperty(this.Category, this.OldPropertyName, this.NewPropertyName);
				}
			}
		}

		@Override
		public void PerformWorldMigration(CustomWorldData data, Configuration cfgFile)
		{
			MigrateWorldProperty(data, this.Category, this.Category, this.OldPropertyName, this.NewPropertyName);
		}
	}

	private static class PropertyMovedMigration extends MigrationAction
	{
		public final String OldCategory;
		public final String NewCategory;
		public final String PropertyName;

		public PropertyMovedMigration(String oldCategory, String newCategory, String propertyName)
		{
			this.OldCategory = oldCategory;
			this.NewCategory = newCategory;
			this.PropertyName = propertyName;
		}

		@Override
		public void PerformConfigMigration(Configuration cfgFile)
		{
			cfgFile.moveProperty(OldCategory, PropertyName, NewCategory);
		}

		@Override
		public void PerformWorldMigration(CustomWorldData data, Configuration cfgFile)
		{
			MigrateWorldProperty(data, this.OldCategory, this.NewCategory, this.PropertyName, this.PropertyName);
		}
	}

	private static List<MigrationAction> migrations = null;

	private synchronized void InitMigrations()
	{
		if (migrations == null)
		{
			migrations = new ArrayList<MigrationAction>();
			migrations.add(new PropertyMovedMigration(Categories.General, Categories.Biospheres, "Dome Block"));
			migrations.add(new PropertyMovedMigration(Categories.General, Categories.Biospheres, "Sphere Radius (Minimum)"));
			migrations.add(new PropertyMovedMigration(Categories.General, Categories.Biospheres, "Sphere Radius (Maximum)"));
			migrations.add(new PropertyMovedMigration(Categories.General, Categories.Biospheres, "Lake Ratio (Minimum)"));
			migrations.add(new PropertyMovedMigration(Categories.General, Categories.Biospheres, "Lake Ratio (Maximum)"));
			migrations.add(new PropertyMovedMigration(Categories.General, Categories.Biospheres, "Tall Grass Enabled"));
			migrations.add(new PropertyMovedMigration(Categories.General, Categories.OreOrbs, "Ore Orb Radius"));
			migrations.add(new PropertyRenamedMigration(Categories.Biospheres, "Dome Block", "Dome Type #0 - Block #0"));
		}
	}

	private void PerformFileMigrations()
	{
		InitMigrations();

		if (cfgFile != null)
		{
			for (MigrationAction mige: migrations)
			{
				mige.PerformConfigMigration(cfgFile);
			}

			if (cfgFile.hasChanged())
			{
				cfgFile.save();
			}
		}
	}

	private static void MigrateWorldProperty(GameRules rules, CustomWorldData data, String category, String propertyName)
	{
		if (rules != null && data != null)
		{
			String oldName = GetOldWorldProperty(propertyName);
			if (rules.hasRule(oldName))
			{
				String value = rules.getGameRuleStringValue(oldName);
				data.put(GetNewWorldProperty(category, propertyName), value);
			}
		}
	}

	private static void MigrateWorldProperty(CustomWorldData data, String oldCategory, String newCategory, String oldPropertyName, String newPropertyName)
	{
		if (data != null)
		{
			String oldName = GetNewWorldProperty(oldCategory, oldPropertyName);
			String newName = GetNewWorldProperty(newCategory, newPropertyName);

			if (data.ContainsKey(oldName))
			{
				String value = data.get(oldName);
				data.RemoveKey(oldName);
				data.put(newName, value);
			}
		}
	}

	private void PerformWorldMigrations()
	{
		InitMigrations();

		if (World == null) { return; }

		CustomWorldData data = CustomWorldData.FromWorld(World);
		if (data == null) { return; }

		GameRules rules = Utils.GetGameRules(this.World);
		if (rules != null)
		{
			String ruleName = ModConsts.ModId + ".Is Biosphere World";
			if (rules.getGameRuleBooleanValue(ruleName))
			{
				data.put(BiosphereWorldType.IsBiosphereWorldKey, true);
			}
		}

		if (rules != null && cfgFile != null)
		{
			for (String catName: cfgFile.getCategoryNames())
			{
				ConfigCategory cat = cfgFile.getCategory(catName);
				for (String propName: cat.getValues().keySet())
				{
					MigrateWorldProperty(rules, data, catName, propName);
				}
			}
		}

		for (MigrationAction mige: migrations)
		{
			mige.PerformWorldMigration(data, cfgFile);
		}

		data.MakeNotNew();
		data.setDirty(true);
	}

	// #endregion

	// #region Property Helpers

	private static Predicate<BiomeEntry> SearchFor(final BiomeGenBase biome)
	{
		return new Predicate<BiomeEntry>()
		{
			@Override
			public boolean test(BiomeEntry entry)
			{
				return entry.biome == biome;
			}
		};
	}

	private static int GetDefaultBiomeWeight(BiomeGenBase biome)
	{
		if (biome == BiomeGenBase.forest) { return 50; }
		if (biome == BiomeGenBase.taiga) { return 40; }
		if (biome == BiomeGenBase.swampland) { return 40; }
		if (biome == BiomeGenBase.hell) { return 10; }
		if (biome == BiomeGenBase.mushroomIsland) { return 5; }
		if (biome == BiomeGenBase.sky) { return 2; }

		return 25;
	}

	private Property GetBiomeEntryProperty(BiomeEntry biomeEntry)
	{
		if (cfgFile == null) { return null; }
		if (biomeEntry == null) { return null; }
		BiomeGenBase biome = biomeEntry.biome;
		if (biome == null) { return null; }

		return cfgFile.get(Categories.BiomeWeights, biome.biomeName, GetDefaultBiomeWeight(biome),
			"The weighted chance that the \"" + biome.biomeName + "\" biome will be generated.", 0, 1000);
	}

	private static BlockEntry GetDefaultOreBlockEntry(int index)
	{
		if (index == 0) return new BlockEntry(Blx.lapis_ore, 0, 5);
		if (index == 1) return new BlockEntry(Blx.emerald_ore, 0, 5);
		if (index == 2) return new BlockEntry(Blx.diamond_ore, 0, 5);
		if (index == 3) return new BlockEntry(Blx.iron_ore, 0, 10);
		if (index == 4) return new BlockEntry(Blx.gold_ore, 0, 10);
		if (index == 5) return new BlockEntry(Blx.coal_ore, 0, 15);
		if (index == 6) return new BlockEntry(Blx.redstone_ore, 0, 15);
		if (index == 7) return new BlockEntry(Blx.quartz_ore, 0, 10);
		if (index == 8) return new BlockEntry(Blx.gravel, 0, 100);
		if (index == 9) return new BlockEntry(Blx.lava, 0, 15);
		if (index == 10) return new BlockEntry(Blx.stone, 0, 310);

		return new BlockEntry(Blx.air, 0, 0);
	}

	private static BlockEntry GetDefaultStairBlockEntry(int index)
	{
		if (index == 0) return new BlockEntry(Blx.planks, 0, 50);
		if (index == 1) return new BlockEntry(Blx.air, 0, 50);

		return new BlockEntry(Blx.air, 0, 0);
	}

	private static BlockEntry GetDefaultDomeBlockProperty(int domeTypeIndex, int blockIndex)
	{
		if (blockIndex == 0 && domeTypeIndex == 0) return new BlockEntry(Blx.glass, 0, 10);
		return new BlockEntry(Blx.air, 0, 0);
	}

	private Property GetDomeBlockProperty(int domeTypeIndex, int blockIndex)
	{
		if (cfgFile == null) { return null; }

		BlockEntry be = GetDefaultDomeBlockProperty(domeTypeIndex, blockIndex);

		String domeIdxStr = Integer.toString(domeTypeIndex);
		String blockIdxStr = Integer.toString(blockIndex);

		//while (domeIdxStr.length() < 2) { domeIdxStr = "0" + domeIdxStr; }
		//while (blockIdxStr.length() < 2) { blockIdxStr = "0" + blockIdxStr; }

		Property ret = cfgFile.get
		(
			Categories.Biospheres,
			"Dome Type #" + domeIdxStr + " - Block #" + blockIdxStr,
			be.toString(),
			"The chance that a given dome type will produce a given block.  Values have two parts, and are separated "
			+ "by a comma.  The left side of the comma specifies the block name or Id, and the right side of the comma "
			+ "specifies the weighted chance to produce that block as part of a given dome type."
		);

		return ret;
	}

	private Property GetRandomOreBlockEntryProperty(int index)
	{
		if (cfgFile == null) { return null; }

		BlockEntry be = GetDefaultOreBlockEntry(index);

		String idxStr = Integer.toString(index);
		while (idxStr.length() < 2)
		{
			idxStr = "0" + idxStr;
		}

		return cfgFile.get(Categories.OreOrbs, "Random Ore #" + idxStr, be.toString(),
			"The chance that the Ore Orb will produce a given block.  Values have two parts, and are separated by a "
				+ "comma.  The left side of the comma specifies the block name or Id, and the right side of the comma "
				+ "specifies the weighted chance to produce that block inside an Ore Orb.");
	}

	private Property GetRandomStairwayBlockEntryProperty(int index)
	{
		if (cfgFile == null) { return null; }

		BlockEntry be = GetDefaultStairBlockEntry(index);

		String idxStr = Integer.toString(index);
		while (idxStr.length() < 2)
		{
			idxStr = "0" + idxStr;
		}

		return cfgFile.get(Categories.OreOrbs, "Random Stairway #" + idxStr, be.toString(),
			"The chance that a given block will be present in an Ore Orb's stairway pattern.  Values have two parts, "
				+ "and are separated by a comma.  The left side of the comma specifies the block name or Id, and the "
				+ "right side of the comma specifies the weighted chance to produce that block as a part of an Ore "
				+ "Orb's stairway.");
	}

	// #endregion

	private ModConfig(World world)
	{
		this.World = world;

		// Setup Defaults
		List<BiomeEntry> entries = new ArrayList<BiomeEntry>();

		for (BiomeGenBase biome: BiomeGenBase.getBiomeGenArray())
		{
			if (biome != null)
			{
				if (!Utils.Any(Utils.Where(entries, SearchFor(biome))))
				{
					entries.add(new BiomeEntry(biome, GetDefaultBiomeWeight(biome)));
				}
			}
		}

		this.AllBiomes = Collections.unmodifiableList(entries);

		update();
	}

	public void update()
	{
		PerformFileMigrations();
		LoadConfigurationFromFile();
		SaveConfigurationToFile();

		PerformWorldMigrations();
		LoadConfigurationFromWorld();
		SaveConfigurationToWorld();
	}

	// #region Load & Save from World

	private void LoadConfigurationFromWorld()
	{
		if (this.World == null) { return; }
		if (!BiosphereWorldType.IsBiosphereWorld(this.World)) { return; }

		CustomWorldData data = CustomWorldData.FromWorld(this.World);
		if (data == null) { return; }

		setNoiseEnabled(getNoiseEnabledWorldProperty().ReadWorldValue(data));
		setScale(getScaleWorldProperty().ReadWorldValue(data));
		//setDomeBlock(getDomeBlockWorldProperty().ReadWorldValue(data));
		setOrbBlock(getOrbBlockWorldProperty().ReadWorldValue(data));
		setBridgeSupportBlock(getBridgeSupportBlockWorldProperty().ReadWorldValue(data));
		setBridgeRailBlock(getBridgeRailBlockWorldProperty().ReadWorldValue(data));
		setOutsideFillerBlock(getOutsideFillerBlockWorldProperty().ReadWorldValue(data));
		setTallGrassEnabled(getTallGrassEnabledWorldProperty().ReadWorldValue(data));
		setGridSize(getGridSizeWorldProperty().ReadWorldValue(data));
		setBridgeWidth(getBridgeWidthWorldProperty().ReadWorldValue(data));
		setMinSphereRadius(getMinSphereRadiusWorldProperty().ReadWorldValue(data));
		setMaxSphereRadius(getMaxSphereRadiusWorldProperty().ReadWorldValue(data));
		setOrbRadius(getOrbRadiusWorldProperty().ReadWorldValue(data));
		setMinLakeRatio(getMinLakeRatioWorldProperty().ReadWorldValue(data));
		setMaxLakeRatio(getMaxLakeRatioWorldProperty().ReadWorldValue(data));
		setSeaLevel(getSeaLevelWorldProperty().ReadWorldValue(data));

		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomOreBlockEntryProperty(i);

			if (prop != null)
			{
				String keyName = GetNewWorldProperty(prop);

				if (data.ContainsKey(keyName))
				{
					BlockEntry value = BlockEntry.Parse(data.get(keyName));

					if (OreOrbBlocks.size() > i)
					{
						OreOrbBlocks.set(i, value);
					}
					else
					{
						OreOrbBlocks.add(value);
					}
				}
			}
		}

		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomStairwayBlockEntryProperty(i);

			if (prop != null)
			{
				String keyName = GetNewWorldProperty(prop);

				if (data.ContainsKey(keyName))
				{
					BlockEntry value = BlockEntry.Parse(data.get(keyName));

					if (StairwayBlocks.size() > i)
					{
						StairwayBlocks.set(i, value);
					}
					else
					{
						StairwayBlocks.add(value);
					}
				}
			}
		}

		int biomeCount = 0;
		for (BiomeEntry entry: AllBiomes)
		{
			Property prop = GetBiomeEntryProperty(entry);
			if (prop != null)
			{
				entry.itemWeight = data.getInt(GetNewWorldProperty(prop), entry.itemWeight);
				biomeCount += entry.itemWeight;
			}
		}

		if (biomeCount <= 0)
		{
			LoadBiomeWeightsFromFile();
		}

		for (int i = 0; i < DOMETYPE_COUNT; i++)
		{
			if (DomeBlocks[i] == null) { DomeBlocks[i] = new ArrayList<BlockEntry>(); }

			for (int j = 0; j < DOMETYPE_BLOCK_COUNT; j++)
			{
				Property prop = GetDomeBlockProperty(i, j);
				if (prop != null)
				{
					String keyName = GetNewWorldProperty(prop);

					if (data.ContainsKey(keyName))
					{
						BlockEntry value = BlockEntry.Parse(data.get(keyName));

						//System.err.println(keyName + " = " + value.toString());

						if (DomeBlocks[i].size() > j)
						{
							DomeBlocks[i].set(j, value);
						}
						else
						{
							DomeBlocks[i].add(value);
						}
					}
				}
			}
		}
	}

	private void SaveConfigurationToWorld()
	{
		if (!BiosphereWorldType.IsBiosphereWorld(this.World)) { return; }

		CustomWorldData data = CustomWorldData.FromWorld(this.World);
		if (data == null) { return; }

		getNoiseEnabledWorldProperty().WriteWorldValue(data);
		getScaleWorldProperty().WriteWorldValue(data);
		//getDomeBlockWorldProperty().WriteWorldValue(data);
		getOrbBlockWorldProperty().WriteWorldValue(data);
		getBridgeSupportBlockWorldProperty().WriteWorldValue(data);
		getBridgeRailBlockWorldProperty().WriteWorldValue(data);
		getOutsideFillerBlockWorldProperty().WriteWorldValue(data);
		getTallGrassEnabledWorldProperty().WriteWorldValue(data);
		getGridSizeWorldProperty().WriteWorldValue(data);
		getBridgeWidthWorldProperty().WriteWorldValue(data);
		getMinSphereRadiusWorldProperty().WriteWorldValue(data);
		getMaxSphereRadiusWorldProperty().WriteWorldValue(data);
		getOrbRadiusWorldProperty().WriteWorldValue(data);
		getMinLakeRatioWorldProperty().WriteWorldValue(data);
		getMaxLakeRatioWorldProperty().WriteWorldValue(data);
		getSeaLevelWorldProperty().WriteWorldValue(data);

		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomOreBlockEntryProperty(i);

			if (prop != null)
			{
				String keyName = GetNewWorldProperty(prop);
				String value = "air, 0";

				if (OreOrbBlocks.size() > i)
				{
					value = OreOrbBlocks.get(i).toString();
				}

				data.put(keyName, value);
			}
		}

		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomStairwayBlockEntryProperty(i);

			if (prop != null)
			{
				String keyName = GetNewWorldProperty(prop);
				String value = "air, 0";

				if (StairwayBlocks.size() > i)
				{
					value = StairwayBlocks.get(i).toString();
				}

				data.put(keyName, value);
			}
		}

		for (BiomeEntry entry: AllBiomes)
		{
			Property prop = GetBiomeEntryProperty(entry);
			if (prop != null)
			{
				String keyName = GetNewWorldProperty(prop);
				data.put(keyName, entry.itemWeight);
			}
		}

		for (int i = 0; i < DOMETYPE_COUNT; i++)
		{
			for (int j = 0; j < DOMETYPE_BLOCK_COUNT; j++)
			{
				Property prop = GetDomeBlockProperty(i, j);
				if (prop != null)
				{
					String keyName = GetNewWorldProperty(prop);
					String value = "air, 0";

					if (DomeBlocks[i] != null && DomeBlocks[i].size() > j)
					{
						value = DomeBlocks[i].get(j).toString();
					}

					data.put(keyName, value);
				}
			}
		}
	}

	// #endregion

	// #region Load & Save from File

	private void LoadConfigurationFromFile()
	{
		if (cfgFile == null) { return; }

		this.setNoiseEnabled(getNoiseEnabledProperty().getBoolean());
		this.setScale((float)getScaleProperty().getDouble());

		//this.setDomeBlock(Utils.ParseBlock(getDomeBlockProperty().getString(), defaultDomeBlock));

		this.setOrbBlock
		(
			BlockData.Parse
			(
				getOrbBlockProperty().getString(),
				defaultOrbBlock
			)
		);

		this.setBridgeSupportBlock
		(
			BlockData.Parse
			(
				getBridgeSupportBlockProperty().getString(),
				defaultBridgeSupportBlock
			)
		);

		this.setBridgeRailBlock
		(
			BlockData.Parse
			(
				getBridgeRailBlockProperty().getString(),
				defaultBridgeRailBlock
			)
		);

		this.setOutsideFillerBlock
		(
			BlockData.Parse
			(
				getOutsideFillerBlockProperty().getString(),
				defaultOutsideFillerBlock
			)
		);

		this.setTallGrassEnabled(getTallGrassEnabledProperty().getBoolean());
		this.setGridSize(getGridSizeProperty().getInt());
		this.setBridgeWidth(getBridgeWidthProperty().getInt());
		this.setMinSphereRadius(getMinSphereRadiusProperty().getDouble());
		this.setMaxSphereRadius(getMaxSphereRadiusProperty().getDouble());
		this.setOrbRadius(getOrbRadiusProperty().getDouble());
		this.setMinLakeRatio(getMinLakeRatioProperty().getDouble());
		this.setMaxLakeRatio(getMaxLakeRatioProperty().getDouble());
		this.setSeaLevel(getSeaLevelProperty().getInt());

		LoadDomeBlocksFromFile();
		LoadOreBlocksFromFile();
		LoadStairwayBlocksFromFile();
		LoadBiomeWeightsFromFile();

		if (cfgFile.hasChanged())
		{
			cfgFile.save();
		}
	}

	private void LoadDomeBlocksFromFile()
	{
		for (int i = 0; i < DOMETYPE_COUNT; i++)
		{
			if (DomeBlocks[i] == null) { DomeBlocks[i] = new ArrayList<BlockEntry>(); }

			for (int j = 0; j < DOMETYPE_BLOCK_COUNT; j++)
			{
				Property prop = GetDomeBlockProperty(i, j);

				if (prop != null)
				{
					BlockEntry value = BlockEntry.Parse(prop.getString());

					if (DomeBlocks[i].size() > j)
					{
						DomeBlocks[i].set(j, value);
					}
					else
					{
						DomeBlocks[i].add(value);
					}
				}
			}
		}

	}

	private void LoadOreBlocksFromFile()
	{
		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomOreBlockEntryProperty(i);

			if (prop != null)
			{
				BlockEntry value = BlockEntry.Parse(prop.getString());

				if (OreOrbBlocks.size() > i)
				{
					OreOrbBlocks.set(i, value);
				}
				else
				{
					OreOrbBlocks.add(value);
				}
			}
		}

		int oreCount = 0;
		for (BlockEntry block: OreOrbBlocks)
		{
			oreCount += block.itemWeight;
		}

		if (oreCount <= 0)
		{
			OreOrbBlocks.add(new BlockEntry(Blx.stone, 0, 1));
		}
	}

	private void LoadStairwayBlocksFromFile()
	{
		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomStairwayBlockEntryProperty(i);

			if (prop != null)
			{
				BlockEntry value = BlockEntry.Parse(prop.getString());

				if (StairwayBlocks.size() > i)
				{
					StairwayBlocks.set(i, value);
				}
				else
				{
					StairwayBlocks.add(value);
				}
			}
		}

		int blockCount = 0;
		for (BlockEntry block: StairwayBlocks)
		{
			blockCount += block.itemWeight;
		}

		if (blockCount <= 0)
		{
			StairwayBlocks.add(new BlockEntry(Blx.air, 0, 1));
		}
	}

	private void LoadBiomeWeightsFromFile()
	{
		int count = 0;

		if (cfgFile != null)
		{
			for (BiomeEntry entry: AllBiomes)
			{
				Property prop = GetBiomeEntryProperty(entry);
				if (prop != null)
				{
					int weight = prop.getInt(GetDefaultBiomeWeight(entry.biome));
					if (weight < 0)
					{
						weight = 0;
					}

					entry.itemWeight = weight;
					count += weight;
				}
			}
		}

		if (count <= 0)
		{
			for (BiomeEntry entry: AllBiomes)
			{
				entry.itemWeight = GetDefaultBiomeWeight(entry.biome);
			}
		}
	}

	private void SaveConfigurationToFile()
	{
		if (cfgFile == null) { return; }

		getNoiseEnabledProperty().set(isNoiseEnabled());
		getScaleProperty().set(getScale());
		//getDomeBlockProperty().set(Utils.GetNameOrIdForBlock(getDomeBlock()));
		getOrbBlockProperty().set(getOrbBlock().toString());
		getBridgeSupportBlockProperty().set(getBridgeSupportBlock().toString());
		getBridgeRailBlockProperty().set(getBridgeRailBlock().toString());
		getOutsideFillerBlockProperty().set(getOutsideFillerBlock().toString());
		getTallGrassEnabledProperty().set(isTallGrassEnabled());
		getGridSizeProperty().set(getGridSize());
		getBridgeWidthProperty().set(getBridgeWidth());
		getMinSphereRadiusProperty().set(getMinSphereRadius());
		getMaxSphereRadiusProperty().set(getMaxSphereRadius());
		getOrbRadiusProperty().set(getOrbRadius());
		getMinLakeRatioProperty().set(getMinLakeRatio());
		getMaxLakeRatioProperty().set(getMaxLakeRatio());
		getSeaLevelProperty().set(getSeaLevel());

		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomOreBlockEntryProperty(i);

			if (prop != null)
			{
				String value = "air, 0";

				if (OreOrbBlocks.size() > i)
				{
					value = OreOrbBlocks.get(i).toString();
				}

				prop.set(value);
			}
		}

		for (int i = 0; i < BLOCK_COUNT; i++)
		{
			Property prop = GetRandomStairwayBlockEntryProperty(i);

			if (prop != null)
			{
				String value = "air, 0";

				if (StairwayBlocks.size() > i)
				{
					value = StairwayBlocks.get(i).toString();
				}

				prop.set(value);
			}
		}

		for (BiomeEntry entry: AllBiomes)
		{
			Property prop = GetBiomeEntryProperty(entry);
			if (prop != null)
			{
				prop.set(GetDefaultBiomeWeight(entry.biome));
			}
		}

		for (int i = 0; i < DOMETYPE_COUNT; i++)
		{
			for (int j = 0; j < DOMETYPE_BLOCK_COUNT; j++)
			{
				Property prop = GetDomeBlockProperty(i, j);
				if (prop != null)
				{
					String value = "air, 0";

					if (DomeBlocks[i] != null && DomeBlocks[i].size() > j)
					{
						value = DomeBlocks[i].get(j).toString();
					}

					prop.set(value);
				}
			}
		}

		if (cfgFile.hasChanged())
		{
			cfgFile.save();
		}
	}

	// #endregion
}
