/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/init/AWNPCItems, AWNPCBlocks, AWNPCEntities (and the declarations in it) and AWNPCTab, the registration
 * done in npc/AncientWarfareNPC (parsers, packets, commands, events) and npc/crafting/AWNpcCrafting. The sounds are in {@link AwNpcSounds}.
 */
package io.github.capttracker.ancientwarfarenpc.init;

import io.github.capttracker.ancientwarfare.AncientWarfareCore;
import io.github.capttracker.ancientwarfare.compat.CompatLoader;
import io.github.capttracker.ancientwarfare.item.OwnedBlockItem;
import io.github.capttracker.ancientwarfare.registry.RegistryLoader;
import io.github.capttracker.ancientwarfarenpc.AncientWarfareNPC;
import io.github.capttracker.ancientwarfarenpc.block.BlockTownHall;
import io.github.capttracker.ancientwarfarenpc.block.entity.TownHallBlockEntity;
import io.github.capttracker.ancientwarfarenpc.command.CommandDebugAI;
import io.github.capttracker.ancientwarfarenpc.compat.EpicSiegeCompat;
import io.github.capttracker.ancientwarfarenpc.compat.TwilightForestCompat;
import io.github.capttracker.ancientwarfarenpc.config.AWNPCStatics;
import io.github.capttracker.ancientwarfarenpc.crafting.OrderCopyingRecipe;
import io.github.capttracker.ancientwarfarenpc.entity.NoFriendlyFirePotion;
import io.github.capttracker.ancientwarfarenpc.entity.NpcEntityPayloads;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBard;
import io.github.capttracker.ancientwarfarenpc.entity.NpcGuardCaptain;
import io.github.capttracker.ancientwarfarenpc.entity.NpcInnkeeper;
import io.github.capttracker.ancientwarfarenpc.entity.NpcStableHand;
import io.github.capttracker.ancientwarfarenpc.entity.NpcBase;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCombat;
import io.github.capttracker.ancientwarfarenpc.entity.NpcCourier;
import io.github.capttracker.ancientwarfarenpc.entity.NpcPriest;
import io.github.capttracker.ancientwarfarenpc.entity.vehicle.NpcSiegeEngineer;
import io.github.capttracker.ancientwarfarenpc.entity.NpcTrader;
import io.github.capttracker.ancientwarfarenpc.entity.NpcWorker;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFaction;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionArcher;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionArcherElite;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionBard;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionCivilianFemale;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionCivilianMale;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionLeader;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionLeaderElite;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionMountedArcher;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionMountedSoldier;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionPriest;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionSiegeEngineer;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionSoldier;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionSoldierElite;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionSpellcaster;
import io.github.capttracker.ancientwarfarenpc.entity.faction.NpcFactionTrader;
import io.github.capttracker.ancientwarfarenpc.event.NpcEventHandler;
import io.github.capttracker.ancientwarfarenpc.faction.AwFactions;
import io.github.capttracker.ancientwarfarenpc.item.ItemBardInstrument;
import io.github.capttracker.ancientwarfarenpc.item.ItemClub;
import io.github.capttracker.ancientwarfarenpc.item.ItemCoin;
import io.github.capttracker.ancientwarfarenpc.item.ItemCommandBaton;
import io.github.capttracker.ancientwarfarenpc.item.ItemExtendedReachWeapon;
import io.github.capttracker.ancientwarfarenpc.item.ItemFoodBundle;
import io.github.capttracker.ancientwarfarenpc.item.ItemIceSpear;
import io.github.capttracker.ancientwarfarenpc.item.ItemMacuahuitl;
import io.github.capttracker.ancientwarfarenpc.item.ItemNpcSpawner;
import io.github.capttracker.ancientwarfarenpc.item.ItemPitchfork;
import io.github.capttracker.ancientwarfarenpc.item.ItemScythe;
import io.github.capttracker.ancientwarfarenpc.item.ItemShield;
import io.github.capttracker.ancientwarfarenpc.item.ItemSickle;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemCombatOrder;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemRoutingOrder;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemSeaRoute;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemTradeOrder;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemUpkeepOrder;
import io.github.capttracker.ancientwarfarenpc.item.order.ItemWorkOrder;
import io.github.capttracker.ancientwarfarenpc.network.NpcNetwork;
import io.github.capttracker.ancientwarfarenpc.registry.FactionRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.FactionTradeListRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.NpcDefaultsRegistry;
import io.github.capttracker.ancientwarfarenpc.registry.RidesVehicleCondition;
import io.github.capttracker.ancientwarfarenpc.registry.TargetRegistry;
import io.github.capttracker.ancientwarfarenpc.skin.NpcSkinManager;
import io.github.capttracker.lib.registry.ModRegistrar;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.Tiers;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.storage.loot.predicates.LootItemConditionType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * The master list of everything the NPC module registers, in the order of 1.12.2: the town hall block, item and block entity, the 64 items,
 * the 23 entity types (7 player-owned, 15 faction, and the potion of the faction priests) with their {@link NpcDeclaration}s, the order copy recipe
 * serializer, the creative tab and, in {@link AwNpcSounds}, the 119 sounds. Everything goes through the module's {@link ModRegistrar}
 * ({@link AncientWarfareNPC#REG}) except the entity types and the recipe serializer, which need registers the library does not have yet.
 *
 * <p>Items (1.12.2: 58 items and the town hall item block; now 64 items because the metadata variants of the bard instrument and the coin are
 * separate items):
 * <ul>
 *   <li>5 command batons ({@code <wooden|stone|iron|gold|diamond>_command_baton}), 4 bard instruments ({@code bard_instrument_<lute|flute|harp|drum>},
 *       1.12.2 damage values 0 to 3), 16 shields (5 tiers and 11 cosmetic ones)</li>
 *   <li>5 order slips (work, upkeep, combat, routing, trade), the NPC spawner, 4 coins ({@code coin_<ancient|gold|silver|copper>}, 1.12.2 NBT {@code metal})</li>
 *   <li>7 weapons (macuahuitl, sickle, pitchfork, scythe, death scythe, giant club, ice spear), the food bundle</li>
 *   <li>21 extended reach weapons ({@code <wooden|stone|iron|golden|diamond>_<spear|halberd|lance|cleaver>} and the obsidian spear)</li>
 * </ul>
 *
 * <p>The item classes are written by the item slices; each is built here with a plain {@code Item.Properties} (durability, stack size or food where
 * the original set them) and these extra constructor arguments, which that slice must provide:
 * <pre>
 * ItemCommandBaton(Properties, Tier)            ItemBardInstrument(Properties, String instrument: lute|flute|harp|drum)
 * ItemShield(Properties, Tier)                  ItemCoin(Properties, String metal: ancient|gold|silver|copper)
 * ItemWorkOrder/UpkeepOrder/CombatOrder/RoutingOrder/TradeOrder(Properties)   ItemNpcSpawner(Properties)   ItemFoodBundle(Properties)
 * ItemMacuahuitl(Properties, Tier)              ItemSickle(Properties, Tier, float attackSpeed)   ItemPitchfork(Properties, Tier, float attackSpeed)
 * ItemScythe(Properties, Tier, float attackOffset, float attackSpeed, Supplier&lt;MobEffectInstance&gt; hitEffect or null)
 * ItemClub / ItemIceSpear / ItemExtendedReachWeapon(Properties, Tier, double attackOffset, double attackSpeed, float reach)
 * </pre>
 * The entity classes need a constructor {@code (EntityType<? extends X>, Level)}; faction NPCs get their faction from
 * {@code setFactionNameAndDefaults(String)} after creation (see {@link NpcDeclaration#createEntity}).
 *
 * <p>Wiring (mod constructor, before {@code REG.register(modBus)}): {@code AwNpcs.load(); AwNpcs.registerEvents(modBus, container);}.
 */
public final class AwNpcs {
    public static final String MOD_ID = AncientWarfareNPC.MOD_ID;
    private static final ModRegistrar REG = AncientWarfareNPC.REG;

    private AwNpcs() {}

    /** Forces this class to initialise, which registers everything through the registrars. */
    public static void load() {
        AwNpcSounds.load();
    }

    private static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(Registries.ENTITY_TYPE, MOD_ID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, MOD_ID);
    private static final DeferredRegister<LootItemConditionType> LOOT_CONDITIONS = DeferredRegister.create(Registries.LOOT_CONDITION_TYPE, MOD_ID);

    /** The loot condition {@code ancientwarfarenpc:rides_vehicle} of the faction siege engineer loot table (1.12.2: an entity property). */
    public static final DeferredHolder<LootItemConditionType, LootItemConditionType> RIDES_VEHICLE_CONDITION =
            LOOT_CONDITIONS.register("rides_vehicle", () -> new LootItemConditionType(RidesVehicleCondition.CODEC));

    // ---------------------------------------------------------------- block, item block, block entity

    /** 1.12.2: Material.ROCK, hardness 2, not opaque. Needs a pickaxe (tag {@code minecraft:mineable/pickaxe}). */
    public static final DeferredBlock<BlockTownHall> TOWN_HALL = REG.blockWithoutItem("town_hall", BlockTownHall::new,
            BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion());

    /** 1.12.2: {@code ItemBlockOwned}; makes the placer the owner. */
    public static final DeferredItem<OwnedBlockItem> TOWN_HALL_ITEM = REG.item("town_hall", p -> new OwnedBlockItem(TOWN_HALL.get(), p));

    /** 1.12.2 id: {@code town_hall_tile}. */
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<TownHallBlockEntity>> TOWN_HALL_BE =
            REG.blockEntity("town_hall", TownHallBlockEntity::new, TOWN_HALL);

    // ---------------------------------------------------------------- items (1.12.2: AWNPCItems.register, same order)

    private static final Map<String, Tier> TIERS = new LinkedHashMap<>();

    static {
        TIERS.put("wooden", Tiers.WOOD);
        TIERS.put("stone", Tiers.STONE);
        TIERS.put("iron", Tiers.IRON);
        TIERS.put("gold", Tiers.GOLD);
        TIERS.put("diamond", Tiers.DIAMOND);
    }

    /** The five command batons by registry name. */
    public static final Map<String, DeferredItem<ItemCommandBaton>> COMMAND_BATONS = new LinkedHashMap<>();
    /** The four bard instruments by registry name ({@code bard_instrument_lute} and so on). */
    public static final Map<String, DeferredItem<ItemBardInstrument>> BARD_INSTRUMENTS = new LinkedHashMap<>();
    /** The 16 shields by registry name. */
    public static final Map<String, DeferredItem<ItemShield>> SHIELDS = new LinkedHashMap<>();
    /** The four coins by registry name ({@code coin_gold} and so on). */
    public static final Map<String, DeferredItem<ItemCoin>> COINS = new LinkedHashMap<>();
    /** The 21 extended reach weapons by registry name. */
    public static final Map<String, DeferredItem<ItemExtendedReachWeapon>> EXTENDED_REACH_WEAPONS = new LinkedHashMap<>();

    public static final DeferredItem<ItemCommandBaton> IRON_COMMAND_BATON;
    public static final DeferredItem<ItemShield> WOODEN_SHIELD;
    public static final DeferredItem<ItemWorkOrder> WORK_ORDER;
    public static final DeferredItem<ItemUpkeepOrder> UPKEEP_ORDER;
    public static final DeferredItem<ItemCombatOrder> COMBAT_ORDER;
    public static final DeferredItem<ItemRoutingOrder> ROUTING_ORDER;
    public static final DeferredItem<ItemTradeOrder> TRADE_ORDER;
    public static final DeferredItem<ItemSeaRoute> SEA_ROUTE;
    /** Stack size 16. The creative tab lists one stack per NPC type, subtype and faction (added by the item slice). */
    public static final DeferredItem<ItemNpcSpawner> NPC_SPAWNER;
    public static final DeferredItem<ItemMacuahuitl> MACUAHUITL;
    public static final DeferredItem<ItemSickle> SICKLE;
    public static final DeferredItem<ItemPitchfork> PITCHFORK;
    public static final DeferredItem<ItemScythe> SCYTHE;
    public static final DeferredItem<ItemScythe> DEATH_SCYTHE;
    public static final DeferredItem<ItemClub> GIANT_CLUB;
    public static final DeferredItem<ItemIceSpear> ICE_SPEAR;
    /** 15 hunger, 1.0 saturation modifier, wolf food ({@code minecraft:wolf_food} tag), in the tag {@code ancientwarfarenpc:food_bundles} (1.12.2: ore name foodBundle). */
    public static final DeferredItem<ItemFoodBundle> FOOD_BUNDLE;

    static {
        TIERS.forEach((prefix, tier) -> {
            String name = prefix + "_command_baton";
            COMMAND_BATONS.put(name, REG.item(name, p -> new ItemCommandBaton(p, tier), toolProps(tier.getUses())));
        });
        IRON_COMMAND_BATON = COMMAND_BATONS.get("iron_command_baton");

        for (String instrument : new String[]{"lute", "flute", "harp", "drum"}) {
            String name = "bard_instrument_" + instrument;
            BARD_INSTRUMENTS.put(name, REG.item(name, p -> new ItemBardInstrument(p, instrument), new Item.Properties().stacksTo(1)));
        }

        // name, tier, durability (1.12.2: the second constructor argument)
        addShield("wooden_shield", Tiers.WOOD, 336);
        addShield("stone_shield", Tiers.STONE, 506);
        addShield("iron_shield", Tiers.IRON, 759);
        addShield("gold_shield", Tiers.GOLD, 64);
        addShield("diamond_shield", Tiers.DIAMOND, 1138);
        addShield("shield_tribal_1", Tiers.WOOD, 336);
        addShield("shield_tribal_2", Tiers.WOOD, 336);
        for (int i = 1; i <= 6; i++) {
            addShield("shield_round_" + i, Tiers.WOOD, 336);
        }
        addShield("shield_witchbane_1", Tiers.IRON, 759);
        addShield("shield_witchbane_2", Tiers.IRON, 759);
        addShield("shield_buffloka", Tiers.WOOD, 336);
        WOODEN_SHIELD = SHIELDS.get("wooden_shield");

        WORK_ORDER = REG.item("work_order", ItemWorkOrder::new);
        UPKEEP_ORDER = REG.item("upkeep_order", ItemUpkeepOrder::new);
        COMBAT_ORDER = REG.item("combat_order", ItemCombatOrder::new);
        ROUTING_ORDER = REG.item("routing_order", ItemRoutingOrder::new);
        TRADE_ORDER = REG.item("trade_order", ItemTradeOrder::new);
        SEA_ROUTE = REG.item("sea_route", ItemSeaRoute::new);
        NPC_SPAWNER = REG.item("npc_spawner", ItemNpcSpawner::new, new Item.Properties().stacksTo(16));

        for (String metal : new String[]{"ancient", "gold", "silver", "copper"}) {
            String name = "coin_" + metal;
            COINS.put(name, REG.item(name, p -> new ItemCoin(p, metal)));
        }

        MACUAHUITL = REG.item("macuahuitl", p -> new ItemMacuahuitl(p, Tiers.IRON), toolProps(Tiers.IRON.getUses() - 100));
        SICKLE = REG.item("sickle", p -> new ItemSickle(p, Tiers.IRON, -2.3F), toolProps(Tiers.IRON.getUses()));
        PITCHFORK = REG.item("pitchfork", p -> new ItemPitchfork(p, Tiers.IRON, -2.3F), toolProps(Tiers.IRON.getUses()));
        SCYTHE = REG.item("scythe", p -> new ItemScythe(p, Tiers.IRON, 0.0F, -2.3F, null), toolProps(Tiers.IRON.getUses()));
        // the death scythe withers what it hits (1.12.2: an anonymous subclass overriding applyPotionEffect)
        DEATH_SCYTHE = REG.item("death_scythe",
                p -> new ItemScythe(p, Tiers.DIAMOND, 0.0F, -2.3F, () -> new MobEffectInstance(MobEffects.WITHER, 100)), toolProps(Tiers.DIAMOND.getUses()));
        GIANT_CLUB = REG.item("giant_club", p -> new ItemClub(p, Tiers.DIAMOND, 3.5, -3.6D, 4.2F), toolProps(Tiers.DIAMOND.getUses()));
        ICE_SPEAR = REG.item("ice_spear", p -> new ItemIceSpear(p, Tiers.DIAMOND, 2, -3, 4.2F), toolProps(Tiers.DIAMOND.getUses()));

        FOOD_BUNDLE = REG.item("food_bundle", ItemFoodBundle::new,
                new Item.Properties().food(new FoodProperties.Builder().nutrition(15).saturationModifier(1.0F).build()));

        addExtendedReachWeapons("spear", 2, -3, 4.2F);
        addExtendedReachWeapons("halberd", 3, -3.2D, 4.5F);
        addExtendedReachWeapons("lance", 2.5D, -3.2D, 5.5F);
        addExtendedReachWeapons("cleaver", 3.5D, -2.8D, 3.0F);
        addExtendedReachWeapon("obsidian_spear", Tiers.DIAMOND, 2, -3, 4.2F);
    }

    private static Item.Properties toolProps(int durability) {
        return new Item.Properties().durability(durability);
    }

    private static void addShield(String name, Tier tier, int durability) {
        SHIELDS.put(name, REG.item(name, p -> new ItemShield(p, tier), toolProps(durability)));
    }

    private static void addExtendedReachWeapons(String name, double attackOffset, double attackSpeed, float reach) {
        // 1.12.2 spelled the gold prefix "golden_" for these (and "gold_" for batons and shields)
        addExtendedReachWeapon("wooden_" + name, Tiers.WOOD, attackOffset, attackSpeed, reach);
        addExtendedReachWeapon("stone_" + name, Tiers.STONE, attackOffset, attackSpeed, reach);
        addExtendedReachWeapon("iron_" + name, Tiers.IRON, attackOffset, attackSpeed, reach);
        addExtendedReachWeapon("golden_" + name, Tiers.GOLD, attackOffset, attackSpeed, reach);
        addExtendedReachWeapon("diamond_" + name, Tiers.DIAMOND, attackOffset, attackSpeed, reach);
    }

    private static void addExtendedReachWeapon(String name, Tier tier, double attackOffset, double attackSpeed, float reach) {
        EXTENDED_REACH_WEAPONS.put(name, REG.item(name, p -> new ItemExtendedReachWeapon(p, tier, attackOffset, attackSpeed, reach), toolProps(tier.getUses())));
    }

    // ---------------------------------------------------------------- recipe serializer

    /** Type {@code ancientwarfarenpc:order_copy}, used by the four order copy recipes (data files). */
    public static final DeferredHolder<RecipeSerializer<?>, RecipeSerializer<OrderCopyingRecipe>> ORDER_COPY_SERIALIZER =
            RECIPE_SERIALIZERS.register("order_copy", OrderCopyingRecipe.Serializer::new);

    // ---------------------------------------------------------------- entity types (1.12.2: AWNPCEntities; tracking range 120 blocks, update every 3 ticks, velocity updates)

    /** Every NPC declaration by NPC type: {@code combat}, {@code worker}, ... and {@code faction.archer}, {@code faction.leader.elite}, ... */
    private static final Map<String, NpcDeclaration> NPC_MAP = new LinkedHashMap<>();

    // player-owned (registry names are the 1.12.2 AWEntityRegistry constants)
    public static final DeferredHolder<EntityType<?>, EntityType<NpcCombat>> NPC_COMBAT = npc("aw_npc_combat", NpcCombat::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcWorker>> NPC_WORKER = npc("aw_npc_worker", NpcWorker::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcCourier>> NPC_COURIER = npc("aw_npc_courier", NpcCourier::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcTrader>> NPC_TRADER = npc("aw_npc_trader", NpcTrader::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcPriest>> NPC_PRIEST = npc("aw_npc_priest", NpcPriest::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcBard>> NPC_BARD = npc("aw_npc_bard", NpcBard::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcSiegeEngineer>> NPC_SIEGE_ENGINEER = npc("aw_npc_siege_engineer", NpcSiegeEngineer::new);
    // rework phase 5: the new NPC types
    public static final DeferredHolder<EntityType<?>, EntityType<NpcInnkeeper>> NPC_INNKEEPER = npc("aw_npc_innkeeper", NpcInnkeeper::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcStableHand>> NPC_STABLE_HAND = npc("aw_npc_stable_hand", NpcStableHand::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcGuardCaptain>> NPC_GUARD_CAPTAIN = npc("aw_npc_guard_captain", NpcGuardCaptain::new);

    // faction
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionArcher>> NPC_FACTION_ARCHER = npc("faction.archer", NpcFactionArcher::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionSoldier>> NPC_FACTION_SOLDIER = npc("faction.soldier", NpcFactionSoldier::new);
    /** 1.12.2 constant NPC_FACTION_COMMANDER; its NPC type and subtype are "leader". */
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionLeader>> NPC_FACTION_LEADER = npc("faction.leader", NpcFactionLeader::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionPriest>> NPC_FACTION_PRIEST = npc("faction.priest", NpcFactionPriest::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionTrader>> NPC_FACTION_TRADER = npc("faction.trader", NpcFactionTrader::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionMountedSoldier>> NPC_FACTION_CAVALRY = npc("faction.cavalry", NpcFactionMountedSoldier::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionMountedArcher>> NPC_FACTION_MOUNTED_ARCHER =
            npc("faction.mounted_archer", NpcFactionMountedArcher::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionCivilianMale>> NPC_FACTION_CIVILIAN_MALE = npc("faction.civilian.male", NpcFactionCivilianMale::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionCivilianFemale>> NPC_FACTION_CIVILIAN_FEMALE =
            npc("faction.civilian.female", NpcFactionCivilianFemale::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionArcherElite>> NPC_FACTION_ARCHER_ELITE = npc("faction.archer.elite", NpcFactionArcherElite::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionSoldierElite>> NPC_FACTION_SOLDIER_ELITE = npc("faction.soldier.elite", NpcFactionSoldierElite::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionLeaderElite>> NPC_FACTION_LEADER_ELITE = npc("faction.leader.elite", NpcFactionLeaderElite::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionBard>> NPC_FACTION_BARD = npc("faction.bard", NpcFactionBard::new);
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionSiegeEngineer>> NPC_FACTION_SIEGE_ENGINEER = npc("faction.siege_engineer", NpcFactionSiegeEngineer::new);
    /** The plain spell caster; the Electroblob's Wizardry variant (same registry name in 1.12.2) is not ported. */
    public static final DeferredHolder<EntityType<?>, EntityType<NpcFactionSpellcaster>> NPC_FACTION_SPELLCASTER = npc("faction.spellcaster", NpcFactionSpellcaster::new);

    /**
     * The potion the faction priests throw. 1.12.2 never registered it (vanilla tracked any {@code EntityPotion} subclass), 1.21 needs a real
     * entity type: same size and tracking as the vanilla thrown potion.
     */
    public static final DeferredHolder<EntityType<?>, EntityType<NoFriendlyFirePotion>> NO_FRIENDLY_FIRE_POTION = ENTITY_TYPES.register("no_friendly_fire_potion",
            () -> EntityType.Builder.<NoFriendlyFirePotion>of(NoFriendlyFirePotion::new, MobCategory.MISC)
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .updateInterval(10)
                    .build(MOD_ID + ":no_friendly_fire_potion"));

    private static <T extends NpcBase> DeferredHolder<EntityType<?>, EntityType<T>> npc(String registryName, EntityType.EntityFactory<T> factory) {
        // 120 blocks tracking range is 8 chunks (the builder takes chunks)
        return ENTITY_TYPES.register(registryName, () -> EntityType.Builder.of(factory, MobCategory.MISC)
                .sized(0.6F, 1.8F)
                .clientTrackingRange(8)
                .updateInterval(3)
                .setShouldReceiveVelocityUpdates(true)
                .build(MOD_ID + ":" + registryName));
    }

    static {
        // the three data from 1.12.2 addPlayerOwnedNpcs: NPC type, item model variant, spawner subtypes
        NpcDeclaration combat = declare("combat", "aw_npc_combat", NPC_COMBAT, "soldier");
        combat.addSubTypes("commander", "soldier", "archer", "medic", "engineer");
        NpcDeclaration worker = declare("worker", "aw_npc_worker", NPC_WORKER, "miner");
        worker.addSubTypes("miner", "farmer", "lumberjack", "researcher", "craftsman", "smith", "tanner", "builder");
        declare("courier", "aw_npc_courier", NPC_COURIER, "courier");
        declare("trader", "aw_npc_trader", NPC_TRADER, "trader");
        declare("priest", "aw_npc_priest", NPC_PRIEST, "priest");
        declare("bard", "aw_npc_bard", NPC_BARD, "bard");
        declare("siege_engineer", "aw_npc_siege_engineer", NPC_SIEGE_ENGINEER, "siege_engineer");
        declare("innkeeper", "aw_npc_innkeeper", NPC_INNKEEPER, "innkeeper");
        declare("stable_hand", "aw_npc_stable_hand", NPC_STABLE_HAND, "stable_hand");
        declare("guard_captain", "aw_npc_guard_captain", NPC_GUARD_CAPTAIN, "guard_captain");

        declareFaction("faction.archer", NPC_FACTION_ARCHER, "archer");
        declareFaction("faction.soldier", NPC_FACTION_SOLDIER, "soldier");
        declareFaction("faction.leader", NPC_FACTION_LEADER, "commander");
        declareFaction("faction.priest", NPC_FACTION_PRIEST, "priest");
        declareFaction("faction.trader", NPC_FACTION_TRADER, "trader");
        declareFaction("faction.cavalry", NPC_FACTION_CAVALRY, "soldier");
        declareFaction("faction.mounted_archer", NPC_FACTION_MOUNTED_ARCHER, "archer");
        declareFaction("faction.civilian.male", NPC_FACTION_CIVILIAN_MALE, "civilian_male");
        declareFaction("faction.civilian.female", NPC_FACTION_CIVILIAN_FEMALE, "civilian_female");
        declareFaction("faction.archer.elite", NPC_FACTION_ARCHER_ELITE, "archer");
        declareFaction("faction.soldier.elite", NPC_FACTION_SOLDIER_ELITE, "soldier");
        declareFaction("faction.leader.elite", NPC_FACTION_LEADER_ELITE, "commander");
        declareFaction("faction.bard", NPC_FACTION_BARD, "bard");
        declareFaction("faction.siege_engineer", NPC_FACTION_SIEGE_ENGINEER, "siege_engineer");
        declareFaction("faction.spellcaster", NPC_FACTION_SPELLCASTER, "spellcaster");
    }

    private static NpcDeclaration declare(String npcType, String registryName, Supplier<? extends EntityType<? extends NpcBase>> type, String itemModelVariant) {
        NpcDeclaration declaration = new NpcDeclaration(npcType, registryName, type, itemModelVariant, false);
        NPC_MAP.put(npcType, declaration);
        return declaration;
    }

    private static void declareFaction(String registryName, Supplier<? extends EntityType<? extends NpcBase>> type, String itemModelVariant) {
        // the NPC type of a faction NPC is its registry name
        NPC_MAP.put(registryName, new NpcDeclaration(registryName, registryName, type, itemModelVariant, true));
    }

    /**
     * Must run after the items are registered (1.12.2: post-init): the main-hand item an NPC spawned for a subtype holds. Done at common setup by
     * {@link #registerEvents}.
     */
    public static void loadNpcSubtypeEquipment() {
        addNpcSubtypeEquipment("worker", "farmer", new ItemStack(Items.IRON_HOE));
        addNpcSubtypeEquipment("worker", "miner", new ItemStack(Items.IRON_PICKAXE));
        addNpcSubtypeEquipment("worker", "lumberjack", new ItemStack(Items.IRON_AXE));
        addNpcSubtypeEquipment("worker", "researcher", new ItemStack(AncientWarfareCore.QUILLS.get("iron_quill").get()));
        addNpcSubtypeEquipment("worker", "craftsman", new ItemStack(AncientWarfareCore.HAMMERS.get("iron_hammer").get()));
        addNpcSubtypeEquipment("worker", "smith", new ItemStack(AncientWarfareCore.HAMMERS.get("iron_hammer").get()));
        addNpcSubtypeEquipment("worker", "tanner", new ItemStack(Items.SHEARS));
        addNpcSubtypeEquipment("worker", "builder", new ItemStack(AncientWarfareCore.HAMMERS.get("iron_hammer").get())); // rework phase 9

        addNpcSubtypeEquipment("combat", "commander", new ItemStack(IRON_COMMAND_BATON.get()));
        addNpcSubtypeEquipment("combat", "soldier", new ItemStack(Items.IRON_SWORD));
        addNpcSubtypeEquipment("combat", "archer", new ItemStack(Items.BOW));
        addNpcSubtypeEquipment("combat", "engineer", new ItemStack(AncientWarfareCore.HAMMERS.get("iron_hammer").get()));
        addNpcSubtypeEquipment("combat", "medic", new ItemStack(Items.IRON_AXE));
    }

    private static void addNpcSubtypeEquipment(String npcType, String npcSubtype, ItemStack equipment) {
        NpcDeclaration declaration = NPC_MAP.get(npcType);
        if (declaration == null) {
            throw new IllegalArgumentException("npc type must first be mapped");
        }
        declaration.addSubtypeEquipment(npcSubtype, equipment);
    }

    /** Creates an NPC of the type (not yet in the level) or null for an unknown type. {@code faction} is only used for faction NPCs. */
    @Nullable
    public static NpcBase createNpc(Level level, String npcType, String npcSubtype, String faction) {
        NpcDeclaration declaration = NPC_MAP.get(npcType);
        return declaration == null ? null : declaration.createEntity(level, npcSubtype, faction);
    }

    /** Every declaration by NPC type, in registration order (1.12.2: {@code getNpcMap()}). */
    public static Map<String, NpcDeclaration> getNpcMap() {
        return Collections.unmodifiableMap(NPC_MAP);
    }

    @Nullable
    public static NpcDeclaration getNpcDeclaration(String npcType) {
        return NPC_MAP.get(npcType);
    }

    /**
     * What a spawner or a structure needs to know about one NPC entity type: its registry name, its NPC type key (the key of {@link #getNpcMap()};
     * the short subtype name of a faction NPC is the key without {@code faction.}), the subtypes with their main-hand equipment (player-owned
     * NPCs only) and the item model variant of its spawner icon.
     */
    public static final class NpcDeclaration {
        private final String npcType;
        private final String registryName;
        private final Supplier<? extends EntityType<? extends NpcBase>> entityType;
        private final String itemModelVariant;
        private final boolean faction;
        private final Map<String, ItemStack> spawnEquipment = new LinkedHashMap<>();

        private NpcDeclaration(String npcType, String registryName, Supplier<? extends EntityType<? extends NpcBase>> entityType, String itemModelVariant,
                               boolean faction) {
            this.npcType = npcType;
            this.registryName = registryName;
            this.entityType = entityType;
            this.itemModelVariant = itemModelVariant;
            this.faction = faction;
        }

        private void addSubTypes(String... subTypes) {
            for (String subType : subTypes) {
                addSubtypeEquipment(subType, ItemStack.EMPTY);
            }
        }

        private void addSubtypeEquipment(String type, ItemStack equipment) {
            spawnEquipment.put(type, equipment);
        }

        public boolean isFactionNpc() {
            return faction;
        }

        /** The registry path of the entity type ({@code aw_npc_combat}, {@code faction.archer}). */
        public String getRegistryName() {
            return registryName;
        }

        public EntityType<? extends NpcBase> getEntityType() {
            return entityType.get();
        }

        public String getFaction() {
            return "";
        }

        /**
         * Creates the entity. Player-owned NPCs get the main-hand item of the subtype; a faction NPC gets its faction (and so its defaults) from
         * {@code NpcFaction#setFactionNameAndDefaults}. Returns null if the entity could not be created.
         */
        @Nullable
        public NpcBase createEntity(Level level, String subType, String factionName) {
            NpcBase npc = entityType.get().create(level);
            if (npc == null) {
                return null;
            }
            if (faction) {
                if (npc instanceof NpcFaction factionNpc) {
                    factionNpc.setFactionNameAndDefaults(factionName);
                }
                return npc;
            }
            if (npc instanceof NpcGuardCaptain && npc.getMainHandItem().isEmpty()) {
                npc.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
            }
            if (!subType.isEmpty()) {
                if (npc instanceof NpcWorker worker) {
                    worker.setRole(subType);
                }
                ItemStack stack = spawnEquipment.get(subType);
                if (stack != null && !stack.isEmpty()) {
                    npc.setItemSlot(EquipmentSlot.MAINHAND, stack.copy());
                }
            }
            return npc;
        }

        /** The subtype names then the default variant (1.12.2: the models the spawner item needs). */
        public List<String> getItemModelVariants() {
            List<String> ret = new ArrayList<>(spawnEquipment.keySet());
            ret.add(itemModelVariant);
            return ret;
        }

        public String getItemModelVariant(String npcSubType) {
            return npcSubType.isEmpty() ? itemModelVariant : npcSubType;
        }

        public Set<String> getSubTypes() {
            return Collections.unmodifiableSet(spawnEquipment.keySet());
        }

        /** Always true in 1.12.2 (the field was never changed). */
        public boolean canSpawnBaseEntity() {
            return true;
        }

        public String getNpcType() {
            return npcType;
        }
    }

    // ---------------------------------------------------------------- spawner icon variants

    /**
     * The 18 icon variants of the NPC spawner (the 1.12.2 blockstate variants of {@code npc_spawner}, the values of
     * {@link NpcDeclaration#getItemModelVariants()}). The item model {@code models/item/npc_spawner.json} switches on the
     * {@code custom_model_data} component: value {@code i + 1} of this list shows {@code models/item/npc_spawner_<variant>.json}, and no component shows the plain
     * base icon. The spawner item sets {@code DataComponents.CUSTOM_MODEL_DATA = new CustomModelData(getSpawnerModelIndex(variant))}.
     */
    public static final List<String> SPAWNER_MODEL_VARIANTS = List.of("siege_engineer", "archer", "bard", "commander", "courier", "craftsman", "engineer",
            "farmer", "lumberjack", "medic", "miner", "priest", "researcher", "trader", "soldier", "civilian_female", "civilian_male", "spellcaster",
            "innkeeper", "stable_hand", "guard_captain");

    /** The custom model data value of a spawner icon variant, or 0 (the base icon) for an unknown one. */
    public static int getSpawnerModelIndex(String variant) {
        return SPAWNER_MODEL_VARIANTS.indexOf(variant) + 1;
    }

    // ---------------------------------------------------------------- creative tab (1.12.2: AWNPCTab)

    /**
     * Title {@code itemGroup.ancientwarfarenpc}, icon the NPC spawner. The town hall, the order slips, the batons, the bard instruments and the
     * spawner come first (1.12.2: {@code SortItemsFirstComparator}), then everything else in registration order. The spawner stacks of every NPC
     * type, subtype and faction are added by the item slice with a {@code BuildCreativeModeTabContentsEvent} listener on this tab's key.
     */
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = REG.creativeTabs.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MOD_ID))
            .icon(() -> new ItemStack(NPC_SPAWNER.get()))
            .displayItems((parameters, output) -> {
                List<Supplier<? extends Item>> first = new ArrayList<>();
                first.add(TOWN_HALL_ITEM);
                first.add(WORK_ORDER);
                first.add(UPKEEP_ORDER);
                first.add(COMBAT_ORDER);
                first.add(ROUTING_ORDER);
                first.add(TRADE_ORDER);
                first.add(SEA_ROUTE);
                first.addAll(COMMAND_BATONS.values());
                first.addAll(BARD_INSTRUMENTS.values());
                for (Supplier<? extends Item> item : first) {
                    output.accept(item.get());
                }
                for (Supplier<? extends Item> item : REG.tabContents()) {
                    if (!first.contains(item) && item != NPC_SPAWNER) {
                        output.accept(item.get());
                    }
                }
            })
            .build());

    // ---------------------------------------------------------------- events

    /**
     * Registers what {@code REG.register(modBus)} does not cover (entity types, recipe serializer, payloads, attributes, capabilities, common
     * setup) and the config, the data parsers, the game events and the commands. Call from the mod constructor.
     */
    public static void registerEvents(IEventBus modBus, ModContainer container) {
        AWNPCStatics.register(modBus, container);
        registerEvents(modBus);
    }

    /** As {@link #registerEvents(IEventBus, ModContainer)} but without the config. */
    public static void registerEvents(IEventBus modBus) {
        ENTITY_TYPES.register(modBus);
        RECIPE_SERIALIZERS.register(modBus);
        LOOT_CONDITIONS.register(modBus);
        modBus.addListener(AwNpcs::onRegisterPayloads);
        modBus.addListener(AwNpcs::onEntityAttributes);
        modBus.addListener(AwNpcs::onRegisterCapabilities);
        modBus.addListener(AwNpcs::onCommonSetup);

        // data: the five JSON registries (data pack files) and the skin index (resource pack files)
        RegistryLoader.registerParser(new FactionRegistry.FactionParser());
        RegistryLoader.registerParser(new TargetRegistry.TargetListParser());
        RegistryLoader.registerParser(new NpcDefaultsRegistry.FactionNpcDefaultsParser());
        RegistryLoader.registerParser(new NpcDefaultsRegistry.OwnedNpcDefaultsParser());
        RegistryLoader.registerParser(new FactionTradeListRegistry.FactionTradeListParser());
        NpcSkinManager.registerParser();

        // soft dependencies (Electroblob's Wizardry is dropped)
        CompatLoader.registerCompat(new EpicSiegeCompat());
        CompatLoader.registerCompat(new TwilightForestCompat());

        NeoForge.EVENT_BUS.register(NpcEventHandler.INSTANCE);
        NeoForge.EVENT_BUS.register(io.github.capttracker.ancientwarfarenpc.event.ShipPrizeEvents.INSTANCE);
        AwFactions.init();
        NeoForge.EVENT_BUS.addListener(AwNpcs::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent event) -> CommandDebugAI.loadDebugMode(event.getServer()));
    }

    private static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(NpcNetwork.VERSION);
        NpcNetwork.registerPayloads(registrar);
        // the entity data payload of the entity slice
        NpcEntityPayloads.registerPayloads(registrar);
    }

    private static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDebugAI.register(event.getDispatcher());
        io.github.capttracker.ancientwarfarenpc.command.CommandCaravan.register(event.getDispatcher());
        io.github.capttracker.ancientwarfarenpc.command.CommandSettlement.register(event.getDispatcher()); // rework phase 10: test the families
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        // the items exist now (1.12.2: post-init)
        event.enqueueWork(AwNpcs::loadNpcSubtypeEquipment);
        // rework phase 8: the production sites of the automation module count their product in the storages of the settlement
        io.github.capttracker.ancientwarfare.api.WorksiteStock.setCounter(io.github.capttracker.ancientwarfarenpc.settlement.SettlementApi::countItems);
        // ... and make what the settlement is short of first
        io.github.capttracker.ancientwarfare.api.SettlementDemand.setProvider(new io.github.capttracker.ancientwarfarenpc.settlement.SettlementNeeds.Provider());
    }

    /**
     * Attributes of the NPC entity types: the mob defaults plus the two attributes the 1.12.2 base class added to the attribute map. The
     * values are set per NPC from the data defaults when it is created ({@code NpcDefault#applyAttributes}).
     */
    private static void onEntityAttributes(EntityAttributeCreationEvent event) {
        AttributeSupplier attributes = Mob.createMobAttributes()
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.ATTACK_SPEED, 4.0D)
                .build();
        for (NpcDeclaration declaration : NPC_MAP.values()) {
            event.put(declaration.getEntityType(), attributes);
        }
    }

    private static void onRegisterCapabilities(RegisterCapabilitiesEvent event) {
        // the town hall inventory is an item handler on every side
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, TOWN_HALL_BE.get(), (townHall, side) -> townHall.inventory);
    }
}
