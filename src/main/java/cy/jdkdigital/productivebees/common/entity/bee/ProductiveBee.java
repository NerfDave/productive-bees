package cy.jdkdigital.productivebees.common.entity.bee;

import cy.jdkdigital.productivebees.ProductiveBees;
import cy.jdkdigital.productivebees.ProductiveBeesConfig;
import cy.jdkdigital.productivebees.common.block.Feeder;
import cy.jdkdigital.productivebees.common.block.entity.AdvancedBeehiveBlockEntityAbstract;
import cy.jdkdigital.productivebees.common.block.entity.FeederBlockEntity;
import cy.jdkdigital.productivebees.common.entity.bee.hive.RancherBee;
import cy.jdkdigital.productivebees.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiRecord;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoublePlantBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.items.CapabilityItemHandler;

import javax.annotation.Nonnull;
import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ProductiveBee extends Bee
{
    protected Map<BeeAttribute<?>, Object> beeAttributes = new HashMap<>();

    protected Predicate<PoiType> beehiveInterests = (poiType) -> {
        return poiType == PoiType.BEEHIVE;
    };
    private Color primaryColor = null;
    private Color secondaryColor = null;
    private boolean renderStatic;

    protected FollowParentGoal followParentGoal;
    protected BreedGoal breedGoal;
    protected BeeEnterHiveGoal enterHiveGoal;

    public ProductiveBee(EntityType<? extends Bee> entityType, Level world) {
        super(entityType, world);

        setAttributeValue(BeeAttributes.PRODUCTIVITY, level.random.nextInt(3));
        setAttributeValue(BeeAttributes.TEMPER, 1);
        setAttributeValue(BeeAttributes.ENDURANCE, level.random.nextInt(4));
        setAttributeValue(BeeAttributes.BEHAVIOR, 0);
        setAttributeValue(BeeAttributes.WEATHER_TOLERANCE, 0);
        setAttributeValue(BeeAttributes.TYPE, "hive");
        setAttributeValue(BeeAttributes.APHRODISIACS, ItemTags.FLOWERS);

        // Goal to make entity follow player, must be registered after init to use bee attributes
        this.goalSelector.addGoal(3, new ProductiveTemptGoal(this, 1.25D));
    }

    @Override
    protected void registerGoals() {
        registerBaseGoals();

        this.beePollinateGoal = new ProductiveBee.PollinateGoal();
        this.goalSelector.addGoal(4, this.beePollinateGoal);

        this.goToKnownFlowerGoal = new Bee.BeeGoToKnownFlowerGoal();
        this.goalSelector.addGoal(6, this.goToKnownFlowerGoal);

        this.goalSelector.addGoal(7, new Bee.BeeGrowCropGoal());
    }

    protected void registerBaseGoals() {
//        this.goalSelector.addGoal(0, new Bee.BeeAttackGoal(this, 1.4D, true));

        this.enterHiveGoal = new Bee.BeeEnterHiveGoal();
        this.goalSelector.addGoal(1, this.enterHiveGoal);

        this.breedGoal = new BreedGoal(this, 1.0D, ProductiveBee.class);
        this.goalSelector.addGoal(2, this.breedGoal);

        this.followParentGoal = new FollowParentGoal(this, 1.25D);
        this.goalSelector.addGoal(5, this.followParentGoal);

        this.goalSelector.addGoal(5, new ProductiveBee.UpdateNestGoal());
        this.goToHiveGoal = new ProductiveBee.FindNestGoal();
        this.goalSelector.addGoal(5, this.goToHiveGoal);

        this.goalSelector.addGoal(8, new Bee.BeeWanderGoal());
        this.goalSelector.addGoal(9, new FloatGoal(this));

        this.targetSelector.addGoal(1, (new Bee.BeeHurtByOtherGoal(this)).setAlertOthers());
        this.targetSelector.addGoal(2, new Bee.BeeBecomeAngryTargetGoal(this));

        // Empty default goals
        this.beePollinateGoal = new EmptyPollinateGoal();
        this.goToKnownFlowerGoal = new EmptyFindFlowerGoal();
    }

    @Override
    public void tick() {
        super.tick();

        // "Positive" effect to nearby players
        if (!level.isClientSide && tickCount % ProductiveBeesConfig.BEE_ATTRIBUTES.effectTicks.get() == 0) {
            BeeEffect effect = getBeeEffect();
            if (effect != null && effect.getEffects().size() > 0) {
                List<Player> players = level.getEntitiesOfClass(Player.class, (new AABB(new BlockPos(ProductiveBee.this.blockPosition()))).inflate(8.0D, 6.0D, 8.0D));
                if (players.size() > 0) {
                    players.forEach(playerEntity -> {
                        effect.getEffects().forEach((potionEffect, duration) -> {
                            playerEntity.addEffect(new MobEffectInstance(potionEffect, duration));
                        });
                    });
                }
            }
        }

        // Attribute improvement while leashed
        if (!level.isClientSide && isLeashed() && tickCount % ProductiveBeesConfig.BEE_ATTRIBUTES.leashedTicks.get() == 0) {
            // Rain tolerance improvements
            int tolerance = getAttributeValue(BeeAttributes.WEATHER_TOLERANCE);
            if (tolerance < 2 && level.random.nextFloat() < ProductiveBeesConfig.BEE_ATTRIBUTES.toleranceChance.get()) {
                if ((tolerance < 1 && level.isRaining()) || level.isThundering()) {
                    beeAttributes.put(BeeAttributes.WEATHER_TOLERANCE, tolerance + 1);
                }
            }
            // Behavior improvement
            int behavior = getAttributeValue(BeeAttributes.BEHAVIOR);
            if (behavior < 2 && level.random.nextFloat() < ProductiveBeesConfig.BEE_ATTRIBUTES.behaviorChance.get()) {
                // If diurnal, it can change to nocturnal
                if (behavior < 1 && level.isNight()) {
                    beeAttributes.put(BeeAttributes.BEHAVIOR, level.random.nextFloat() < 0.85F ? 1 : 2);
                }
                // If nocturnal, it can become metaturnal or back to diurnal
                else if (behavior == 1 && !level.isNight()) {
                    beeAttributes.put(BeeAttributes.BEHAVIOR, level.random.nextFloat() < 0.85F ? 2 : 0);
                }
            }

            // It might die when leashed outside
            boolean isInDanger = (tolerance < 1 && level.isRaining()) || (behavior < 1 && level.isNight());
            if (isInDanger && level.random.nextFloat() < ProductiveBeesConfig.BEE_ATTRIBUTES.damageChance.get()) {
                setHealth(getHealth() - (getMaxHealth() / 3) - 1);
            }
        }

        // Kill below Y level 0
        if (this.getY() < -0.0D) {
            this.outOfWorld();
        }
    }

    @Nonnull
    @Override
    public EntityDimensions getDimensions(Pose poseIn) {
        return super.getDimensions(poseIn).scale(getSizeModifier());
    }

    public float getSizeModifier() {
        return 1.0f;
    }

    @Override
    public boolean isAngry() { // isAngry
        return super.isAngry() && getAttributeValue(BeeAttributes.TEMPER) > 0;
    }

    @Override
    public boolean isFlowerValid(BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return false;
        }

        Block flowerBlock = level.getBlockState(pos).getBlock();

        return (
            isFlowerBlock(flowerBlock) ||
            (flowerBlock instanceof Feeder && isValidFeeder(level.getBlockEntity(pos), ProductiveBee.this::isFlowerBlock))
        );
    }

    public boolean doesHiveAcceptBee(BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity instanceof AdvancedBeehiveBlockEntityAbstract) {
            return ((AdvancedBeehiveBlockEntityAbstract) blockEntity).acceptsBee(this);
        }
        return true;
    }

    public static boolean isValidFeeder(BlockEntity tile, Predicate<Block> validator) {
        AtomicBoolean hasValidBlock = new AtomicBoolean(false);
        if (tile instanceof FeederBlockEntity) {
            tile.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY).ifPresent(handler -> {
                for (int slot = 0; slot < handler.getSlots(); ++slot) {
                    Item slotItem = handler.getStackInSlot(slot).getItem();
                    if (slotItem instanceof BlockItem && validator.test(((BlockItem) slotItem).getBlock())) {
                        hasValidBlock.set(true);
                    }
                }
            });
        }
        return hasValidBlock.get();
    }

    @Override
    public boolean wantsToEnterHive() {
        if (this.stayOutOfHiveCountdown <= 0 && !this.beePollinateGoal.isPollinating() && !this.hasStung() && this.getTarget() == null) {
            boolean shouldReturnToHive =
                this.isTiredOfLookingForNectar() ||
                this.hasNectar() ||
                (level.isNight() && !canOperateDuringNight()) ||
                (level.isRaining() && !canOperateDuringRain());

            return shouldReturnToHive && !this.isHiveNearFire();
        } else {
            return false;
        }
    }

    @Override
    public void setHasStung(boolean hasStung) {
        if (hasStung && getAttributeValue(BeeAttributes.ENDURANCE) == 2) {
            // 50% chance to not loose stinger
            hasStung = level.random.nextBoolean();
        }
        if (hasStung && getAttributeValue(BeeAttributes.ENDURANCE) == 3) {
            // 80% chance to not loose stinger
            hasStung = level.random.nextFloat() < .2;
        }
        super.setHasStung(hasStung);
    }

