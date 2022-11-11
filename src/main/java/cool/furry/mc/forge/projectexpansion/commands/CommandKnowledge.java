package cool.furry.mc.forge.projectexpansion.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import cool.furry.mc.forge.projectexpansion.config.Config;
import cool.furry.mc.forge.projectexpansion.util.ColorStyle;
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
import net.minecraft.util.text.TranslationTextComponent;

import javax.annotation.Nullable;
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
            .requires((source) -> source.hasPermission(2))
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
            return source.getPlayerOrException().getDisplayName();
        } catch (CommandSyntaxException e) {
            return new TranslationTextComponent("command.projectexpansion.console").setStyle(ColorStyle.RED);
        }
    }

    private static UUID getSourceUUID(CommandSource source) {
        try {
            return source.getPlayerOrException().getUUID();
        } catch (CommandSyntaxException e) {
            return Util.DUMMY_UUID;
        }
    }

    private static boolean compareUUID(CommandSource source, ServerPlayerEntity player) {
        try {
            return source.getPlayerOrException().getUUID().equals(player.getUUID());
        } catch (CommandSyntaxException e) {
            return false;
        }
    }

    private static int handle(CommandContext<CommandSource> ctx, ActionType action) throws CommandSyntaxException {
        ServerPlayerEntity player = EntityArgument.getPlayer(ctx, "player");
        boolean isSelf = compareUUID(ctx.getSource(), player);
        if(action == ActionType.CLEAR) {
            @Nullable IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
            if(provider == null) {
                ctx.getSource().sendFailure(new TranslationTextComponent("text.projectexpansion.failed_to_get_knowledge_provider", player.getDisplayName()).setStyle(ColorStyle.RED));
                return 0;
            }
            if(provider.getKnowledge().size() == 0) {
                if(isSelf) ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.clear.failSelf").setStyle(ColorStyle.RED));
                else ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.clear.fail", player.getDisplayName()).setStyle(ColorStyle.RED));
                return 0;
            }
            provider.clearKnowledge();
            provider.sync(player);
            if (compareUUID(ctx.getSource(), player)) ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.clear.successSelf").setStyle(ColorStyle.GREEN), false);
            else {
                ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.clear.success", player.getDisplayName()).setStyle(ColorStyle.GREEN), true);
                if(Config.notifyCommandChanges.get()) player.sendMessage(new TranslationTextComponent("command.projectexpansion.knowledge.clear.notification", getSourceName(ctx.getSource())), getSourceUUID(ctx.getSource()));
            }
            return 1;
        }
        Item item = ItemArgument.getItem(ctx, "item").getItem();

        @Nullable IKnowledgeProvider provider = Util.getKnowledgeProvider(player);
        if(provider == null) {
            ctx.getSource().sendFailure(new TranslationTextComponent("text.projectexpansion.failed_to_get_knowledge_provider", player.getDisplayName()).setStyle(ColorStyle.RED));
            return 0;
        }
        IEMCProxy proxy = ProjectEAPI.getEMCProxy();
        if (!proxy.hasValue(item)) {
            ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.invalid"));
            return 0;
        }
        int response = 1;
        switch(action) {
            case LEARN: {
                if (!provider.addKnowledge(ItemInfo.fromItem(item))) {
                    response = 0;
                    if (isSelf) ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.learn.failSelf", new ItemStack(item).getDisplayName()).setStyle(ColorStyle.RED));
                    else ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.learn.fail", player.getDisplayName(), new ItemStack(item).getDisplayName()).setStyle(ColorStyle.RED));
                } else {
                    if (isSelf) ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.learn.successSelf", new ItemStack(item).getDisplayName()).setStyle(ColorStyle.GREEN), false);
                    else {
                        ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.learn.success", player.getDisplayName(), new ItemStack(item).getDisplayName()).setStyle(ColorStyle.GRAY), true);
                        if (Config.notifyCommandChanges.get()) player.sendMessage(new TranslationTextComponent("command.projectexpansion.knowledge.learn.notification", new ItemStack(item).getDisplayName(), getSourceName(ctx.getSource())).setStyle(ColorStyle.GRAY), getSourceUUID(ctx.getSource()));
                    }
                }
                break;
            }

            case UNLEARN: {
                if (!provider.removeKnowledge(ItemInfo.fromItem(item))) {
                    response = 0;
                    if (isSelf) ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.failSelf", new ItemStack(item).getDisplayName()).setStyle(ColorStyle.RED));
                    else ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.fail", player.getDisplayName(), new ItemStack(item).getDisplayName()).setStyle(ColorStyle.RED));
                } else {
                    if (isSelf) ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.successSelf", new ItemStack(item).getDisplayName()).setStyle(ColorStyle.GREEN), false);
                    else {
                        ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.success", player.getDisplayName(), new ItemStack(item).getDisplayName()).setStyle(ColorStyle.GRAY), true);
                        if (Config.notifyCommandChanges.get()) player.sendMessage(new TranslationTextComponent("command.projectexpansion.knowledge.unlearn.notification", new ItemStack(item).getDisplayName(), getSourceName(ctx.getSource())).setStyle(ColorStyle.GRAY), getSourceUUID(ctx.getSource()));
                    }
                }
                break;
            }

            case TEST: {
                if (!provider.hasKnowledge(ItemInfo.fromItem(item))) {
                    response = 0;
                    if (isSelf) ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.test.failSelf", new ItemStack(item).getDisplayName()).setStyle(ColorStyle.RED));
                    else ctx.getSource().sendFailure(new TranslationTextComponent("command.projectexpansion.knowledge.test.fail", player.getDisplayName(), new ItemStack(item).getDisplayName()).setStyle(ColorStyle.RED));
                } else {
                    if (isSelf) ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.test.successSelf", new ItemStack(item).getDisplayName()).setStyle(ColorStyle.GREEN), false);
                    else ctx.getSource().sendSuccess(new TranslationTextComponent("command.projectexpansion.knowledge.test.success", player.getDisplayName(), new ItemStack(item).getDisplayName()).setStyle(ColorStyle.GRAY), false);
                }
                break;
            }
        }
        if(response == 1 && action != ActionType.TEST) provider.syncKnowledgeChange(player, NBTManager.getPersistentInfo(ItemInfo.fromItem(item)), true);

        return response;
    }
}
