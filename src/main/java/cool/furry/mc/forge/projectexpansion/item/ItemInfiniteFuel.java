package cool.furry.mc.forge.projectexpansion.item;

import cool.furry.mc.forge.projectexpansion.Main;
import cool.furry.mc.forge.projectexpansion.config.Config;
import cool.furry.mc.forge.projectexpansion.util.ColorStyle;
import cool.furry.mc.forge.projectexpansion.util.EMCFormat;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Rarity;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TranslationTextComponent;
import net.minecraft.world.World;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.server.ServerLifecycleHooks;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

public class ItemInfiniteFuel extends Item {

    public ItemInfiniteFuel() {
        super(new Item.Properties().maxStackSize(1).rarity(Rarity.RARE).group(Main.group));
    }

    @OnlyIn(Dist.CLIENT)
    @Override
    public void addInformation(ItemStack stack, @Nullable World world, List<ITextComponent> list, ITooltipFlag flag) {
        super.addInformation(stack, world, list, flag);
        list.add(new TranslationTextComponent("item.projectexpansion.infinite_fuel.tooltip").setStyle(ColorStyle.GRAY));
        list.add(new TranslationTextComponent("text.projectexpansion.cost", EMCFormat.getComponent(Config.infiniteFuelCost.get()).setStyle(ColorStyle.GRAY)).setStyle(ColorStyle.RED));
    }

    @Override
    public int getBurnTime(ItemStack stack) {
        @Nullable UUID owner = stack.getTag() == null ? null : stack.getTag().getUniqueId("Owner");
        if (owner == null) return 0;

        return (Config.infiniteFuelCost.get() == 0 || Config.infiniteFuelBurnTime.get() == 0) ? 0 : ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(owner).getEmc().compareTo(BigInteger.valueOf(Config.infiniteFuelCost.get())) < 0 ? 0 : Config.infiniteFuelBurnTime.get();
    }

    @Override
    public boolean hasContainerItem(ItemStack stack) {
        return true;
    }

    @Override
    public ItemStack getContainerItem(ItemStack stack) {
        @Nullable UUID owner = stack.getTag() == null ? null : stack.getTag().getUniqueId("Owner");
        if (owner == null) return stack;
        ServerPlayerEntity player = ServerLifecycleHooks.getCurrentServer().getPlayerList().getPlayerByUUID(owner);
        IKnowledgeProvider provider = ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(owner);
        provider.setEmc(provider.getEmc().subtract(BigInteger.valueOf(Config.infiniteFuelCost.get())));
        if (player != null) provider.sync(player);
        return stack;
    }
}