//    @Override
//    public boolean isBreedingItem(ItemStack itemStack) {
//        return itemStack.getItem().is(getAttributeValue(BeeAttributes.APHRODISIACS));
//    }

    public String getBeeType() {
        return getEncodeId();
    }

    public String getBeeName() {
        return getBeeName(true);
    }

    public String getBeeName(boolean stripName) {
        String[] types = getBeeType().split("[:]");
        String type = types[0];
        if (types.length > 1) {
            type = types[1];
        }
        return stripName ? type.replace("_bee", "") : type;
    }

    public String getRenderer() {
        return "default";
    }

    public <T> T getAttributeValue(BeeAttribute<T> parameter) {
        return (T) this.beeAttributes.get(parameter);
    }

    public void setAttributeValue(BeeAttribute<?> parameter, Integer value) {
        // Give health boost based on endurance
        if (parameter.equals(BeeAttributes.ENDURANCE)) {
            AttributeInstance healthMod = this.getAttribute(Attributes.MAX_HEALTH);
            if (healthMod != null && value != 1) {
                healthMod.removeModifier(BeeAttributes.HEALTH_MOD_ID_WEAK);
                healthMod.removeModifier(BeeAttributes.HEALTH_MOD_ID_MEDIUM);
                healthMod.removeModifier(BeeAttributes.HEALTH_MOD_ID_STRONG);
                healthMod.addPermanentModifier(BeeAttributes.HEALTH_MODS.get(value));
            }
        }

        this.beeAttributes.put(parameter, value);
    }

    public void setAttributeValue(BeeAttribute<?> parameter, Object value) {
        this.beeAttributes.put(parameter, value);
    }

    public Map<BeeAttribute<?>, Object> getBeeAttributes() {
        return beeAttributes;
    }

    public boolean canOperateDuringNight() {
        return getAttributeValue(BeeAttributes.BEHAVIOR) > 0;
    }

    boolean canOperateDuringRain() {
        return getAttributeValue(BeeAttributes.WEATHER_TOLERANCE) == 1;
    }

    boolean canOperateDuringThunder() {
        return getAttributeValue(BeeAttributes.WEATHER_TOLERANCE) == 2;
    }

    public int getTimeInHive(boolean hasNectar) {
        return hasNectar ? 2400 : 600;
    }

    public void setRenderStatic() {
        renderStatic = true;
    }

    public boolean getRenderStatic() {
        return renderStatic;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        if (getBeeName().equals("dye") && source.equals(DamageSource.WITHER)) {
            return true;
        }
        return source.equals(DamageSource.IN_WALL) || source.equals(DamageSource.SWEET_BERRY_BUSH) || super.isInvulnerableTo(source);
    }

    @Nonnull
    @Override
    protected PathNavigation createNavigation(@Nonnull Level worldIn) {
        PathNavigation navigator = super.createNavigation(worldIn);

        if (navigator instanceof FlyingPathNavigation) {
            navigator.setCanFloat(false);
            ((FlyingPathNavigation) navigator).setCanPassDoors(false);
        }
        return navigator;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);

        tag.putInt("bee_productivity", this.getAttributeValue(BeeAttributes.PRODUCTIVITY));
        tag.putInt("bee_endurance", this.getAttributeValue(BeeAttributes.ENDURANCE));
        tag.putInt("bee_temper", this.getAttributeValue(BeeAttributes.TEMPER));
        tag.putInt("bee_behavior", this.getAttributeValue(BeeAttributes.BEHAVIOR));
        tag.putInt("bee_weather_tolerance", this.getAttributeValue(BeeAttributes.WEATHER_TOLERANCE));
        tag.putString("bee_type", this.getAttributeValue(BeeAttributes.TYPE));
