package fr.rakambda.fallingtree.neoforge.common;

import fr.rakambda.fallingtree.common.FallingTreeCommon;
import fr.rakambda.fallingtree.common.leaf.LeafBreakingHandler;
import fr.rakambda.fallingtree.common.network.ServerPacketHandler;
import fr.rakambda.fallingtree.common.wrapper.DirectionCompat;
import fr.rakambda.fallingtree.common.wrapper.IBlock;
import fr.rakambda.fallingtree.common.wrapper.IBlockPos;
import fr.rakambda.fallingtree.common.wrapper.IBlockState;
import fr.rakambda.fallingtree.common.wrapper.IComponent;
import fr.rakambda.fallingtree.common.wrapper.IEnchantment;
import fr.rakambda.fallingtree.common.wrapper.IItem;
import fr.rakambda.fallingtree.common.wrapper.IItemStack;
import fr.rakambda.fallingtree.common.wrapper.ILevel;
import fr.rakambda.fallingtree.common.wrapper.IPlayer;
import fr.rakambda.fallingtree.neoforge.client.event.PlayerLeaveListener;
import fr.rakambda.fallingtree.neoforge.common.wrapper.BlockWrapper;
import fr.rakambda.fallingtree.neoforge.common.wrapper.ComponentWrapper;
import fr.rakambda.fallingtree.neoforge.common.wrapper.EnchantmentWrapper;
import fr.rakambda.fallingtree.neoforge.common.wrapper.ItemStackWrapper;
import fr.rakambda.fallingtree.neoforge.common.wrapper.ItemWrapper;
import fr.rakambda.fallingtree.neoforge.event.BlockBreakListener;
import fr.rakambda.fallingtree.neoforge.event.FallingTreeBlockBreakEvent;
import fr.rakambda.fallingtree.neoforge.event.FallingTreeEnchantments;
import fr.rakambda.fallingtree.neoforge.event.LeafBreakingListener;
import fr.rakambda.fallingtree.neoforge.event.ServerCommandRegistrationListener;
import fr.rakambda.fallingtree.neoforge.network.NeoForgePacketHandler;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredHolder;
import org.jetbrains.annotations.NotNull;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static java.util.stream.Stream.empty;

public class FallingTreeCommonsImpl extends FallingTreeCommon<Direction>{
	private final IEventBus modEventBus;
	@Getter
	private final LeafBreakingHandler leafBreakingHandler;
	private final NeoForgePacketHandler packetHandler;
	@Getter
	private Collection<IEnchantment> chopperEnchantments;
	
	public FallingTreeCommonsImpl(@NotNull IEventBus modEventBus){
		this.modEventBus = modEventBus;
		
		leafBreakingHandler = new LeafBreakingHandler(this);
		chopperEnchantments = new ArrayList<>();
		packetHandler = new NeoForgePacketHandler(this);
	}
	
	@Override
	@NotNull
	public IComponent translate(@NotNull String key, Object... objects){
		Object[] vars = Arrays.stream(objects)
				.map(o -> {
					if(o instanceof IComponent component){
						return component.getRaw();
					}
					return o;
				})
				.toArray();
		return new ComponentWrapper(Component.translatable(key, vars));
	}
	
	@Override
	@NotNull
	public ServerPacketHandler getServerPacketHandler(){
		return packetHandler;
	}
	
	@Override
	@NotNull
	public Stream<IBlock> getBlock(@NotNull String name){
		try{
			var isTag = name.startsWith("#");
			if(isTag){
				name = name.substring(1);
			}
			var resourceLocation = new ResourceLocation(name);
			if(isTag){
				var tag = TagKey.create(Registries.BLOCK, resourceLocation);
				return getRegistryTagContent(BuiltInRegistries.BLOCK, tag).map(BlockWrapper::new);
			}
			return getRegistryElement(BuiltInRegistries.BLOCK, resourceLocation).stream().map(BlockWrapper::new);
		}
		catch(Exception e){
			return empty();
		}
	}
	
	@Override
	@NotNull
	public Stream<IItem> getItem(@NotNull String name){
		try{
			var isTag = name.startsWith("#");
			if(isTag){
				name = name.substring(1);
			}
			var resourceLocation = new ResourceLocation(name);
			if(isTag){
				var tag = TagKey.create(Registries.ITEM, resourceLocation);
				return getRegistryTagContent(BuiltInRegistries.ITEM, tag).map(ItemWrapper::new);
			}
			return getRegistryElement(BuiltInRegistries.ITEM, resourceLocation).stream().map(ItemWrapper::new);
		}
		catch(Exception e){
			return empty();
		}
	}
	
	@Override
	public boolean isLeafBlock(@NotNull IBlock block){
		var isAllowedBlock = registryTagContains(BuiltInRegistries.BLOCK, BlockTags.LEAVES, (Block) block.getRaw())
		                     || getConfiguration().getTrees().getAllowedLeaveBlocks(this).stream().anyMatch(leaf -> leaf.equals(block));
		if(isAllowedBlock){
			var isDeniedBlock = getConfiguration().getTrees().getDeniedLeaveBlocks(this).stream().anyMatch(leaf -> leaf.equals(block));
			return !isDeniedBlock;
		}
		return false;
	}
	
