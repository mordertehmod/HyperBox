package net.commoble.hyperbox;

import java.util.function.Supplier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.google.common.collect.Lists;
import com.mojang.serialization.MapCodec;

import net.commoble.hyperbox.blocks.ApertureBlock;
import net.commoble.hyperbox.blocks.ApertureBlockEntity;
import net.commoble.hyperbox.blocks.C2SSaveHyperboxPacket;
import net.commoble.hyperbox.blocks.HyperboxBlock;
import net.commoble.hyperbox.blocks.HyperboxBlockEntity;
import net.commoble.hyperbox.blocks.HyperboxMenu;
import net.commoble.hyperbox.client.ClientProxy;
import net.commoble.hyperbox.dimension.DelayedTeleportData;
import net.commoble.hyperbox.dimension.HyperboxChunkGenerator;
import net.commoble.hyperbox.dimension.HyperboxDimension;
import net.commoble.hyperbox.dimension.HyperboxSaveData;
import net.commoble.hyperbox.dimension.ReturnPoint;
import net.commoble.hyperbox.dimension.TeleportHelper;
import net.commoble.infiniverse.api.InfiniverseAPI;
import net.commoble.infiniverse.api.UnregisterDimensionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

@Mod(Hyperbox.MODID)
public class Hyperbox
{
	public static final String MODID = "hyperbox";
	public static Hyperbox modInstance;
	
	public static final ResourceLocation HYPERBOX_ID = id(Names.HYPERBOX);

	public static final ResourceKey<Biome> BIOME_KEY = ResourceKey.create(Registries.BIOME, HYPERBOX_ID);
	public static final ResourceKey<Level> WORLD_KEY = ResourceKey.create(Registries.DIMENSION, HYPERBOX_ID);
	public static final ResourceKey<LevelStem> DIMENSION_KEY = ResourceKey.create(Registries.LEVEL_STEM, HYPERBOX_ID);
	public static final ResourceKey<DimensionType> DIMENSION_TYPE_KEY = ResourceKey.create(Registries.DIMENSION_TYPE, HYPERBOX_ID);

	public static final int DEFAULT_COLOR = 0x4a354a;
	
	public final CommonConfig commonConfig;
	public final Supplier<HyperboxBlock> hyperboxBlock;
	public final Supplier<HyperboxBlock> hyperboxPreviewBlock;
	public final Supplier<ApertureBlock> apertureBlock;
	public final Supplier<Block> hyperboxWall;
	public final Supplier<BlockItem> hyperboxItem;
	public final Supplier<BlockEntityType<HyperboxBlockEntity>> hyperboxBlockEntityType;
	public final Supplier<BlockEntityType<ApertureBlockEntity>> apertureBlockEntityType;
	public final Supplier<MenuType<HyperboxMenu>> hyperboxMenuType;
	public final Supplier<MapCodec<HyperboxChunkGenerator>> hyperboxChunkGeneratorCodec;
	public final Supplier<AttachmentType<ReturnPoint>> returnPointAttachment;
	public final Supplier<DataComponentType<ResourceKey<Level>>> worldKeyDataComponent;
	
