/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/item/ItemCommandBaton.
 */
package io.github.capttracker.ancientwarfarenpc.item;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import io.github.capttracker.ancientwarfare.input.IItemKeyInterface;
import io.github.capttracker.ancientwarfare.input.IScrollableItem;
import io.github.capttracker.ancientwarfare.util.RayTraceUtils;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.init.AwNpcItemComponents;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand;
import io.github.capttracker.ancientwarfarenpc.npccommand.NpcCommand.CommandType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The command baton: a melee weapon (attack 4 plus the tier's bonus, speed -2.3) that selects NPCs and gives them commands.
 *
 * <p>Right click an NPC you may command to add it to or remove it from the baton's list (the {@code baton_entities} component, the UUIDs of
 * the NPCs; ones that cannot be found any more are dropped on each click). Sneak plus mouse wheel changes the {@link BatonMode} (the
 * {@code baton_mode} component, default {@link BatonMode#CLEAR_COMMAND}). The primary alt key runs on the client: it ray traces up to
 * {@value #RANGE} blocks and sends the command of the current mode with {@link NpcCommand#handleCommandClient}; the server hands the
 * command to the listed NPCs ({@link #getCommandedEntities}).
 *
 * <p>1.12.2 wrote a default {@code mode} tag into the stack from the tooltip call; that client side change is not ported, the component
 * default does the job.
 */
public class ItemCommandBaton extends Item implements IItemKeyInterface, IScrollableItem {
    /** The range of the command ray trace in blocks (1.12.2: {@code range = 120}, with a TODO to make it a config option). */
    public static final int RANGE = 120;
    /** How far around the player the client looks for the listed NPCs. */
    private static final double CLIENT_SEARCH_RANGE = 256;

    private final Tier tier;

    public ItemCommandBaton(Properties properties, Tier tier) {
        super(properties.attributes(NpcItemAttributes.melee(4 + tier.getAttackDamageBonus(), -2.3D)));
        this.tier = tier;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("RMB = ").append(Component.translatable("guistrings.npc.baton.add_remove")));
        tooltip.add(Component.empty().append(ItemAltFunction.ALT_FUNCTION_1.keyDisplay()).append(" = Execute Command: ")
                .append(Component.translatable(getMode(stack).getTranslationKey())));
        tooltip.add(Component.literal("Use scroll wheel to change mode when sneaking"));
    }

    @Override
    public int getEnchantmentValue() {
        return tier.getEnchantmentValue();
    }

    @Override
    public boolean isValidRepairItem(ItemStack toRepair, ItemStack repair) {
        return tier.getRepairIngredient().test(repair) || super.isValidRepairItem(toRepair, repair);
    }

    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        return true;
    }

    @Override
    public void postHurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        stack.hurtAndBreak(1, attacker, EquipmentSlot.MAINHAND);
    }

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miner) {
        if (!level.isClientSide && state.getDestroySpeed(level, pos) != 0.0F) {
            stack.hurtAndBreak(2, miner, EquipmentSlot.MAINHAND);
        }
        return true;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            //TODO (from the original) openGUI
        } else {
            HitResult hit = RayTraceUtils.getPlayerTarget(player, RANGE, 0);
            if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof NpcBase npc
                    && npc.hasCommandPermissions(player.getUUID(), player.getGameProfile().getName())) {
                onNpcClicked(npc, stack);
            }
        }
        return InteractionResultHolder.success(stack);
    }

    // ---- key action and scrolling ----

    @Override
    public void onKeyAction(Player player, ItemStack stack, ItemAltFunction altFunction) {
        //noop on the server (the command is sent from the client, see onKeyActionClient)
    }

    @Override
    public boolean onKeyActionClient(Player player, ItemStack stack, ItemAltFunction altFunction) {
        if (altFunction == ItemAltFunction.ALT_FUNCTION_1) {
            switch (getMode(stack)) {
                case CLEAR_COMMAND -> NpcCommand.handleCommandClient(CommandType.CLEAR_COMMAND, new EntityHitResult(player));
                case ATTACK -> {
                    HitResult hit = RayTraceUtils.getPlayerTarget(player, RANGE, 0);
                    if (hit != null) {
                        NpcCommand.handleCommandClient(hit.getType() == HitResult.Type.ENTITY ? CommandType.ATTACK : CommandType.ATTACK_AREA, hit);
                    }
                }
                case MOVE -> {
                    HitResult hit = RayTraceUtils.getPlayerTarget(player, RANGE, 0);
                    if (hit != null) {
                        NpcCommand.handleCommandClient(hit.getType() == HitResult.Type.ENTITY ? CommandType.GUARD : CommandType.MOVE, hit);
                    }
                }
                case SET_HOME -> sendIfBlock(player, CommandType.SET_HOME);
                case CLEAR_HOME -> NpcCommand.handleCommandClient(CommandType.CLEAR_HOME, RayTraceUtils.getPlayerTarget(player, RANGE, 0));
                case SET_UPKEEP -> sendIfBlock(player, CommandType.SET_UPKEEP);
                case CLEAR_UPKEEP -> NpcCommand.handleCommandClient(CommandType.CLEAR_UPKEEP, RayTraceUtils.getPlayerTarget(player, RANGE, 0));
            }
        }
        return false;
    }

    private static void sendIfBlock(Player player, CommandType type) {
        HitResult hit = RayTraceUtils.getPlayerTarget(player, RANGE, 0);
        if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
            NpcCommand.handleCommandClient(type, hit);
        }
    }

    @Override
    public boolean onScrollUp(Level world, Player player, ItemStack stack) {
        if (!world.isClientSide) {
            changeMode(getMode(stack).next(), player, stack);
        }
        return true;
    }

    @Override
    public boolean onScrollDown(Level world, Player player, ItemStack stack) {
        if (!world.isClientSide) {
            changeMode(getMode(stack).previous(), player, stack);
        }
        return true;
    }

    private void changeMode(BatonMode mode, Player player, ItemStack stack) {
        player.sendSystemMessage(Component.translatable(mode.getTranslationKey()));
        stack.set(AwNpcItemComponents.BATON_MODE.get(), mode);
    }

    public static BatonMode getMode(ItemStack stack) {
        return stack.getOrDefault(AwNpcItemComponents.BATON_MODE.get(), BatonMode.getDefault());
    }

    // ---- the list of commanded NPCs ----

    private static Set<UUID> getIds(ItemStack stack) {
        List<UUID> ids = stack.get(AwNpcItemComponents.BATON_ENTITIES.get());
        return ids == null ? new LinkedHashSet<>() : new LinkedHashSet<>(ids);
    }

    private static void setIds(ItemStack stack, Set<UUID> ids) {
        stack.set(AwNpcItemComponents.BATON_ENTITIES.get(), List.copyOf(ids));
    }

    /** Toggles the NPC in the list, then drops the ids that cannot be found any more (server side). */
    private void onNpcClicked(NpcBase npc, ItemStack stack) {
        Set<UUID> ids = getIds(stack);
        if (!ids.remove(npc.getUUID())) {
            ids.add(npc.getUUID());
        }
        if (npc.level() instanceof ServerLevel serverLevel) {
            // costly, so only done on a click
            ids.removeIf(id -> id == null || serverLevel.getEntity(id) == null);
        }
        setIds(stack, ids);
    }

    /**
     * The entities of the baton's list: on the server found by UUID in the level, on the client among the loaded entities around the player
     * (the NPCs send their UUID to the client with the spawn packet). 1.12.2 had {@code getCommandedEntities(World, ItemStack)}; the client side
     * needs the player to know where to look.
     */
    public static List<Entity> getCommandedEntities(Player player, ItemStack stack) {
        if (stack.isEmpty() || !(stack.getItem() instanceof ItemCommandBaton)) {
            return new ArrayList<>();
        }
        Set<UUID> ids = getIds(stack);
        List<Entity> found = new ArrayList<>();
        if (ids.isEmpty()) {
            return found;
        }
        Level level = player.level();
        if (level instanceof ServerLevel serverLevel) {
            for (UUID id : ids) {
                Entity entity = serverLevel.getEntity(id);
                if (entity != null) {
                    found.add(entity);
                }
            }
        } else {
            found.addAll(level.getEntitiesOfClass(NpcBase.class, player.getBoundingBox().inflate(CLIENT_SEARCH_RANGE), npc -> ids.contains(npc.getUUID())));
        }
        return found;
    }

    /**
     * The entities of the baton in the player's main hand, or, if that is not a baton, in the off hand (1.12.2:
     * {@code EntityTools.getItemFromEitherHand}). Installed as {@code NpcCommand.CommandedEntitiesProvider}.
     */
    public static List<Entity> getCommandedEntities(Player player) {
        ItemStack held = player.getMainHandItem().getItem() instanceof ItemCommandBaton ? player.getMainHandItem() : player.getOffhandItem();
        return getCommandedEntities(player, held);
    }

    /** What the baton does with the key action. */
    public enum BatonMode {
        CLEAR_COMMAND("clear", "guistrings.npc.baton.clear"),
        ATTACK("attack", "guistrings.npc.baton.attack"),
        MOVE("move", "guistrings.npc.baton.move"),
        SET_HOME("setHome", "guistrings.npc.baton.set_home"),
        CLEAR_HOME("clearHome", "guistrings.npc.baton.clear_home"),
        SET_UPKEEP("setUpkeep", "guistrings.npc.baton.set_upkeep"),
        CLEAR_UPKEEP("clearUpkeep", "guistrings.npc.baton.clear_upkeep");

        private static final Map<String, BatonMode> NAME_TO_MODE = new HashMap<>();

        static {
            for (BatonMode mode : values()) {
                NAME_TO_MODE.put(mode.name, mode);
            }
        }

        /** The mode as its 1.12.2 name ({@code clear}, {@code attack}, {@code setHome}, ...); an unknown name reads as the default. */
        public static final Codec<BatonMode> CODEC = Codec.STRING.xmap(BatonMode::fromName, BatonMode::getName);
        public static final StreamCodec<ByteBuf, BatonMode> STREAM_CODEC = ByteBufCodecs.STRING_UTF8.map(BatonMode::fromName, BatonMode::getName);

        private final String name;
        private final String key;

        BatonMode(String name, String key) {
            this.name = name;
            this.key = key;
        }

        public String getTranslationKey() {
            return key;
        }

        public static BatonMode getDefault() {
            return CLEAR_COMMAND;
        }

        public BatonMode next() {
            return values()[(ordinal() + 1) % values().length];
        }

        public BatonMode previous() {
            return values()[(ordinal() + values().length - 1) % values().length];
        }

        public static BatonMode fromName(@Nullable String name) {
            return name == null ? CLEAR_COMMAND : NAME_TO_MODE.getOrDefault(name, CLEAR_COMMAND);
        }

        public String getName() {
            return name;
        }
    }
}
