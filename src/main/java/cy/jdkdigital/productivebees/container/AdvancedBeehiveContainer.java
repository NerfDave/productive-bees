package cy.jdkdigital.productivebees.container;

import cy.jdkdigital.productivebees.block.AdvancedBeehive;
import cy.jdkdigital.productivebees.init.ModContainerTypes;
import cy.jdkdigital.productivebees.tileentity.AdvancedBeehiveTileEntity;
import cy.jdkdigital.productivebees.tileentity.ItemHandlerHelper;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.PacketBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IWorldPosCallable;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import javax.annotation.Nonnull;
import java.util.*;

public class AdvancedBeehiveContainer extends AbstractContainer {

	public final AdvancedBeehiveTileEntity tileEntity;

	public static final HashMap<Integer, List<Integer>> BEE_POSITIONS = new HashMap<Integer, List<Integer>>() {{
		put(0, new ArrayList<Integer>() {{add(37);add(25);}});
		put(1, new ArrayList<Integer>() {{add(55);add(35);}});
		put(2, new ArrayList<Integer>() {{add(37);add(45);}});
	}};
	public static final HashMap<Integer, List<Integer>> BEE_POSITIONS_EXPANDED = new HashMap<Integer, List<Integer>>() {{
		put(0, new ArrayList<Integer>() {{add(19);add(24);}});
		put(1, new ArrayList<Integer>() {{add(19);add(45);}});
		put(2, new ArrayList<Integer>() {{add(37);add(35);}});
		put(3, new ArrayList<Integer>() {{add(55);add(24);}});
		put(4, new ArrayList<Integer>() {{add(55);add(45);}});
	}};
	
	private final IWorldPosCallable canInteractWithCallable;
	
	public AdvancedBeehiveContainer(final int windowId, final PlayerInventory playerInventory, final PacketBuffer data) {
		this(windowId, playerInventory, getTileEntity(playerInventory, data));
	}

	public AdvancedBeehiveContainer(final int windowId, final PlayerInventory playerInventory, final AdvancedBeehiveTileEntity tileEntity) {
		super(ModContainerTypes.ADVANCED_BEEHIVE.get(), windowId);

		this.tileEntity = tileEntity;
		this.canInteractWithCallable = IWorldPosCallable.of(tileEntity.getWorld(), tileEntity.getPos());

		IItemHandler inventory = new InvWrapper(playerInventory);

		// Inventory slots
		this.tileEntity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(inv -> {
			addSlotBox(inv, ItemHandlerHelper.OUTPUT_SLOTS[0], 116, 17, 3, 18, 3, 18);

			// Bottle slot
			addSlot(new ManualSlotItemHandler((ItemHandlerHelper.ItemHandler) inv, ItemHandlerHelper.BOTTLE_SLOT, 86, 17));
		});

		layoutPlayerInventorySlots(inventory, 0, 8, 84);
	}

	private static AdvancedBeehiveTileEntity getTileEntity(final PlayerInventory playerInventory, final PacketBuffer data) {
		Objects.requireNonNull(playerInventory, "playerInventory cannot be null!");
		Objects.requireNonNull(data, "data cannot be null!");
		final TileEntity tileAtPos = playerInventory.player.world.getTileEntity(data.readBlockPos());
		if (tileAtPos instanceof AdvancedBeehiveTileEntity) {
			List<String> inhabitantList = Arrays.asList(data.readString().split(","));
			((AdvancedBeehiveTileEntity) tileAtPos).inhabitantList = inhabitantList;
			return (AdvancedBeehiveTileEntity) tileAtPos;
		}
		throw new IllegalStateException("Tile entity is not correct! " + tileAtPos);
	}

	@Override
	public boolean canInteractWith(@Nonnull final PlayerEntity player) {
		return canInteractWithCallable.applyOrElse((world, pos) -> world.getBlockState(pos).getBlock() instanceof AdvancedBeehive && player.getDistanceSq((double) pos.getX() + 0.5D, (double) pos.getY() + 0.5D, (double) pos.getZ() + 0.5D) <= 64.0D, true);
	}
}