	@Override
	public boolean isLogBlock(@NotNull IBlock block){
		var isAllowedBlock = getConfiguration().getTrees().getDefaultLogsBlocks(this).stream().anyMatch(log -> log.equals(block))
		                     || getConfiguration().getTrees().getAllowedLogBlocks(this).stream().anyMatch(log -> log.equals(block));
		if(isAllowedBlock){
			var isDeniedBlock = getConfiguration().getTrees().getDeniedLogBlocks(this).stream().anyMatch(log -> log.equals(block));
			return !isDeniedBlock;
		}
		return false;
	}
	
	@Override
	@NotNull
	public Set<IBlock> getAllNonStrippedLogsBlocks(){
		return getRegistryTagContent(BuiltInRegistries.BLOCK, BlockTags.LOGS)
				.filter(block -> !Optional.of(BuiltInRegistries.BLOCK.getKey(block))
						.map(ResourceLocation::getPath)
						.map(name -> name.startsWith("stripped"))
						.orElse(false))
				.map(BlockWrapper::new)
				.collect(Collectors.toSet());
	}
	
	@Override
	@NotNull
	public DirectionCompat asDirectionCompat(@NotNull Direction dir){
		return DirectionCompat.valueOf(dir.name());
	}
	
	@Override
	@NotNull
	public Direction asDirection(@NotNull DirectionCompat dir){
		return Direction.valueOf(dir.name());
	}
	
	@Override
	public boolean isNetherWartOrShroomlight(@NotNull IBlock block){
		return registryTagContains(BuiltInRegistries.BLOCK, BlockTags.WART_BLOCKS, (Block) block.getRaw())
		       || Blocks.SHROOMLIGHT.equals(block.getRaw());
	}
	
	@Override
	public boolean isMangroveRoots(@NotNull IBlock block){
		return Blocks.MANGROVE_ROOTS.equals(block.getRaw());
	}
	
	@Override
	public boolean checkCanBreakBlock(@NotNull ILevel level, @NotNull IBlockPos blockPos, @NotNull IBlockState blockState, @NotNull IPlayer player){
		var event = NeoForge.EVENT_BUS.post(new FallingTreeBlockBreakEvent((Level) level.getRaw(), (BlockPos) blockPos.getRaw(), (BlockState) blockState.getRaw(), (Player) player.getRaw()));
		return !event.isCanceled();
	}
	
	@Override
	protected void performDefaultEnchantRegister(){
		FallingTreeEnchantments.registerDefault();
	}
	
	@Override
	protected void performSpecificEnchantRegister(){
		FallingTreeEnchantments.registerSpecific();
	}
	
	@Override
	protected void performCommitEnchantRegister(){
		FallingTreeEnchantments.commit(modEventBus);
		
		Stream.of(FallingTreeEnchantments.CHOPPER_ENCHANTMENT,
						FallingTreeEnchantments.CHOPPER_INSTANTANEOUS_ENCHANTMENT,
						FallingTreeEnchantments.CHOPPER_FALL_BLOCK_ENCHANTMENT,
						FallingTreeEnchantments.CHOPPER_FALL_ITEM_ENCHANTMENT,
						FallingTreeEnchantments.CHOPPER_SHIFT_DOWN_ENCHANTMENT
				)
				.filter(Objects::nonNull)
				.map(DeferredHolder::asOptional)
				.flatMap(Optional::stream)
				.map(EnchantmentWrapper::new)
				.forEach(chopperEnchantments::add);
	}

	@Override
	@NotNull
	public IItemStack getEmptyItemStack() {
		return new ItemStackWrapper(ItemStack.EMPTY);
	}

	@NotNull
	private <T> Optional<T> getRegistryElement(Registry<T> registryKey, ResourceLocation identifier){
		return registryKey.getHolder(identifier).map(Holder::value);
	}
	
	@NotNull
	private <T> Stream<T> getRegistryTagContent(@NotNull Registry<T> registry, @NotNull TagKey<T> tag){
		return registry.getTag(tag).stream()
				.flatMap(a -> a.stream().map(Holder::value));
	}
	
	private <T> boolean registryTagContains(@NotNull Registry<T> registry, @NotNull TagKey<T> tag, @NotNull T element){
		return getRegistryTagContent(registry, tag).anyMatch(element::equals);
	}
	
	public void registerForge(@NotNull IEventBus eventBus){
		getServerPacketHandler().registerServer();
		
		eventBus.register(new BlockBreakListener(this));
		eventBus.register(new LeafBreakingListener(this));
		eventBus.register(new PlayerLeaveListener(this));
		eventBus.register(new ServerCommandRegistrationListener(this));
		
		modEventBus.register(packetHandler);
	}
}
