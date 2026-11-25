/*
 * Ancient Warfare NPCs - NeoForge 1.21.1 port. GPL-3.0-or-later; see LICENSE.txt in this mod's folder.
 * Ported from Ancient Warfare 2: npc/entity/faction/NpcFactionPriest.
 */
package io.github.capttracker.ancientwarfarenpc.entity.faction;

import io.github.capttracker.ancientwarfarenpc.ai.NpcAIAttackNearest;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDefendFriends;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIDoor;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIFollowPlayer;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIMoveHome;
import io.github.capttracker.ancientwarfarenpc.ai.NpcAIWander;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionHurt;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionPriest;
import io.github.capttracker.ancientwarfarenpc.ai.faction.NpcAIFactionRangedAttack;
import io.github.capttracker.ancientwarfarenpc.entity.NoFriendlyFirePotion;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The priest of a faction: heals the faction around it ({@code NpcAIFactionPriest}) and throws potions at its enemies like a witch does,
 * but with a {@link NoFriendlyFirePotion} that only affects the entities the priest is hostile to. Immune to poison.
 */
public class NpcFactionPriest extends NpcFaction implements RangedAttackMob {
    public NpcFactionPriest(EntityType<? extends NpcFactionPriest> type, Level level) {
        super(type, level);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(0, new FloatGoal(this));
        goalSelector.addGoal(0, new NpcAIDoor(this, true));
        goalSelector.addGoal(1, new NpcAIFollowPlayer(this));
        goalSelector.addGoal(2, new NpcAIMoveHome(this, 50F, 5F, 30F, 5F));
        goalSelector.addGoal(3, new NpcAIFactionPriest(this));
        goalSelector.addGoal(16, new NpcAIFactionRangedAttack(this, 0.8d, 20, 80));
        //post-100 -- used by delayed shared tasks (look at random stuff, wander)
        goalSelector.addGoal(101, new LookAtPlayerGoal(this, Player.class, 3.0F, 1.0F));
        goalSelector.addGoal(102, new NpcAIWander(this));
        goalSelector.addGoal(103, new LookAtPlayerGoal(this, Mob.class, 8.0F));

        targetSelector.addGoal(1, new NpcAIFactionHurt(this, this::isHostileTowards));
        targetSelector.addGoal(15, new NpcAIAttackNearest(this, this::isHostileTowardsNearHome));
        targetSelector.addGoal(3, new NpcAIDefendFriends(this));
    }

    @Override
    public String getNpcType() {
        return "priest";
    }

    @Override
    public void performRangedAttack(LivingEntity target, float distanceFactor) {
        processWitchThrowPotionLogic(target);
    }

    /** Copy of {@code Witch.performRangedAttack} (without the raider and drinking cases) that throws the no friendly fire potion. */
    private void processWitchThrowPotionLogic(LivingEntity target) {
        Vec3 targetMotion = target.getDeltaMovement();
        double d1 = target.getX() + targetMotion.x - getX();
        double d2 = target.getEyeY() - 1.1F - getY();
        double d3 = target.getZ() + targetMotion.z - getZ();
        double distance = Math.sqrt(d1 * d1 + d3 * d3);

        Holder<Potion> potion = Potions.HARMING;
        if (distance >= 8.0F && !target.hasEffect(MobEffects.MOVEMENT_SLOWDOWN)) {
            potion = Potions.SLOWNESS;
        } else if (target.getHealth() >= 8.0F && !target.hasEffect(MobEffects.POISON)) {
            potion = Potions.POISON;
        } else if (distance <= 3.0F && !target.hasEffect(MobEffects.WEAKNESS) && random.nextFloat() < 0.25F) {
            potion = Potions.WEAKNESS;
        }

        NoFriendlyFirePotion thrownPotion = getPotion(potion);
        thrownPotion.setXRot(thrownPotion.getXRot() - -20.0F);
        thrownPotion.shoot(d1, d2 + distance * 0.2F, d3, 0.75F, 8.0F);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.WITCH_THROW, getSoundSource(), 1.0F, 0.8F + random.nextFloat() * 0.4F);
        level().addFreshEntity(thrownPotion);
    }

    private NoFriendlyFirePotion getPotion(Holder<Potion> potion) {
        ItemStack stack = PotionContents.createItemStack(Items.SPLASH_POTION, potion);
        return new NoFriendlyFirePotion(level(), this, stack);
    }

    @Override
    public boolean canAttackType(EntityType<?> type) {
        return true;
    }

    @Override
    public boolean isPassive() {
        return false;
    }

    /**
     * Poison does not work on the priests. 1.12.2 posted a {@code PotionApplicableEvent} and only an explicit ALLOW result let poison
     * through, which nothing gave, so it never did.
     */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.is(MobEffects.POISON)) {
            return false;
        }
        return super.canBeAffected(effect);
    }
}
