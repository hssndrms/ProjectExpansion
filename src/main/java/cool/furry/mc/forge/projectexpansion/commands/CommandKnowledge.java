package cool.furry.mc.forge.projectexpansion.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import cool.furry.mc.forge.projectexpansion.config.Config;
import cool.furry.mc.forge.projectexpansion.util.Util;
import moze_intel.projecte.api.ItemInfo;
import moze_intel.projecte.api.ProjectEAPI;
import moze_intel.projecte.api.capabilities.IKnowledgeProvider;
import moze_intel.projecte.api.proxy.IEMCProxy;
import moze_intel.projecte.emc.nbt.NBTManager;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.command.arguments.ItemArgument;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.util.text.TranslationTextComponent;

import java.util.UUID;

public class CommandKnowledge {
    private enum ActionType {
        LEARN,
        UNLEARN,
        CLEAR,
        TEST
    }
    public static void register(CommandDispatcher<CommandSource> dispatcher) {
        LiteralArgumentBuilder<CommandSource> cmd = Commands.literal("knowledge")
            .requires((source) -> source.hasPermissionLevel(2))
            .then(Commands.literal("clear")
                .then(Commands.argument("player", EntityArgument.player())
                    .executes((ctx) -> CommandKnowledge.handle(ctx, ActionType.CLEAR))
                )
            )
            .then(Commands.literal("learn")
                .then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.argument("item", ItemArgument.item())
                        .executes((ctx) -> CommandKnowledge.handle(ctx, ActionType.LEARN))
                    )
              )
            )
            .then(Commands.literal("unlearn")
                 .then(Commands.argument("player", EntityArgument.player())
                     .then(Commands.argument("item", ItemArgument.item())
                         .executes((ctx) -> CommandKnowledge.handle(ctx, ActionType.UNLEARN))
                    )
                )
            )
            .then(Commands.literal("test")
                 .then(Commands.argument("player", EntityArgument.player())
                     .then(Commands.argument("item", ItemArgument.item())
                         .executes((ctx) -> CommandKnowledge.handle(ctx, ActionType.TEST))
                    )
                )
            );

        dispatcher.register(cmd);
    }

    private static ITextComponent getSourceName(CommandSource source) {
        try {
            return source.asPlayer().getDisplayName();
        } catch (CommandSyntaxException e) {
            return new TranslationTextComponent("command.projectexpansion.console").mergeStyle(TextFormatting.RED);
        }
    }

    private static UUID getSourceUUID(CommandSource source) {
        try {
            return source.asPlayer().getUniqueID();
        } catch (CommandSyntaxException e) {
            return Util.DUMMY_UUID;
        }
    }

    private static boolean compareUUID(CommandSource source, ServerPlayerEntity player) {
        try {
            return source.asPlayer().getUniqueID().equals(player.getUniqueID());
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    private static int handle(CommandContext<CommandSource> ctx, ActionType action) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgument.getPlayer(ctx, "player");
        boolean isSelf = compareUUID(ctx.getSource(), player);
        if(action == ActionType.CLEAR) {
            IKnowledgeProvider provider = ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(player.getUniqueID());
            if(provider.getKnowledge().size() == 0) {
                if(isSelf) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.clear.failSelf").mergeStyle(TextFormatting.RED), false);
                else ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.clear.fail", player.getDisplayName()).mergeStyle(TextFormatting.RED), false);
                return 0;
            }
            provider.clearKnowledge();
            provider.sync(player);
            if (compareUUID(ctx.getSource(), player)) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.clear.successSelf").mergeStyle(TextFormatting.GREEN), false);
            else {
                ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.clear.success", player.getDisplayName()).mergeStyle(TextFormatting.GREEN), true);
                if(Config.notifyCommandChanges.get()) player.sendMessage(new TranslationTextComponent("command.projectexpansion.knowledge.clear.notification", getSourceName(ctx.getSource())), getSourceUUID(ctx.getSource()));
            }
            return 1;
        }
        Item item = ItemArgument.getItem(ctx, "item").getItem();

        IKnowledgeProvider provider = ProjectEAPI.getTransmutationProxy().getKnowledgeProviderFor(player.getUniqueID());
        IEMCProxy proxy = ProjectEAPI.getEMCProxy();
        if (!proxy.hasValue(item)) {
            ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.invalid"), false);
            return 0;
        }
        int response = 1;
        switch(action) {
            case LEARN: {
                if (!provider.addKnowledge(ItemInfo.fromItem(item))) {
                    response = 0;
                    if (isSelf) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.learn.failSelf", new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.RED), false);
                    else ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.learn.fail", player.getDisplayName(), new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.RED), true);
                } else {
                    if (isSelf) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.learn.successSelf", new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.GREEN), false);
                    else {
                        ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.learn.success", player.getDisplayName(), new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.GRAY), true);
                        if (Config.notifyCommandChanges.get()) player.sendMessage(new TranslationTextComponent("command.projectexpansion.knowledge.learn.notification", new ItemStack(item).getTextComponent(), getSourceName(ctx.getSource())).mergeStyle(TextFormatting.GRAY), getSourceUUID(ctx.getSource()));
                    }
                }
                break;
            }

            case UNLEARN: {
                if (!provider.removeKnowledge(ItemInfo.fromItem(item))) {
                    response = 0;
                    if (isSelf) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.failSelf", new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.RED), false);
                    else ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.fail", player.getDisplayName(), new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.RED), true);
                } else {
                    if (isSelf) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.successSelf", new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.GREEN), false);
                    else {
                        ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.success", player.getDisplayName(), new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.GRAY), true);
                        if (Config.notifyCommandChanges.get()) player.sendMessage(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.notification", new ItemStack(item).getTextComponent(), getSourceName(ctx.getSource())).mergeStyle(TextFormatting.GRAY), getSourceUUID(ctx.getSource()));
                    }
                }
                break;
            }

            case TEST: {
                if (!provider.hasKnowledge(ItemInfo.fromItem(item))) {
                    response = 0;
                    if (isSelf) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.test.failSelf", new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.RED), false);
                    else ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.test.fail", player.getDisplayName(), new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.RED), false);
                } else {
                    if (isSelf) ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.test.successSelf", new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.GREEN), false);
                    else ctx.getSource().sendFeedback(new TranslationTextComponent("command.projectexpansion.knowledge.test.success", player.getDisplayName(), new ItemStack(item).getTextComponent()).mergeStyle(TextFormatting.GRAY), false);
                }
                break;
            }
        }
        if(response == 1 && action != ActionType.TEST) provider.syncKnowledgeChange(player, NBTManager.getPersistentInfo(ItemInfo.fromItem(item)), true);

        return response;
    }
}