//        tag.putString("bee_aphrodisiac", this.getAttributeValue(BeeAttributes.APHRODISIACS).toString());
        tag.putFloat("MaxHealth", getMaxHealth());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);

        if (tag.contains("bee_productivity")) {
            beeAttributes.clear();
            setAttributeValue(BeeAttributes.PRODUCTIVITY, tag.getInt("bee_productivity"));
            setAttributeValue(BeeAttributes.ENDURANCE, tag.contains("bee_endurance") ? tag.getInt("bee_endurance") : 1);
            setAttributeValue(BeeAttributes.TEMPER, tag.getInt("bee_temper"));
            setAttributeValue(BeeAttributes.BEHAVIOR, tag.getInt("bee_behavior"));
            setAttributeValue(BeeAttributes.WEATHER_TOLERANCE, tag.getInt("bee_weather_tolerance"));
            setAttributeValue(BeeAttributes.TYPE, tag.getString("bee_type"));
//            setAttributeValue(BeeAttributes.APHRODISIACS, ItemTags.createOptional(new ResourceLocation(tag.getString("bee_aphrodisiac"))));
        }
    }

    @Override
    public ItemStack getPickedResult(HitResult target) {
        return BeeCreator.getSpawnEgg(this.getBeeType());
    }

    @Override
    protected void ageBoundaryReached() {
        super.ageBoundaryReached();

        if (!this.isBaby()) {
            BlockPos pos = blockPosition();
            if (level.isEmptyBlock(pos)) {
                this.setPos(pos.getX(), pos.getY(), pos.getZ());
            } else if (level.isEmptyBlock(pos.below())) {
                pos = pos.below();
                this.setPos(pos.getX(), pos.getY(), pos.getZ());
            }
        }
    }

    @Override
    public Bee getBreedOffspring(@Nonnull ServerLevel world, AgeableMob targetEntity) {
        Entity newBee = BeeHelper.getBreedingResult(this, targetEntity, world);

        if (!(newBee instanceof Bee)) {
            return EntityType.BEE.create(world);
        }

        if (newBee instanceof ProductiveBee) {
            BeeHelper.setOffspringAttributes((ProductiveBee) newBee, this, targetEntity);
        }

        return (Bee) newBee;
    }

    @Override
    public boolean canMate(@Nonnull Animal otherAnimal) {
        if (otherAnimal == this) {
            return false;
        } else if (!(otherAnimal instanceof Bee)) {
            return false;
        } else {
            return (
                this.isInLove() &&
                otherAnimal.isInLove()
            ) &&
                (
                    (level instanceof ServerLevel && BeeHelper.getRandomBreedingRecipe(this, otherAnimal, (ServerLevel) level) != null) || // check if there's an offspring recipe
                    canSelfBreed() || // allows self breeding
                    !(otherAnimal instanceof ProductiveBee) // or not a productive bee
                );
        }
    }

    public boolean canSelfBreed() {
        return true;
    }

    @Override
    protected float getStandingEyeHeight(Pose poseIn, EntityDimensions sizeIn) {
        return this.isBaby() ? sizeIn.height * 0.25F : sizeIn.height * 0.5F;
    }

    public void setColor(Color primary, Color secondary) {
        this.primaryColor = primary;
        this.secondaryColor = secondary;
    }

    public Color getColor(int tintIndex) {
        return tintIndex == 0 ? primaryColor : secondaryColor;
    }

    public boolean isFlowerBlock(Block flowerBlock) {
        return BlockTags.FLOWERS.contains(flowerBlock);
    }

    public Tag<Block> getNestingTag() {
        return BlockTags.BEEHIVES;
    }

    public BeeEffect getBeeEffect() {
        return null;
    }

    public class PollinateGoal extends Bee.BeePollinateGoal
    {
        public Predicate<BlockPos> flowerPredicate = (blockPos) -> {
            BlockState blockState = ProductiveBee.this.level.getBlockState(blockPos);
            boolean isInterested = false;
            try {
                if (blockState.getBlock() instanceof Feeder) {
                    isInterested = isValidFeeder(level.getBlockEntity(blockPos), ProductiveBee.this::isFlowerBlock);
                } else {
                    isInterested = ProductiveBee.this.isFlowerBlock(blockState.getBlock());
                    if (isInterested && blockState.is(BlockTags.TALL_FLOWERS)) {
                        if (blockState.getBlock() == Blocks.SUNFLOWER) {
                            isInterested = blockState.getValue(DoublePlantBlock.HALF) == DoubleBlockHalf.UPPER;
                        }
                    }
                }
            } catch (Exception e) {
                // early tag access
            }

            return isInterested;
        };

        public PollinateGoal() {
            super();
        }

        @Override
        public boolean canBeeUse() {
            if (ProductiveBee.this.remainingCooldownBeforeLocatingNewFlower > 0) {
                return false;
            } else if (ProductiveBee.this.hasNectar()) {
                return false;
            } else if (ProductiveBee.this.level.isRaining() && !ProductiveBee.this.canOperateDuringRain()) {
                return false;
            } else if (ProductiveBee.this.level.isThundering() && !ProductiveBee.this.canOperateDuringThunder()) {
                return false;
            } else if (ProductiveBee.this.random.nextFloat() <= 0.7F) {
                return false;
            } else {
                Optional<BlockPos> optional = this.findNearbyFlower();
                if (optional.isPresent()) {
                    ProductiveBee.this.savedFlowerPos = optional.get();
                    ProductiveBee.this.navigation.moveTo((double) ProductiveBee.this.savedFlowerPos.getX() + 0.5D, (double) ProductiveBee.this.savedFlowerPos.getY() + 0.5D, (double) ProductiveBee.this.savedFlowerPos.getZ() + 0.5D, 1.2F);
                    return true;
                }
                // Failing to find a target will set a cooldown before next attempt
                ProductiveBee.this.remainingCooldownBeforeLocatingNewFlower = 70 + level.random.nextInt(50);
                return false;
            }
        }

        @Nonnull
        @Override
        public Optional<BlockPos> findNearbyFlower() {
            if (ProductiveBee.this instanceof RancherBee) {
                return findEntities(RancherBee.predicate, 5D);
            }
            return this.findNearestBlock(this.flowerPredicate, 5);
        }

        private Optional<BlockPos> findNearestBlock(Predicate<BlockPos> predicate, double distance) {
            BlockPos blockpos = ProductiveBee.this.blockPosition();
            BlockPos.MutableBlockPos blockpos$mutableblockpos = new BlockPos.MutableBlockPos();

            for(int i = 0; (double)i <= distance; i = i > 0 ? -i : 1 - i) {
                for(int j = 0; (double)j < distance; ++j) {
                    for(int k = 0; k <= j; k = k > 0 ? -k : 1 - k) {
                        for(int l = k < j && k > -j ? j : 0; l <= j; l = l > 0 ? -l : 1 - l) {
                            blockpos$mutableblockpos.setWithOffset(blockpos, k, i - 1, l);
                            if (blockpos.closerThan(blockpos$mutableblockpos, distance) && predicate.test(blockpos$mutableblockpos)) {
                                return Optional.of(blockpos$mutableblockpos);
                            }
                        }
                    }
                }
            }

            return Optional.empty();
        }

        private Optional<BlockPos> findEntities(Predicate<Entity> predicate, double distance) {
            BlockPos blockpos = ProductiveBee.this.blockPosition();
            BlockPos.MutableBlockPos blockpos$mutable = new BlockPos.MutableBlockPos();

            List<Entity> ranchables = level.getEntities(ProductiveBee.this, (new AABB(blockpos).expandTowards(distance, distance, distance)), predicate);
            if (ranchables.size() > 0) {
                PathfinderMob entity = (PathfinderMob) ranchables.get(0);
                entity.getNavigation().setSpeedModifier(0);
                blockpos$mutable.set(entity.getX(), entity.getY(), entity.getZ());
                return Optional.of(blockpos$mutable);
            }

            return Optional.empty();
        }
    }

    public class FindNestGoal extends Bee.BeeGoToHiveGoal
    {
        public FindNestGoal() {
            super();
        }

        @Override
        public boolean canBeeUse() {
            if (!ProductiveBee.this.hasHive()) {
                return false;
            }

            Tag<Block> nestTag = ProductiveBee.this.getNestingTag();
            try {
                if (nestTag == null || nestTag.getValues().size() == 0) {
                    return false;
                }
            } catch (Exception e) {
                String bee = ProductiveBee.this.getEncodeId();
                if (ProductiveBee.this instanceof ConfigurableBee) {
                    bee = ProductiveBee.this.getBeeType();
                }
                ProductiveBees.LOGGER.debug("Nesting tag for " + bee + " not found. Looking for " + nestTag);
            }

            return !ProductiveBee.this.hasRestriction() &&
                    ProductiveBee.this.wantsToEnterHive() &&
                    !this.isCloseEnough(ProductiveBee.this.hivePos) &&
                    nestTag.contains(ProductiveBee.this.level.getBlockState(ProductiveBee.this.hivePos).getBlock());
        }

        private boolean isCloseEnough(BlockPos pos) {
            if (ProductiveBee.this.closerThan(pos, 2)) {
                return true;
            } else {
                Path path = ProductiveBee.this.navigation.getPath();
                return path != null && path.getTarget().equals(pos) && path.canReach() && path.isDone();
            }
        }

        @Override
        protected void blacklistTarget(BlockPos pos) {
            BlockEntity tileEntity = ProductiveBee.this.level.getBlockEntity(pos);
            Tag<Block> nestTag = ProductiveBee.this.getNestingTag();
            if (tileEntity != null && tileEntity.getBlockState().is(nestTag)) {
                this.blacklistedTargets.add(pos);

                while (this.blacklistedTargets.size() > 3) {
                    this.blacklistedTargets.remove(0);
                }
            }
        }
    }

    public class UpdateNestGoal extends Bee.BeeLocateHiveGoal
    {
        public UpdateNestGoal() {
            super();
        }

        @Override
        public void start() {
            ProductiveBee.this.remainingCooldownBeforeLocatingNewHive = 200;
            List<BlockPos> nearbyNests = this.getNearbyFreeNests();
            if (!nearbyNests.isEmpty()) {
                Iterator<BlockPos> iterator = nearbyNests.iterator();
                BlockPos blockPos;
                do {
                    if (!iterator.hasNext()) {
                        ProductiveBee.this.goToHiveGoal.clearBlacklist();
                        ProductiveBee.this.hivePos = nearbyNests.get(0);
                        return;
                    }

                    blockPos = iterator.next();
                } while (ProductiveBee.this.goToHiveGoal.isTargetBlacklisted(blockPos));

                ProductiveBee.this.hivePos = blockPos;
            }
        }

        private List<BlockPos> getNearbyFreeNests() {
            BlockPos pos = ProductiveBee.this.blockPosition();

            PoiManager poiManager = ((ServerLevel) ProductiveBee.this.level).getPoiManager();

            Stream<PoiRecord> stream = poiManager.getInRange(ProductiveBee.this.beehiveInterests, pos, 30, PoiManager.Occupancy.ANY);

            return stream
                    .map(PoiRecord::getPos)
                    .filter(ProductiveBee.this::doesHiveHaveSpace)
                    .filter(ProductiveBee.this::doesHiveAcceptBee)
                    .sorted(Comparator.comparingDouble((vec) -> vec.distSqr(pos)))
                    .collect(Collectors.toList());
        }
    }

    public class ProductiveTemptGoal extends TemptGoal
    {
        public ProductiveTemptGoal(PathfinderMob entity, double speed) {
            super(entity, speed, Ingredient.of(ItemTags.FLOWERS), false);
        }
    }

    public class EmptyPollinateGoal extends PollinateGoal
    {
        @Override
        public boolean canBeeUse() {
            return false;
        }
    }

    public class EmptyFindFlowerGoal extends BeeGoToKnownFlowerGoal
    {
        @Override
        public boolean canBeeUse() {
            return false;
        }
    }
}
