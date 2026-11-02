package fr.crusadecraft.core.city;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/**
 * Ce que vendent les gens d'une ville (P38). Un villageois de Comes Alive est un villageois de Minecraft : on lui donne
 * son métier, le rang de maître (il ne le perdra plus, même sans poste de travail) et ses échanges, qu'on remet en
 * stock chaque jour. Le boulanger, le tavernier et le forgeron ont les leurs ; les autres, ceux de leur métier.
 */
public final class CityTrades {
    /** Le rang de maître : tous les échanges du métier, et plus de montée qui les changerait. */
    static final int MASTER = 5, MASTER_XP = 250;

    private CityTrades() {}

    /** Le métier d'un poste ({@code minecraft:farmer}, {@code mca:guard}), ou rien s'il n'est pas installé. */
    public static VillagerProfession profession(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        return rl == null ? null : ForgeRegistries.VILLAGER_PROFESSIONS.getValue(rl);
    }

    /** Donne à ce villageois le métier de son poste et ce qu'il vend. Sans effet sur ce qui n'est pas un villageois. */
    public static void apply(Entity entity, BuildingFunction function, String post, RandomSource random) {
        if (!(entity instanceof Villager v)) return;
        VillagerProfession prof = profession(post);
        if (prof == null) prof = VillagerProfession.NONE;
        // Le métier d'abord : en changer efface les échanges.
        v.setVillagerData(v.getVillagerData().setProfession(prof).setLevel(prof == VillagerProfession.NONE ? 1 : MASTER));
        if (prof != VillagerProfession.NONE) v.setVillagerXp(MASTER_XP);
        setOffers(v, offers(entity, function, prof, random));
    }

    /**
     * Les échanges d'un villageois. Côté serveur, {@code overrideOffers} ne fait rien (il ne sert qu'au client) : on
     * passe par ses données enregistrées, que Minecraft (et Comes Alive) relisent en entier.
     */
    static void setOffers(Villager v, MerchantOffers offers) {
        net.minecraft.nbt.CompoundTag t = v.saveWithoutId(new net.minecraft.nbt.CompoundTag());
        t.put("Offers", offers.createTag());
        v.load(t);
    }

    static MerchantOffers offers(Entity entity, BuildingFunction function, VillagerProfession prof, RandomSource random) {
        return switch (function.trades()) {
            case NONE -> new MerchantOffers();
            case VANILLA -> vanilla(entity, List.of(prof), 2, random);
            case BAKER -> baker();
            case TAVERN -> tavern();
            case SMITH -> vanilla(entity, List.of(VillagerProfession.WEAPONSMITH, VillagerProfession.ARMORER, VillagerProfession.TOOLSMITH), 1, random);
        };
    }

    /** Les échanges de Minecraft de ces métiers, {@code perLevel} par métier et par niveau, comme un maître. */
    static MerchantOffers vanilla(Entity entity, List<VillagerProfession> professions, int perLevel, RandomSource random) {
        MerchantOffers out = new MerchantOffers();
        for (int level = 1; level <= MASTER; level++) {
            for (VillagerProfession prof : professions) {
                Int2ObjectMap<VillagerTrades.ItemListing[]> table = VillagerTrades.TRADES.get(prof);
                VillagerTrades.ItemListing[] listings = table == null ? null : table.get(level);
                if (listings == null || listings.length == 0) continue;
                List<VillagerTrades.ItemListing> pool = new ArrayList<>(List.of(listings));
                for (int i = 0; i < perLevel && !pool.isEmpty(); i++) {
                    VillagerTrades.ItemListing listing = pool.remove(random.nextInt(pool.size()));
                    try {
                        MerchantOffer offer = listing.getOffer(entity, random);
                        if (offer != null) out.add(offer);
                    } catch (RuntimeException ignored) {
                        // un échange qui a besoin d'un monde plus complet (une carte au trésor) : on s'en passe
                    }
                }
            }
        }
        return out;
    }

    /** Le boulanger : pain, biscuits, gâteaux, tartes ; il achète le blé. */
    static MerchantOffers baker() {
        MerchantOffers out = new MerchantOffers();
        out.add(sell(1, new ItemStack(Items.BREAD, 6), 16));
        out.add(sell(1, new ItemStack(Items.COOKIE, 10), 12));
        out.add(sell(1, new ItemStack(Items.PUMPKIN_PIE, 3), 12));
        out.add(sell(2, new ItemStack(Items.CAKE), 6));
        out.add(buy(new ItemStack(Items.WHEAT, 20), 16));
        out.add(buy(new ItemStack(Items.SUGAR, 12), 12));
        return out;
    }

    /** Le tavernier : à manger et à boire ; il achète les pommes de terre. */
    static MerchantOffers tavern() {
        MerchantOffers out = new MerchantOffers();
        out.add(sell(1, new ItemStack(Items.MUSHROOM_STEW), 12));
        out.add(sell(2, new ItemStack(Items.RABBIT_STEW), 8));
        out.add(sell(1, new ItemStack(Items.BAKED_POTATO, 6), 16));
        out.add(sell(1, new ItemStack(Items.COOKED_CHICKEN, 3), 16));
        out.add(sell(2, new ItemStack(Items.HONEY_BOTTLE, 2), 12));
        out.add(buy(new ItemStack(Items.POTATO, 24), 16));
        return out;
    }

    private static MerchantOffer sell(int emeralds, ItemStack what, int uses) {
        return new MerchantOffer(new ItemStack(Items.EMERALD, emeralds), what, uses, 5, 0.05f);
    }

    private static MerchantOffer buy(ItemStack what, int uses) {
        return new MerchantOffer(what, new ItemStack(Items.EMERALD), uses, 10, 0.05f);
    }
}
