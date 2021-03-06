package cy.jdkdigital.productivebees.setup;

import cy.jdkdigital.productivebees.ProductiveBees;
import cy.jdkdigital.productivebees.client.particle.FallingNectarParticle;
import cy.jdkdigital.productivebees.client.particle.LavaNectarParticle;
import cy.jdkdigital.productivebees.client.particle.PoppingNectarParticle;
import cy.jdkdigital.productivebees.client.particle.PortalNectarParticle;
import cy.jdkdigital.productivebees.client.render.block.BottlerTileEntityRenderer;
import cy.jdkdigital.productivebees.client.render.block.CentrifugeTileEntityRenderer;
import cy.jdkdigital.productivebees.client.render.block.FeederTileEntityRenderer;
import cy.jdkdigital.productivebees.client.render.block.JarTileEntityRenderer;
import cy.jdkdigital.productivebees.client.render.entity.DyeBeeRenderer;
import cy.jdkdigital.productivebees.client.render.entity.HoarderBeeRenderer;
import cy.jdkdigital.productivebees.client.render.entity.ProductiveBeeRenderer;
import cy.jdkdigital.productivebees.client.render.entity.RancherBeeRenderer;
import cy.jdkdigital.productivebees.common.entity.bee.ProductiveBeeEntity;
import cy.jdkdigital.productivebees.common.item.BeeBomb;
import cy.jdkdigital.productivebees.common.item.BeeCage;
import cy.jdkdigital.productivebees.common.item.NestLocator;
import cy.jdkdigital.productivebees.container.gui.*;
import cy.jdkdigital.productivebees.init.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScreenManager;
import net.minecraft.client.renderer.ItemRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.RenderTypeLookup;
import net.minecraft.client.renderer.entity.SpriteRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.item.ItemFrameEntity;
import net.minecraft.item.IItemPropertyGetter;
import net.minecraft.item.ItemModelsProperties;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.client.event.ParticleFactoryRegisterEvent;
import net.minecraftforge.fml.RegistryObject;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import net.minecraftforge.fml.client.registry.RenderingRegistry;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = ProductiveBees.MODID)
public class ClientSetup
{
    public static void init(final FMLClientSetupEvent event) {
        ScreenManager.registerFactory(ModContainerTypes.ADVANCED_BEEHIVE.get(), AdvancedBeehiveScreen::new);
        ScreenManager.registerFactory(ModContainerTypes.CENTRIFUGE.get(), CentrifugeScreen::new);
        ScreenManager.registerFactory(ModContainerTypes.POWERED_CENTRIFUGE.get(), CentrifugeScreen::new);
        ScreenManager.registerFactory(ModContainerTypes.BOTTLER.get(), BottlerScreen::new);
        ScreenManager.registerFactory(ModContainerTypes.FEEDER.get(), FeederScreen::new);
        ScreenManager.registerFactory(ModContainerTypes.INCUBATOR.get(), IncubatorScreen::new);
        ScreenManager.registerFactory(ModContainerTypes.CATCHER.get(), CatcherScreen::new);
        ScreenManager.registerFactory(ModContainerTypes.HONEY_GENERATOR.get(), HoneyGeneratorScreen::new);

        ItemModelsProperties.registerProperty(ModItems.BEE_CAGE.get(), new ResourceLocation("filled"), (stack, world, entity) -> BeeCage.isFilled(stack) ? 1.0F : 0.0F);
        ItemModelsProperties.registerProperty(ModItems.STURDY_BEE_CAGE.get(), new ResourceLocation("filled"), (stack, world, entity) -> BeeCage.isFilled(stack) ? 1.0F : 0.0F);
        ItemModelsProperties.registerProperty(ModItems.BEE_BOMB.get(), new ResourceLocation("loaded"), (stack, world, entity) -> BeeBomb.isLoaded(stack) ? 1.0F : 0.0F);
        ItemModelsProperties.registerProperty(ModItems.NEST_LOCATOR.get(), new ResourceLocation("angle"), new IItemPropertyGetter()
        {
            private double rotation;
            private double rota;
            private long lastUpdateTick;

            public float call(@Nonnull ItemStack stack, @Nullable ClientWorld world, @Nullable LivingEntity player) {
                if ((player != null || stack.isOnItemFrame()) && NestLocator.hasPosition(stack)) {
                    boolean flag = player != null;
                    Entity entity = flag ? player : stack.getItemFrame();
                    if (world == null && entity != null && entity.world instanceof ClientWorld) {
                        world = (ClientWorld) entity.world;
                    }
                    BlockPos pos = NestLocator.getPosition(stack);
                    if (entity != null && world != null && pos != null) {
                        double d1 = flag ? (double) entity.rotationYaw : this.getFrameRotation((ItemFrameEntity) entity);
                        d1 = MathHelper.positiveModulo(d1 / 360.0D, 1.0D);
                        double d2 = this.getPositionToAngle(pos, entity) / (double) ((float) Math.PI * 2F);
                        double d0 = 0.5D - (d1 - 0.25D - d2);

                        if (flag) {
                            d0 = this.wobble(world, d0);
                        }

                        return MathHelper.positiveModulo((float) d0, 1.0F);
                    }
                }
                return 0.0F;
            }

            private double wobble(World worldIn, double amount) {
                if (worldIn.getGameTime() != this.lastUpdateTick) {
                    this.lastUpdateTick = worldIn.getGameTime();
                    double d0 = amount - this.rotation;
                    d0 = MathHelper.positiveModulo(d0 + 0.5D, 1.0D) - 0.5D;
                    this.rota += d0 * 0.1D;
                    this.rota *= 0.8D;
                    this.rotation = MathHelper.positiveModulo(this.rotation + this.rota, 1.0D);
                }

                return this.rotation;
            }

            private double getFrameRotation(ItemFrameEntity frameEntity) {
                return MathHelper.wrapDegrees(180 + frameEntity.getHorizontalFacing().getHorizontalIndex() * 90);
            }

            private double getPositionToAngle(BlockPos blockpos, Entity entityIn) {
                return Math.atan2((double) blockpos.getZ() - entityIn.getPosZ(), (double) blockpos.getX() - entityIn.getPosX());
            }
        });

        ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.CENTRIFUGE.get(), CentrifugeTileEntityRenderer::new);
        ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.POWERED_CENTRIFUGE.get(), CentrifugeTileEntityRenderer::new);
        ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.BOTTLER.get(), BottlerTileEntityRenderer::new);
        ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.FEEDER.get(), FeederTileEntityRenderer::new);
        ClientRegistry.bindTileEntityRenderer(ModTileEntityTypes.JAR.get(), JarTileEntityRenderer::new);

        registerEntityRendering();
        registerBlockRendering();
    }

    public static void registerParticles(final ParticleFactoryRegisterEvent event) {
        Minecraft.getInstance().particles.registerFactory(ModParticles.COLORED_FALLING_NECTAR.get(), FallingNectarParticle.FallingNectarFactory::new);
        Minecraft.getInstance().particles.registerFactory(ModParticles.COLORED_POPPING_NECTAR.get(), PoppingNectarParticle.PoppingNectarFactory::new);
        Minecraft.getInstance().particles.registerFactory(ModParticles.COLORED_LAVA_NECTAR.get(), LavaNectarParticle.LavaNectarFactory::new);
        Minecraft.getInstance().particles.registerFactory(ModParticles.COLORED_PORTAL_NECTAR.get(), PortalNectarParticle.PortalNectarFactory::new);
    }

    private static void registerEntityRendering() {
        for (RegistryObject<EntityType<?>> registryObject : ModEntities.HIVE_BEES.getEntries()) {
            EntityType<?> bee = registryObject.get();
            String key = bee.getTranslationKey();
            if (key.contains("dye_bee")) {
                RenderingRegistry.registerEntityRenderingHandler((EntityType<? extends ProductiveBeeEntity>) bee, DyeBeeRenderer::new);
            }
            else if (key.contains("rancher_bee") || key.contains("farmer_bee")) {
                RenderingRegistry.registerEntityRenderingHandler((EntityType<? extends ProductiveBeeEntity>) bee, RancherBeeRenderer::new);
            }
            else if (key.contains("hoarder_bee")) {
                RenderingRegistry.registerEntityRenderingHandler((EntityType<? extends ProductiveBeeEntity>) bee, HoarderBeeRenderer::new);
            }
            else {
                RenderingRegistry.registerEntityRenderingHandler((EntityType<? extends ProductiveBeeEntity>) bee, ProductiveBeeRenderer::new);
            }
        }

        for (RegistryObject<EntityType<?>> registryObject : ModEntities.SOLITARY_BEES.getEntries()) {
            RenderingRegistry.registerEntityRenderingHandler((EntityType<? extends ProductiveBeeEntity>) registryObject.get(), ProductiveBeeRenderer::new);
        }

        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        RenderingRegistry.registerEntityRenderingHandler(ModEntities.BEE_BOMB.get(), entity -> new SpriteRenderer<>(entity, itemRenderer));
    }

    private static void registerBlockRendering() {
        RenderTypeLookup.setRenderLayer(ModBlocks.COMB_GHOSTLY.get(), RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(ModBlocks.SLIMY_NEST.get(), RenderType.getTranslucent());
        RenderTypeLookup.setRenderLayer(ModBlocks.BUMBLE_BEE_NEST.get(), RenderType.getCutoutMipped());
        RenderTypeLookup.setRenderLayer(ModBlocks.SUGAR_CANE_NEST.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(ModBlocks.JAR.get(), RenderType.getCutout());
        RenderTypeLookup.setRenderLayer(ModBlocks.INVISIBLE_REDSTONE_BLOCK.get(), RenderType.getCutout());
    }
}