	public Hyperbox(IEventBus modBus)
	{
		modInstance = this;
		
		IEventBus forgeBus = NeoForge.EVENT_BUS;

		this.commonConfig = ConfigHelper.register(MODID, ModConfig.Type.COMMON, CommonConfig::new);
		
		// create and set up registrars
		DeferredRegister<Block> blocks = defreg(modBus, Registries.BLOCK);
		DeferredRegister<Item> items = defreg(modBus, Registries.ITEM);
		DeferredRegister<BlockEntityType<?>> tileEntities = defreg(modBus, Registries.BLOCK_ENTITY_TYPE);
		DeferredRegister<MenuType<?>> menuTypes = defreg(modBus, Registries.MENU);
		DeferredRegister<MapCodec<? extends ChunkGenerator>> chunkGeneratorCodecs = defreg(modBus, Registries.CHUNK_GENERATOR);
		DeferredRegister<AttachmentType<?>> attachmentTypes = defreg(modBus, NeoForgeRegistries.Keys.ATTACHMENT_TYPES);
		DeferredRegister<DataComponentType<?>> dataComponentTypes = defreg(modBus, Registries.DATA_COMPONENT_TYPE);
		
		this.hyperboxBlock = blocks.register(Names.HYPERBOX, () -> new HyperboxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_BLOCK).strength(2F, 1200F).isRedstoneConductor((state1, world, pos) -> false)));
		this.hyperboxPreviewBlock = blocks.register(Names.HYPERBOX_PREVIEW, () -> new HyperboxBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.PURPUR_BLOCK).strength(2F, 1200F).isRedstoneConductor((state1, world, pos) -> false)));
		this.hyperboxItem = items.register(Names.HYPERBOX, () -> new BlockItem(this.hyperboxBlock.get(), new Item.Properties()));
		this.hyperboxBlockEntityType = tileEntities.register(Names.HYPERBOX, () -> BlockEntityType.Builder.of(HyperboxBlockEntity::create, this.hyperboxBlock.get()).build(null));
		
		this.apertureBlock = blocks.register(Names.APERTURE, () -> new ApertureBlock(BlockBehaviour.Properties.ofFullCopy(Blocks.BARRIER).mapColor(MapColor.NONE).lightLevel(state -> 6).isRedstoneConductor((state1, world, pos) -> false)));
		this.apertureBlockEntityType = tileEntities.register(Names.APERTURE, () -> BlockEntityType.Builder.of(ApertureBlockEntity::create, this.apertureBlock.get()).build(null));
		
		this.hyperboxWall = blocks.register(Names.HYPERBOX_WALL,
				() -> new Block(BlockBehaviour.Properties
						.ofFullCopy(Blocks.BARRIER)
						.mapColor(MapColor.NONE)
						.lightLevel((BlockState state) -> 15)) {
					@Override
					public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos) {
						return true;
					}
				}
		);

		this.hyperboxMenuType = menuTypes.register(Names.HYPERBOX, () -> new MenuType<>((id, playerInventory) -> HyperboxMenu.makeClientMenu(id), FeatureFlags.VANILLA_SET));
		
		this.hyperboxChunkGeneratorCodec = chunkGeneratorCodecs.register(Names.HYPERBOX, HyperboxChunkGenerator::makeCodec);
		
		this.returnPointAttachment = attachmentTypes.register(Names.RETURN_POINT, () -> AttachmentType.builder(() -> ReturnPoint.EMPTY)
			.serialize(ReturnPoint.CODEC, rp -> rp.data().isPresent())
			.copyOnDeath()
			.build());
		
		this.worldKeyDataComponent = dataComponentTypes.register("world_key", () -> DataComponentType.<ResourceKey<Level>>builder()
			.persistent(ResourceKey.codec(Registries.DIMENSION))
			.networkSynchronized(ResourceKey.streamCodec(Registries.DIMENSION))
			.build());
		
		// subscribe event handlers
		modBus.addListener(EventPriority.LOW, this::registerDelegateCapabilities);
		modBus.addListener(this::onRegisterPayloads);
		modBus.addListener(this::onBuildTabContents);
		forgeBus.addListener(this::onUnregisterDimension);
		forgeBus.addListener(EventPriority.HIGH, this::onPreLevelTick);
		forgeBus.addListener(EventPriority.HIGH, this::onPostLevelTick);
		forgeBus.addListener(this::onLevelLoad);
		forgeBus.addListener(this::onLevelUnload);
		
		// subscribe client-build event handlers
		if (FMLEnvironment.dist.isClient())
		{
			ClientProxy.doClientModInit(modBus, forgeBus);
		}
	}
	
	private void registerDelegateCapabilities(RegisterCapabilitiesEvent event)
	{
		for (var blockCapability : BlockCapability.getAll())
		{
			genericallyRegisterBlockCap(event, blockCapability);
		}
	}
	
	@SuppressWarnings("unchecked")
	private <T,C> void genericallyRegisterBlockCap(@NotNull RegisterCapabilitiesEvent event, BlockCapability<T,C> blockCap)
	{
		event.registerBlockEntity(blockCap, hyperboxBlockEntityType.get(), (be, context) -> context instanceof Direction direction
			? be.getCapability((BlockCapability<T,Direction>)blockCap, direction)
			: null);
		event.registerBlockEntity(blockCap, apertureBlockEntityType.get(), (be, context) -> context instanceof Direction direction
			? be.getCapability((BlockCapability<T,Direction>)blockCap, direction)
			: null);
	}
	
	private void onRegisterPayloads(@NotNull RegisterPayloadHandlersEvent event)
	{
		event.registrar(MODID)
			.playToServer(C2SSaveHyperboxPacket.TYPE, C2SSaveHyperboxPacket.STREAM_CODEC, C2SSaveHyperboxPacket::handle);
	}
	
	private void onBuildTabContents(@NotNull BuildCreativeModeTabContentsEvent event)
	{
		if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS || event.getTabKey() == CreativeModeTabs.REDSTONE_BLOCKS)
		{
			event.accept(this.hyperboxItem.get());			
		}
	}
	
	private void onUnregisterDimension(@NotNull UnregisterDimensionEvent event)
	{
		ServerLevel level = event.getLevel();
		MinecraftServer server = level.getServer();
		DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
		if (level.dimensionType() == hyperboxDimensionType)
		{
			for (ServerPlayer player : Lists.newArrayList(level.players()))
			{
				TeleportHelper.ejectPlayerFromDeadWorld(player);
			}
		}
	}
	
	private void onPreLevelTick(LevelTickEvent.@NotNull Pre event)
	{
		if (!(event.getLevel() instanceof ServerLevel serverLevel))
		{
			return;
		}

		if (Boolean.TRUE.equals(this.commonConfig.autoForceHyperboxChunks.get()) && HyperboxDimension.getDimensionType(serverLevel.getServer()) == serverLevel.dimensionType())
		{
			int chunkRadius = 10;

			boolean isChunkForced = serverLevel.getForcedChunks().contains(HyperboxChunkGenerator.CHUNKID);
			boolean shouldChunkBeForced = shouldHyperboxChunkBeForced(serverLevel);

			for(int chunkX = 0; chunkX < chunkRadius; chunkX++) {
				for(int chunkZ = 0; chunkZ < chunkRadius; chunkZ++) {
					ChunkPos pos = new ChunkPos(chunkX, chunkZ);

					if (isChunkForced != shouldChunkBeForced) serverLevel.setChunkForced(pos.x, pos.z, true); // call shouldChunkBeForced
				}
			}
		}
	}
	
	private void onPostLevelTick(LevelTickEvent.@NotNull Post event)
	{		
		if (!(event.getLevel() instanceof ServerLevel serverLevel))
		{
			return;
		}
		MinecraftServer server = serverLevel.getServer();

		if (shouldUnloadDimension(server, serverLevel))
		{
			ResourceKey<Level> key = serverLevel.dimension();
			InfiniverseAPI.get().markDimensionForUnregistration(server, key);
		}

		DelayedTeleportData.tick(serverLevel);
	}
	
	public static boolean shouldUnloadDimension(MinecraftServer server, @Nonnull ServerLevel targetLevel)
	{
		DimensionType hyperboxDimensionType = HyperboxDimension.getDimensionType(server);
		if (hyperboxDimensionType != targetLevel.dimensionType())
			return false;
		
		if ((targetLevel.getGameTime() + targetLevel.hashCode()) % 20 != 0)
			return false;
		
		HyperboxSaveData hyperboxData = HyperboxSaveData.getOrCreate(targetLevel);
		ResourceKey<Level> parentKey = hyperboxData.getParentWorld();
		BlockPos parentPos = hyperboxData.getParentPos();
		
		@Nullable ServerLevel parentLevel = server.getLevel(parentKey);
		if (parentLevel == null)
			return true;

		int chunkX = parentPos.getX() >> 4;
		int chunkZ = parentPos.getZ() >> 4;

		if (!parentLevel.hasChunk(chunkX, chunkZ)) {
			if (Boolean.TRUE.equals(Hyperbox.modInstance.commonConfig.autoForceHyperboxChunks.get())) {
				int chunkRadius = 10;
				for (int x = 0; x < chunkRadius; x++) {
					for (int z = 0; z < chunkRadius; z++) {
						if (targetLevel.getForcedChunks().contains(ChunkPos.asLong(x, z))) {
							targetLevel.setChunkForced(x, z, false);
						}
					}
				}
			}
			return false;
		}
		
		BlockEntity te = parentLevel.getBlockEntity(parentPos);
		if (!(te instanceof HyperboxBlockEntity hyperbox))
			return true;
		
		ResourceKey<Level> key = targetLevel.dimension();
		
		return hyperbox.getLevelKey()
			.map(childKey -> !childKey.equals(key))
			.orElse(true);
	}

	@SuppressWarnings("deprecation")
	private static boolean shouldHyperboxChunkBeForced(@NotNull ServerLevel hyperboxLevel)
	{
		MinecraftServer server = hyperboxLevel.getServer();
		HyperboxSaveData data = HyperboxSaveData.getOrCreate(hyperboxLevel);
		ResourceKey<Level> parentKey = data.getParentWorld();
		ServerLevel parentLevel = server.getLevel(parentKey);
		if (parentLevel == null)
			return false;
		
		BlockPos parentPos = data.getParentPos();	
		return parentLevel.hasChunkAt(parentPos);
	}
	
	private static <T> @NotNull DeferredRegister<T> defreg(IEventBus modBus, ResourceKey<Registry<T>> registry)
	{
		DeferredRegister<T> register = DeferredRegister.create(registry, MODID);
		register.register(modBus);
		return register;
	}
	
	@Contract("_ -> new")
	public static @NotNull ResourceLocation id(String path)
	{
		return ResourceLocation.fromNamespaceAndPath(MODID, path);
	}


								// This should fix all "memory leaks" //


	private boolean registeredShutdownHook = false;
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(Hyperbox.class);										// Memory leak fix //

	private void onLevelLoad(LevelEvent.Load event) {
		if (!(event.getLevel() instanceof ServerLevel serverLevel))
			return;

		if (HyperboxDimension.getDimensionType(serverLevel.getServer()) == serverLevel.dimensionType()) {
			LOGGER.debug("Hyperbox dimension loaded: {}", serverLevel.dimension().location());

			if (!registeredShutdownHook) {
				NeoForge.EVENT_BUS.addListener(this::onServerStopping);
				registeredShutdownHook = true;
			}
		}
	}

	private void onLevelUnload(LevelEvent.Unload event) {
		if (!(event.getLevel() instanceof ServerLevel serverLevel))
			return;

		if (HyperboxDimension.getDimensionType(serverLevel.getServer()) == serverLevel.dimensionType()) {
			LOGGER.debug("Hyperbox dimension unloaded: {}", serverLevel.dimension().location());

			int chunkRadius = 10;
			for (int chunkX = 0; chunkX < chunkRadius; chunkX++) {
				for (int chunkZ = 0; chunkZ < chunkRadius; chunkZ++) {
					if (serverLevel.getForcedChunks().contains(ChunkPos.asLong(chunkX, chunkZ))) {
						serverLevel.setChunkForced(chunkX, chunkZ, false);
					}
				}
			}
		}
	}

	private void onServerStopping(ServerStoppingEvent event) {
		MinecraftServer server = event.getServer();
		LOGGER.debug("Server stopping, cleaning up all hyperbox dimensions");

		for (ServerLevel level : server.getAllLevels()) {
			if (HyperboxDimension.getDimensionType(server) == level.dimensionType()) {
				int chunkRadius = 10;
				for (int chunkX = 0; chunkX < chunkRadius; chunkX++) {
					for (int chunkZ = 0; chunkZ < chunkRadius; chunkZ++) {
						if (level.getForcedChunks().contains(ChunkPos.asLong(chunkX, chunkZ))) {
							level.setChunkForced(chunkX, chunkZ, false);
						}
					}
				}
			}
		}
	}
}
