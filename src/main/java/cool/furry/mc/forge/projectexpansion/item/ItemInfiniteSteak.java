package cool.furry.mc.forge.projectexpansion.item;

import cool.furry.mc.forge.projectexpansion.Main;
import cool.furry.mc.forge.projectexpansion.config.Config;
import cool.furry.mc.forge.projectexpansion.util.ColorStyle;
import cool.furry.mc.forge.projectexpansion.util.EMCFormat;
import cool.furry.mc.forge.projectexpansion.util.Util;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;

public class ItemInfiniteSteak extends Item {

    public ItemInfiniteSteak() {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE).tab(Main.tab));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(stack, level, list, flag);
        list.add(Component.translatable("item.projectexpansion.infinite_steak.tooltip").setStyle(ColorStyle.GRAY));
        list.add(Component.translatable("text.projectexpansion.cost", EMCFormat.getComponent(Config.infiniteSteakCost.get()).setStyle(ColorStyle.GRAY)).setStyle(ColorStyle.RED));
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return Items.COOKED_BEEF.getUseDuration(stack);
    }

    @Override
    public boolean isEdible() {
        return true;
    }

    @org.jetbrains.annotations.Nullable
    @Override
    public FoodProperties getFoodProperties(ItemStack stack, @Nullable LivingEntity entity) {
        return Items.COOKED_BEEF.getFoodProperties(stack, entity);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        @Nullable IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (!player.canEat(false) || Config.infiniteSteakCost.get() == 0 || provider == null || provider.getEmc().compareTo(BigInteger.valueOf(Config.infiniteSteakCost.get())) < 0) return InteractionResultHolder.fail(stack);
        player.startUsingItem(hand);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!(entity instanceof ServerPlayer player) || level.isClientSide) return stack;
        @Nullable IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if (provider == null) {
            player.displayClientMessage(Component.translatable("text.projectexpansion.failed_to_get_knowledge_provider", player.getDisplayName()).setStyle(ColorStyle.RED), true);
            return stack;
        }
        BigInteger emc = provider.getEmc().subtract(BigInteger.valueOf(Config.infiniteSteakCost.get()));
        if (emc.compareTo(BigInteger.ZERO) < 0) {
            player.displayClientMessage(Component.translatable("item.projectexpansion.infinite_steak.not_enough_emc", Component.literal(Config.infiniteSteakCost.get().toString())).setStyle(ColorStyle.RED), true);
            return stack;
        }
        provider.setEmc(emc);
        provider.syncEmc(player);
        player.eat(level, new ItemStack(Items.COOKED_BEEF));
        return stack;
    }
}
