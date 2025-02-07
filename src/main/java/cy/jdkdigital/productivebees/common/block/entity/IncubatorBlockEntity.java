package cy.jdkdigital.productivebees.common.block.entity;

import cy.jdkdigital.productivebees.ProductiveBees;
import cy.jdkdigital.productivebees.ProductiveBeesConfig;
import cy.jdkdigital.productivebees.common.item.BeeCage;
import cy.jdkdigital.productivebees.common.item.Gene;
import cy.jdkdigital.productivebees.common.item.HoneyTreat;
import cy.jdkdigital.productivebees.container.IncubatorContainer;
import cy.jdkdigital.productivebees.init.ModBlocks;
import cy.jdkdigital.productivebees.init.ModItems;
import cy.jdkdigital.productivebees.init.ModTags;
import cy.jdkdigital.productivebees.init.ModTileEntityTypes;
import cy.jdkdigital.productivebees.util.BeeCreator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.EnergyStorage;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class IncubatorBlockEntity extends CapabilityBlockEntity implements MenuProvider, UpgradeableBlockEntity
{
    public int recipeProgress = 0;
    public boolean isRunning = false;

    private LazyOptional<IItemHandlerModifiable> inventoryHandler = LazyOptional.of(() -> new InventoryHandlerHelper.ItemHandler(3, this)
    {
        @Override
        public boolean isInputSlotItem(int slot, Item item) {
            return
                (slot == 0 && item instanceof BeeCage) ||
                (slot == 0 && ModTags.EGGS.contains(item)) ||
                (slot == 1 && item instanceof HoneyTreat);
        }
    });

    private void setRunning(boolean running) {
        isRunning = running;
    }

    protected LazyOptional<IItemHandlerModifiable> upgradeHandler = LazyOptional.of(() -> new InventoryHandlerHelper.UpgradeHandler(4, this));

    protected LazyOptional<IEnergyStorage> energyHandler = LazyOptional.of(() -> new EnergyStorage(10000));

    public IncubatorBlockEntity(BlockPos pos, BlockState state) {
        super(ModTileEntityTypes.INCUBATOR.get(), pos, state);
    }

    public int getProcessingTime() {
        return (int) (
                ProductiveBeesConfig.GENERAL.incubatorProcessingTime.get() * getProcessingTimeModifier()
        );
    }

    protected double getProcessingTimeModifier() {
        double timeUpgradeModifier = 1 - (getUpgradeCount(ModItems.UPGRADE_TIME.get()) * ProductiveBeesConfig.UPGRADES.timeBonus.get());

        return Math.max(0, timeUpgradeModifier);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, IncubatorBlockEntity blockEntity) {
        if (blockEntity.isRunning && level instanceof ServerLevel) {
            blockEntity.energyHandler.ifPresent(handler -> {
                handler.extractEnergy((int) (ProductiveBeesConfig.GENERAL.incubatorPowerUse.get() * blockEntity.getEnergyConsumptionModifier()), false);
            });
        }
        blockEntity.inventoryHandler.ifPresent(invHandler -> {
            if (!invHandler.getStackInSlot(0).isEmpty()) {
                // Process incubation
                if (blockEntity.isRunning || blockEntity.canProcessInput(invHandler)) {
                    blockEntity.setRunning(true);
                    int totalTime = blockEntity.getProcessingTime();

                    if (++blockEntity.recipeProgress >= totalTime) {
                        blockEntity.completeIncubation(invHandler);
                        blockEntity.recipeProgress = 0;
                        blockEntity.setChanged();
                    }
                }
            } else {
                blockEntity.recipeProgress = 0;
                blockEntity.setRunning(false);
            }
        });
    }

    protected double getEnergyConsumptionModifier() {
        double timeUpgradeModifier = getUpgradeCount(ModItems.UPGRADE_TIME.get()) * ProductiveBeesConfig.UPGRADES.timeBonus.get();

        return Math.max(1, timeUpgradeModifier);
    }

    /**
     * Two recipes can be processed here, babees to adults and eggs to spawn eggs
     */
    private boolean canProcessInput(IItemHandlerModifiable invHandler) {
        int energy = energyHandler.map(IEnergyStorage::getEnergyStored).orElse(0);
        ItemStack inItem = invHandler.getStackInSlot(0);
        ItemStack treatItem = invHandler.getStackInSlot(1);

        boolean eggProcessing = ModTags.EGGS.contains(inItem.getItem());
        boolean cageProcessing = inItem.getItem() instanceof BeeCage && BeeCage.isFilled(inItem);

        return energy > ProductiveBeesConfig.GENERAL.incubatorPowerUse.get() // has enough power
                && (eggProcessing || cageProcessing) // valid processing
                && invHandler.getStackInSlot(2).isEmpty() // output has room
                && treatItem.getItem().equals(ModItems.HONEY_TREAT.get())
                && (
                    (cageProcessing && treatItem.getCount() >= ProductiveBeesConfig.GENERAL.incubatorTreatUse.get()) ||
                    (eggProcessing && !treatItem.isEmpty() && HoneyTreat.hasBeeType(treatItem))
                );
    }

    private void completeIncubation(IItemHandlerModifiable invHandler) {
        if (canProcessInput(invHandler)) {
            ItemStack inItem = invHandler.getStackInSlot(0);

            boolean eggProcessing = ModTags.EGGS.contains(inItem.getItem());
            boolean cageProcessing = inItem.getItem() instanceof BeeCage;

            if (canProcessInput(invHandler)) {
                if (cageProcessing) {
                    CompoundTag nbt = inItem.getTag();
                    if (nbt != null && nbt.contains("Age")) {
                        nbt.putInt("Age", 0);
                    }
                    invHandler.setStackInSlot(2, inItem);
                    invHandler.getStackInSlot(1).shrink(ProductiveBeesConfig.GENERAL.incubatorTreatUse.get());
                    invHandler.setStackInSlot(0, ItemStack.EMPTY);
                } else if (eggProcessing) {
                    ItemStack treatItem = invHandler.getStackInSlot(1);

                    ListTag genes = HoneyTreat.getGenes(treatItem);
                    for (Tag inbt : genes) {
                        ItemStack insertedGene = ItemStack.of((CompoundTag) inbt);
                        String beeName = Gene.getAttributeName(insertedGene);
                        if (!beeName.isEmpty()) {
                            int purity = Gene.getPurity(insertedGene);
                            if (((CompoundTag) inbt).contains("purity")) {
                                purity = ((CompoundTag) inbt).getInt("purity");
                            }
                            if (ProductiveBees.rand.nextInt(100) <= purity) {
                                ItemStack egg = BeeCreator.getSpawnEgg(beeName);
                                if (egg.getItem() instanceof SpawnEggItem) {
                                    invHandler.setStackInSlot(2, egg);
                                }
                            }
                        }
                    }

                    inItem.shrink(1);
                    invHandler.getStackInSlot(1).shrink(1);
                }
            }
        }
    }

    @Override
    public LazyOptional<IItemHandlerModifiable> getUpgradeHandler() {
        return upgradeHandler;
    }

    @Override
    public void setChanged() {
        super.setChanged();
        setRunning(false);
    }

    @Override
    public void loadPacketNBT(CompoundTag tag) {
        super.loadPacketNBT(tag);

        recipeProgress = tag.getInt("RecipeProgress");
    }

    @Override
    public void savePacketNBT(CompoundTag tag) {
        super.savePacketNBT(tag);

        tag.putInt("RecipeProgress", recipeProgress);
    }

    @Nonnull
    @Override
    public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side) {
        if (cap == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return inventoryHandler.cast();
        }
        else if (cap == CapabilityEnergy.ENERGY) {
            return energyHandler.cast();
        }
        return super.getCapability(cap, side);
    }

    @Nonnull
    @Override
    public Component getName() {
        return new TranslatableComponent(ModBlocks.INCUBATOR.get().getDescriptionId());
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(final int windowId, final Inventory playerInventory, final Player player) {
        return new IncubatorContainer(windowId, playerInventory, this);
    }
}
