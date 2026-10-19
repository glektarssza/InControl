package mcjty.incontrol.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mcjty.incontrol.ErrorHandler;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.player.Player;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class AISystem {

    enum Cmd {
        GOALS,
        TARGETS
    }

    enum Goals {
        LOOK_AT_PLAYER(
            "net.minecraft.world.entity.ai.goal.LookAtPlayerGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.LookAtPlayerGoal",
                "net.minecraft.world.entity.ai.goal.InteractGoal",
                "net.minecraft.world.entity.ai.goal.LookAtTradingPlayerGoal",
                "net.minecraft.world.entity.animal.Fox.FoxLookAtPlayerGoal",
                "net.minecraft.world.entity.animal.Panda.PandaLookAtPlayerGoal"
            }
        ),
        HURT_BY_TARGET(
            "net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal",
                "net.minecraft.world.entity.animal.horse.Llama.LlamaHurtByTargetGoal",
                "net.minecraft.world.entity.animal.Bee.BeeHurtByOtherGoal",
                "net.minecraft.world.entity.animal.Panda.PandaHurtByTargetGoal",
                "net.minecraft.world.entity.animal.PolarBear.PolarBearHurtByTargetGoal"
            }
        ),
        NEAREST_ATTACKABLE_TARGET(
            "net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal",
                "net.minecraft.world.entity.ai.goal.target.NearestAttackableWitchTargetGoal",
                "net.minecraft.world.entity.animal.Bee.BeeBecomeAngryTargetGoal",
                "net.minecraft.world.entity.animal.PolarBear.PolarBearAttackPlayersGoal",
                "net.minecraft.world.entity.monster.EnderMan.EndermanLookForPlayerGoal",
                "net.minecraft.world.entity.monster.Shulker.ShulkerDefenseAttackGoal",
                "net.minecraft.world.entity.monster.Spider.SpiderTargetGoal",
                "net.minecraft.world.entity.monster.Vindicator.VindicatorJohnnyAttackGoal"
            }
        ),
        RANGED_ATTACK(
            "net.minecraft.world.entity.ai.goal.RangedAttackGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.RangedAttackGoal",
                "net.minecraft.world.entity.monster.Drowned.DrownedTridentAttackGoal"
            }
        ),
        RANGED_BOW_ATTACK(
            "net.minecraft.world.entity.ai.goal.RangedBowAttackGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.RangedBowAttackGoal"
            }
        ),
        RANGED_CROSSBOW_ATTACK(
            "net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.RangedCrossbowAttackGoal"
            }
        ),
        MELEE_ATTACK(
            "net.minecraft.world.entity.ai.goal.MeleeAttackGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.MeleeAttackGoal",
                "net.minecraft.world.entity.ai.goal.ZombieAttackGoal",
                "net.minecraft.world.entity.animal.Bee.BeeAttackGoal",
                "net.minecraft.world.entity.animal.Fox.FoxMeleeAttackGoal",
                "net.minecraft.world.entity.animal.Panda.PandaAttackGoal",
                "net.minecraft.world.entity.animal.PolarBear.PolarBearMeleeAttackGoal",
                "net.minecraft.world.entity.monster.Drowned.DrownedAttackGoal",
                "net.minecraft.world.entity.monster.Spider.SpiderAttackGoal"
            }
        ),
        DETONATE(
            "net.minecraft.world.entity.ai.goal.SwellGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.SwellGoal"
            }
        ),
        RANDOM_LOOK_AROUND(
            "net.minecraft.world.entity.ai.goal.RandomLookAroundGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.RandomLookAroundGoal"
            }
        ),
        AVOID_ENTITY_GOAL(
            "net.minecraft.world.entity.ai.goal.AvoidEntityGoal",
            new String[] {
                "net.minecraft.world.entity.ai.goal.AvoidEntityGoal",
                "net.minecraft.world.entity.animal.Cat.CatAvoidEntityGoal",
                "net.minecraft.world.entity.animal.Ocelot.OcelotAvoidEntityGoal",
                "net.minecraft.world.entity.animal.Panda.PandaAvoidGoal",
                "net.minecraft.world.entity.animal.Rabbit.RabbitAvoidEntityGoal",
                "net.minecraft.world.entity.animal.Wolf.WolfAvoidEntityGoal"
            }
        );

        /**
         * The fully qualified name of the "main" goal class.
         *
         * This is the class from which any subclasses are derived and is used as
         * the root class when performing subclass checks in place of the
         * {@link Goals#knownSubclasses known subclasses list}.
         */
        public final String mainClass;

        /**
         * A list of known, vanilla subclasses of the
         * {@link Goals#mainClass "main" goal class}.
         */
        public final String[] knownSubclasses;

        private Goals(final String mainClass, final String[] subclasses) {
            this.mainClass = mainClass;
            this.knownSubclasses = subclasses;
        }
    }

    private static final Map<String, Class<?>> CLASS_CACHE = new HashMap<>();

    private static final Map<String, Cmd> ACTIONS = new HashMap<>();
    static {
        for (Cmd cmd : Cmd.values()) {
            ACTIONS.put(cmd.name().toLowerCase(), cmd);
        }
    }

    private static final Map<String, Goals> GOALS = new HashMap<>();
    static {
        for (Goals goal : Goals.values()) {
            GOALS.put(goal.name().toLowerCase(), goal);
        }
    }

    public static Consumer<Mob> parse(String json) {
        Consumer<Mob> action = null;

        JsonElement element = JsonParser.parseString(json);
        if (!element.isJsonObject()) {
            ErrorHandler.error("Invalid ai action! Expected a JSON object.");
            return e -> {};
        }
        JsonObject object = element.getAsJsonObject();

        for (String attr : object.keySet()) {
            Cmd cmd = ACTIONS.get(attr);
            if (cmd == null) {
                ErrorHandler.error("Invalid action '" + attr + "' for ai rule!");
                return e -> {};
            }
            JsonElement value = object.get(attr);
            switch (cmd) {
                case GOALS:
                    if (value.isJsonArray()) {
                        for (JsonElement el : value.getAsJsonArray()) {
                            BiConsumer<GoalSelector, Mob> consumer = parseGoal(el, cmd);
                            action = combineConsumer(action, e -> consumer.accept(e.goalSelector, e));
                        }
                    } else {
                        ErrorHandler.error("Invalid value for 'goals' action! Expected a JSON array.");
                        return combineConsumer(action, e -> {});
                    }
                    break;
                case TARGETS:
                    if (value.isJsonArray()) {
                        for (JsonElement el : value.getAsJsonArray()) {
                            BiConsumer<GoalSelector, Mob> consumer = parseGoal(el, cmd);
                            action = combineConsumer(action, e -> consumer.accept(e.targetSelector, e));
                        }
                    } else {
                        ErrorHandler.error("Invalid value for 'targets' action! Expected a JSON array.");
                        return combineConsumer(action, e -> {});
                    }
                    break;
            }
        }
        return combineConsumer(action, e -> {});
    }

    private static int getDefaultInt(JsonObject object, String key, int defaultValue) {
        if (object.has(key)) {
            JsonElement element = object.get(key);
            return element.getAsInt();
        }
        return defaultValue;
    }

    private static float getDefaultFloat(JsonObject object, String key, float defaultValue) {
        if (object.has(key)) {
            JsonElement element = object.get(key);
            return element.getAsFloat();
        }
        return defaultValue;
    }

    private static double getDefaultDouble(JsonObject object, String key, double defaultValue) {
        if (object.has(key)) {
            JsonElement element = object.get(key);
            return element.getAsDouble();
        }
        return defaultValue;
    }

    private static boolean getDefaultBoolean(JsonObject object, String key, boolean defaultValue) {
        if (object.has(key)) {
            JsonElement element = object.get(key);
            return element.getAsBoolean();
        }
        return defaultValue;
    }

    private static String getDefaultString(JsonObject object, String key, String defaultValue) {
        if (object.has(key)) {
            JsonElement element = object.get(key);
            return element.getAsString();
        }
        return defaultValue;
    }

    private static List<JsonElement> getDefaultList(JsonObject object, String key, List<JsonElement> defaultValue) {
        if (object.has(key)) {
            JsonElement element = object.get(key);
            return element.getAsJsonArray().asList();
        }
        return defaultValue;
    }

    private static Class<?> getEntityClass(JsonObject object, String key, Class defaultClass) {
        if (!object.has(key)) {
            return defaultClass;
        }
        JsonElement element = object.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            ErrorHandler.error("Invalid entity class in '" + key + "' action! Expected a string.");
            return null;
        }
        String entityName = element.getAsString();
        EntityType<?> entityType = BuiltInRegistries.ENTITY_TYPE.get(ResourceLocation.parse(entityName));
        if (entityType == null) {
            ErrorHandler.error("Unknown entity '" + entityName + "'!");
            return null;
        }
        return entityType.getBaseClass();
    }

    @Nonnull
    private static List<Predicate<Goal>> parseClassRemoveRule(JsonElement el, Cmd cmd) {
        final String cmdName = cmd.name().toLowerCase();
        final List<Predicate<Goal>> predicates = new ArrayList<Predicate<Goal>>();
        if (!el.isJsonObject()) {
            ErrorHandler.error("Invalid removal rule in AI '" + cmdName + "' action! Expected a JSON object.");
            return predicates;
        }
        final JsonObject goal = el.getAsJsonObject();
        final String className = getDefaultString(goal, "name", "");
        if (className.isBlank()) {
            ErrorHandler.error("Invalid removal rule in AI '" + cmdName + "' action! Expected class name to be a non-blank string.");
            return predicates;
        }
        final boolean includeSubclasses = getDefaultBoolean(goal, "includesubclasses", false);
        if (includeSubclasses) {
            final Class<?> mainClass = CLASS_CACHE.computeIfAbsent(className, (mainClassName) -> {
                try {
                    Class<?> clazz = AISystem.class.getClassLoader().loadClass(mainClassName);
                    if (!Goal.class.isAssignableFrom(clazz)) {
                        throw new ClassCastException("Cannot assign class \"net.minecraft.world.entity.ai.goal.Goal\" from class \"" + mainClassName +"\"");
                    }
                    return clazz;
                } catch (ClassNotFoundException _ex) {
                    return null;
                } catch (ClassCastException _ex) {
                    return null;
                }
            });
            predicates.add(gg -> mainClass.isInstance(gg));
        } else {
            predicates.add(gg -> gg.getClass().getName().equals(className));
        }
        return predicates;
    }

    @Nonnull
    private static List<Predicate<Goal>> parseGoalRemoveRule(JsonElement el, Cmd cmd) {
        final String cmdName = cmd.name().toLowerCase();
        final List<Predicate<Goal>> predicates = new ArrayList<Predicate<Goal>>();
        if (!el.isJsonObject()) {
            ErrorHandler.error("Invalid removal rule in AI '"+ cmdName +"' action! Expected a JSON object.");
            return predicates;
        }
        final JsonObject goal = el.getAsJsonObject();
        final String goalName = getDefaultString(goal, "name", "");
        if (goalName.isBlank()) {
            ErrorHandler.error("Invalid removal rule in AI '"+ cmdName +"' action! Expected goal name to be a non-blank string.");
            return predicates;
        }
        final boolean includeSubclasses = getDefaultBoolean(goal, "includesubclasses", false);
        Goals g = GOALS.get(goalName.toLowerCase());
        if (g == null) {
            ErrorHandler.error("Invalid goal name '" + goalName + "' found while parsing remove rule in AI '" + cmdName + "' action!");
            return predicates;
        }
        if (includeSubclasses) {
            final Class<?> mainClass = CLASS_CACHE.computeIfAbsent(g.mainClass, (mainClassName) -> {
                try {
                    Class<?> clazz = AISystem.class.getClassLoader().loadClass(mainClassName);
                    if (!Goal.class.isAssignableFrom(clazz)) {
                        throw new ClassCastException("Cannot assign class \"net.minecraft.world.entity.ai.goal.Goal\" from class \"" + mainClassName +"\"");
                    }
                    return clazz;
                } catch (ClassNotFoundException _ex) {
                    return null;
                } catch (ClassCastException _ex) {
                    return null;
                }
            });
            predicates.add(gg -> mainClass.isInstance(gg));
        } else {
            predicates.addAll(Arrays.asList(g.knownSubclasses).parallelStream().<Predicate<Goal>>map((subclass) -> gg -> gg.getClass().getName().equals(subclass)).toList());
        }
        return predicates;
    }

    @Nonnull
    private static List<Predicate<Goal>> parseRemoveRule(JsonElement el, Cmd cmd) {
        final String cmdName = cmd.name().toLowerCase();
        final List<Predicate<Goal>> predicates = new ArrayList<Predicate<Goal>>();
        if (!el.isJsonObject()) {
            ErrorHandler.error("Invalid removal rule in AI '" + cmdName + "' action! Expected a JSON object.");
            return predicates;
        }
        final JsonObject rule = el.getAsJsonObject();
        if (rule.getAsJsonObject().has("goal")) {
            predicates.addAll(parseGoalRemoveRule(rule, cmd));
        }
        if (rule.getAsJsonObject().has("classes")) {
            predicates.addAll(parseClassRemoveRule(rule, cmd));
        }
        return predicates;
    }

    private static BiConsumer<GoalSelector, Mob> parseGoal(JsonElement el, Cmd cmd) {
        final String cmdName = cmd.name().toLowerCase();
        if (!el.isJsonObject()) {
            ErrorHandler.error("Invalid goal in AI '" + cmdName + "' action! Expected a JSON object.");
            return (s, e) -> {};
        }
        final JsonObject goal = el.getAsJsonObject();
        // If it has the atttributes 'removeall' then we remove all goals in this action
        if (goal.has("removeall")) {
            return (s, e) -> e.goalSelector.removeAllGoals(g -> true);
        }
        // If it has the atttributes 'remove' then we remove requested goals in this action
        if (goal.has("remove")) {
            if (goal.get("remove").isJsonArray()) {
                final List<Predicate<Goal>> predicates = new ArrayList<>();
                goal.getAsJsonArray("remove").forEach((removeRule) -> predicates.addAll(parseRemoveRule(removeRule, cmd)));
                if (predicates.isEmpty()) {
                    return (s, e) -> {};
                }
                if (predicates.parallelStream().anyMatch((p) -> p == null)) {
                    return (s, e) -> {};
                }
                return (s, e) -> e.goalSelector.removeAllGoals((g) -> predicates.parallelStream().anyMatch((p) -> p.test(g)));
            }
            ErrorHandler.error("Invalid remove goal in '" + cmd.name().toLowerCase() + "' action! Expected 'remove' to be an array of conditions.");
            return (s, e) -> {};
        }
        // It has the attributes 'goal' and 'priority'. Then specific attributes for the goal.
        if (!goal.has("goal") || !goal.has("priority")) {
            ErrorHandler.error("Invalid goal in '" + cmd.name().toLowerCase() + "' action! Expected 'goal' and 'priority' attributes.");
            return (s, e) -> {};
        }
        BiConsumer<GoalSelector, Mob> goalSelector = null;
        String goalName = goal.get("goal").getAsString();
        int priority = goal.get("priority").getAsInt();
        Goals g = GOALS.get(goalName.toLowerCase());
        if (g == null) {
            ErrorHandler.error("Invalid goal '" + goalName + "' in AI '" + cmdName + "' action!");
            return (s, e) -> {};
        }
        switch (g) {
            case LOOK_AT_PLAYER: {
                float lookdistance = getDefaultFloat(goal, "lookdistance", 8.0f);
                float probability = getDefaultFloat(goal, "probability", 0.02f);
                boolean onlyHorizontal = getDefaultBoolean(goal, "onlyhorizontal", false);
                return combineConsumer(goalSelector, (s, e) -> s.addGoal(priority, new LookAtPlayerGoal(e, Player.class, lookdistance, probability, onlyHorizontal)));
            }
            case HURT_BY_TARGET:
                return combineConsumer(goalSelector, (s, e) -> {
                    if (e instanceof PathfinderMob pf) {
                        s.addGoal(priority, new HurtByTargetGoal(pf));
                    }
                });
            case NEAREST_ATTACKABLE_TARGET: {
                boolean mustsee = getDefaultBoolean(goal, "mustsee", true);
                boolean mustreach = getDefaultBoolean(goal, "mustreach", false);
                int randomInterval = getDefaultInt(goal, "randominterval", 10);
                return combineConsumer(goalSelector, (s, e) -> s.addGoal(priority, new NearestAttackableTargetGoal<>(e, Player.class, randomInterval, mustsee, mustreach, null)));
            }
            case RANGED_ATTACK: {
                return combineConsumer(goalSelector, (s, e) -> {
                    double speedModifier = getDefaultDouble(goal, "speedmodifier", 1.0f);
                    int attackInterval = getDefaultInt(goal, "attackInterval", 20);
                    float attackRadius = getDefaultFloat(goal, "attackRadius", 15.0F);
                    if (e instanceof RangedAttackMob ra) {
                        s.addGoal(priority, new RangedAttackGoal(ra, speedModifier, attackInterval, attackRadius));
                    }
                });
            }
            case MELEE_ATTACK: {
                float speed = getDefaultFloat(goal, "speed", 1.0f);
                boolean followingTargetEvenNotSeen = getDefaultBoolean(goal, "followingtarget", false);
                return combineConsumer(goalSelector, (s, e) -> {
                    if (e instanceof PathfinderMob pf) {
                        s.addGoal(priority, new MeleeAttackGoal(pf, speed, followingTargetEvenNotSeen));
                    }
                });
            }
            case DETONATE: {
                return combineConsumer(goalSelector, (s, e) -> {
                    if (e instanceof Creeper c) {
                        s.addGoal(priority, new SwellGoal(c));
                    }
                });
            }
            case RANDOM_LOOK_AROUND: {
                return combineConsumer(goalSelector, (s, e) -> s.addGoal(priority, new RandomLookAroundGoal(e)));
            }
            case AVOID_ENTITY_GOAL: {
                Class entityClass = getEntityClass(goal, "entity", Player.class);
                if (entityClass == null) {
                    return (s, e) -> {};
                }
                float maxDistance = getDefaultFloat(goal, "maxdistance", 10.0f);
                double walkSpeedModifier = getDefaultDouble(goal, "walkspeedmodifier", 1.0f);
                double sprintSpeedModifier = getDefaultDouble(goal, "sprintspeedmodifier", 1.2f);
                return combineConsumer(goalSelector, (s, e) -> {
                    if (e instanceof PathfinderMob pf) {
                        s.addGoal(priority, new AvoidEntityGoal<>(pf, entityClass, maxDistance, walkSpeedModifier, sprintSpeedModifier));
                    }
                });
            }
            default:
                ErrorHandler.error("Unknown goal '" + g + "' in 'goals' action!");
                return (s, e) -> {};
        }
    }

    private static <T> Consumer<T> combineConsumer(@Nullable Consumer<T> first, @Nonnull Consumer<T> second) {
        if (first == null) {
            return second;
        } else {
            return first.andThen(second);
        }
    }

    private static <T,S> BiConsumer<T,S> combineConsumer(@Nullable BiConsumer<T, S> first, @Nonnull BiConsumer<T, S> second) {
        if (first == null) {
            return second;
        } else {
            return first.andThen(second);
        }
    }
}
