package cy.jdkdigital.productivebees.integrations.jei;

import com.google.common.collect.Lists;
import cy.jdkdigital.productivebees.ProductiveBees;
import cy.jdkdigital.productivebees.init.ModBlocks;
import cy.jdkdigital.productivebees.recipe.CentrifugeRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.IRecipeLayout;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IGuiItemStackGroup;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredients;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.resources.I18n;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.crafting.Ingredient;
import net.minecraft.util.ResourceLocation;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

public class CentrifugeRecipeCategory implements IRecipeCategory<CentrifugeRecipe> {

    private final IDrawable background;
    private final IDrawable icon;

    public CentrifugeRecipeCategory(IGuiHelper guiHelper) {
        ResourceLocation location = new ResourceLocation(ProductiveBees.MODID, "textures/gui/jei/centrifuge_recipe.png");
        this.background = guiHelper.createDrawable(location, 0, 0, 126, 70);
        this.icon = guiHelper.createDrawableIngredient(new ItemStack(ModBlocks.CENTRIFUGE.get()));
    }

    @Nonnull
    @Override
    public ResourceLocation getUid() {
        return ProduciveBeesJeiPlugin.CATEGORY_CENTRIFUGE_UID;
    }

    @Nonnull
    @Override
    public Class<? extends CentrifugeRecipe> getRecipeClass() {
        return CentrifugeRecipe.class;
    }

    @Nonnull
    @Override
    public String getTitle() {
        return I18n.format("jei.productivebees.centrifuge");
    }

    @Nonnull
    @Override
    public IDrawable getBackground() {
        return this.background;
    }

    @Nonnull
    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setIngredients(@Nonnull CentrifugeRecipe recipe, @Nonnull IIngredients ingredients) {
        ingredients.setInputIngredients(Lists.newArrayList(recipe.ingredient, Ingredient.fromItems(Items.GLASS_BOTTLE)));

        List<ItemStack> outputList = new ArrayList<>();
        recipe.output.forEach((key, value) -> outputList.add(key));
        outputList.add(new ItemStack(Items.HONEY_BOTTLE));

        ingredients.setOutputs(VanillaTypes.ITEM, outputList);
    }

    @Override
    public void setRecipe(@Nonnull IRecipeLayout recipeLayout, @Nonnull CentrifugeRecipe recipe, @Nonnull IIngredients ingredients) {
        IGuiItemStackGroup itemStacks = recipeLayout.getItemStacks();
        List<List<ItemStack>> outputs = ingredients.getOutputs(VanillaTypes.ITEM);

        itemStacks.init(0, true, 4, 26);
        itemStacks.init(1, true, 36, 8);

        int startX = 68;
        int startY = 8;
        int offset = ingredients.getInputs(VanillaTypes.ITEM).size();
        IntStream.range(offset, outputs.size()+offset).forEach((i) -> {
            if (i > 9 + offset) return;
            itemStacks.init(i, false, startX + ((i-offset) * 18), startY + ((int) Math.floor(((float)i-offset) / 3.0F) * 18));
        });

        itemStacks.set(ingredients);
    }
}
