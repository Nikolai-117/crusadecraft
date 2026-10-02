package fr.crusadecraft.core.character;

import fr.crusadecraft.compat.minecolonies.MineColoniesBridge;
import fr.crusadecraft.config.CrusadeConfig;
import fr.crusadecraft.core.realm.Realm;
import fr.crusadecraft.core.realm.RealmService;
import fr.crusadecraft.core.law.LawEffects;
import fr.crusadecraft.core.treasury.LedgerCategory;
import fr.crusadecraft.core.treasury.TreasuryService;
import fr.crusadecraft.data.CrusadeSavedData;
import fr.crusadecraft.network.CrusadeNetwork;
import fr.crusadecraft.network.packet.SyncTitlesPacket;
import fr.crusadecraft.network.packet.ToastPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Les personnages : calcul de leur fiche, actions du souverain, morts et disparitions, titres affichés.
 *
 * <p>Tout ce qui vient de MineColonies (nom, compétences, métier, bonheur) est relu à chaque fois ;
 * seul ce qui n'existe que chez nous est enregistré, dans {@link CharacterRecord}.
 */
public final class CharacterService {

    public enum Action { REWARD, REPRIMAND, APPOINT, DISMISS, IMPRISON, RELEASE, EXILE, RECALL }

    /** Résultat d'une action : réussite ou non, et le message pour le souverain. */
    public record Outcome(boolean success, Component message) {
        static Outcome ok(Component m) { return new Outcome(true, m); }
        static Outcome no(String key, Object... args) { return new Outcome(false, Component.translatable(key, args)); }
    }

    private static StatFormula formula;
    private static Object formulaSource;

    private CharacterService() {}

    static StatFormula formula() {
        List<? extends String> rules = CrusadeConfig.SERVER.statRules.get();
        if (formula == null || rules != formulaSource) {
            formula = StatFormula.parse(rules);
            formulaSource = rules;
        }
        return formula;
    }

    /** Aptitudes de base, éducation, aptitudes finales (éducation et traits compris). */
    public record Stats(int[] base, Education education, int[] total) {}

    public static Stats stats(int[] skills, List<Trait> traits) {
        int[] base = formula().compute(skills);
        Education edu = Education.of(base);
        int[] total = base.clone();
        total[edu.stat().ordinal()] += edu.bonus();
        for (Trait t : traits) {
            for (Stat s : Stat.values()) total[s.ordinal()] += t.statModifier(s);
        }
        for (int i = 0; i < total.length; i++) total[i] = Math.max(0, total[i]);
        return new Stats(base, edu, total);
    }

    /** La fiche enregistrée du personnage, créée (traits tirés) à la première rencontre. */
    public static CharacterRecord record(MinecraftServer server, CitizenInfo info) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<CharacterRecord> existing = data.character(info.ref());
        if (existing.isPresent()) {
            CharacterRecord rec = existing.get();
            if (!rec.lastKnownName().equals(info.name())) {
                rec.setLastKnownName(info.name());
                data.setDirty();
            }
            return rec;
        }
        long seed = TraitGenerator.seed(server.getWorldData().worldGenOptions().seed(),
                info.ref().colony().dimension().toString(), info.ref().colony().colonyId(), info.ref().citizenId());
        List<Trait> traits = TraitGenerator.roll(seed, info.skills(), CrusadeConfig.SERVER.traitsPerCharacter.get());
        CharacterRecord rec = new CharacterRecord(info.ref(), info.name(), traits);
        data.putCharacter(rec);
        return rec;
    }

    /** Détail de l'opinion du personnage envers le souverain du royaume qui tient sa colonie. */
    public static List<Opinion.Line> opinionLines(MinecraftServer server, Realm realm, CharacterRecord rec) {
        double happiness = MineColoniesBridge.info(rec.ref().colony()).happiness();
        boolean holdsPost = realm.postOf(rec.ref()) != null;
        Opinion.Context ctx = new Opinion.Context(rec.traits(), happiness, holdsPost, rec.status());
        List<Opinion.Line> lines = Opinion.breakdown(rec.modifiers(), ctx, RealmService.currentDay(server));
        lines.addAll(LawEffects.opinionLines(realm.laws(), rec.traits(), holdsPost));
        return lines;
    }

    /** Opinion d'un citoyen envers son souverain ; 0 s'il est inconnu. */
    public static int opinionOf(MinecraftServer server, Realm realm, CitizenRef ref) {
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        if (info.isEmpty()) return 0;
        return Opinion.total(opinionLines(server, realm, record(server, info.get())));
    }

    public static Optional<CharacterView> view(MinecraftServer server, CitizenRef ref, ServerPlayer viewer) {
        Optional<CitizenInfo> found = MineColoniesBridge.citizen(ref);
        if (found.isEmpty()) return Optional.empty();
        CitizenInfo info = found.get();
        CrusadeSavedData data = CrusadeSavedData.get(server);
        CharacterRecord rec = record(server, info);
        long today = RealmService.currentDay(server);
        if (refreshStatus(rec, today)) data.setDirty();
        Stats stats = stats(info.skills(), rec.traits());

        Optional<Realm> holder = data.realmHolding(ref.colony());
        Optional<Realm> viewerRealm = data.realmOfRuler(viewer.getUUID());
        boolean canAct = holder.isPresent() && viewerRealm.isPresent()
                && holder.get().id().equals(viewerRealm.get().id()) && holder.get().state().isActive();
        List<Opinion.Line> lines = holder.map(r -> opinionLines(server, r, rec)).orElse(List.of());
        Post post = holder.map(r -> r.postOf(ref)).orElse(null);

        List<CharacterView.PostSlot> slots = new ArrayList<>();
        if (canAct) {
            Realm realm = holder.get();
            for (Post p : Post.values()) {
                CitizenRef current = p == Post.MAIRE ? realm.mayors().get(ref.colony()) : realm.council().get(p);
                slots.add(new CharacterView.PostSlot(p, current == null ? "" : nameOf(server, current), ref.equals(current)));
            }
        }
        List<HistoryEntry> history = rec.history().subList(0, Math.min(8, rec.history().size()));
        return Optional.of(new CharacterView(ref, info.name(), info.female(), info.child(), info.jobKey(),
                MineColoniesBridge.info(ref.colony()).name(), holder.map(Realm::name).orElse(""), post,
                rec.status(), rec.statusUntilDay(), rec.deceased(), info.skills(), stats.base(), stats.total(),
                stats.education(), rec.traits(), holder.isPresent(), Opinion.total(lines), lines, rec.stress(),
                info.happiness(), new ArrayList<>(history), info.entityId(), canAct,
                viewerRealm.map(Realm::feminineTitle).orElse(false), today, slots, sources()));
    }

    /** Les règles de calcul en vigueur, pour expliquer chaque aptitude dans les infobulles. */
    public static List<CharacterView.StatSource> sources() {
        return sources(formula());
    }

    public static List<CharacterView.StatSource> sources(StatFormula f) {
        List<CharacterView.StatSource> out = new ArrayList<>();
        for (Stat stat : Stat.values()) {
            List<Integer> skills = new ArrayList<>();
            List<Float> weights = new ArrayList<>();
            for (CitizenSkill skill : CitizenSkill.values()) {
                double w = f.weight(stat, skill);
                if (w != 0) {
                    skills.add(skill.ordinal());
                    weights.add((float) w);
                }
            }
            int[] sk = skills.stream().mapToInt(Integer::intValue).toArray();
            float[] wt = new float[weights.size()];
            for (int i = 0; i < wt.length; i++) wt[i] = weights.get(i);
            out.add(new CharacterView.StatSource(sk, wt, (float) f.divisor(stat)));
        }
        return out;
    }

    public static String nameOf(MinecraftServer server, CitizenRef ref) {
        return MineColoniesBridge.citizen(ref).map(CitizenInfo::name)
                .orElseGet(() -> CrusadeSavedData.get(server).character(ref).map(CharacterRecord::lastKnownName).orElse("?"));
    }

    // ------------------------------------------------------------------ actions du souverain

    public static Outcome act(ServerPlayer player, CitizenRef ref, Action action, Post post) {
        MinecraftServer server = player.server;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        Optional<Realm> owned = data.realmOfRuler(player.getUUID());
        if (owned.isEmpty()) return Outcome.no("crusadecraft.realm.none");
        Realm realm = owned.get();
        if (!realm.state().isActive()) return Outcome.no("crusadecraft.character.realm_dormant");
        if (!realm.holds(ref.colony())) return Outcome.no("crusadecraft.character.not_subject");
        Optional<CitizenInfo> found = MineColoniesBridge.citizen(ref);
        if (found.isEmpty()) return Outcome.no("crusadecraft.character.missing");
        CitizenInfo info = found.get();
        CharacterRecord rec = record(server, info);
        long today = RealmService.currentDay(server);
        refreshStatus(rec, today);
        String name = info.name();

        Outcome outcome = switch (action) {
            case APPOINT -> appoint(player, realm, rec, info, post, today);
            case DISMISS -> {
                Post vacated = realm.vacate(ref);
                if (vacated == null) yield Outcome.no("crusadecraft.character.no_post", name);
                rec.addModifier(OpinionModifier.of(OpinionReason.DESTITUE, today, 1));
                rec.remember(today, "crusadecraft.history.dismissed", vacated.key(info.female()));
                yield Outcome.ok(Component.translatable("crusadecraft.character.dismissed", name,
                        Component.translatable(vacated.key(info.female()))));
            }
            case REWARD -> reward(player, realm, rec, info, today);
            case REPRIMAND -> {
                double mult = rec.traits().contains(Trait.ARROGANT) ? 1.5 : rec.traits().contains(Trait.HUMBLE) ? 0.5 : 1.0;
                rec.addModifier(OpinionModifier.of(OpinionReason.REPRIMANDE, today, mult));
                rec.remember(today, "crusadecraft.history.reprimanded");
                realm.setDread(realm.dread() + 1);
                yield Outcome.ok(Component.translatable("crusadecraft.character.reprimanded", name));
            }
            case IMPRISON -> {
                if (rec.status() == CharacterStatus.EMPRISONNE) yield Outcome.no("crusadecraft.character.already_imprisoned", name);
                realm.vacate(ref);
                rec.setStatus(CharacterStatus.EMPRISONNE, -1);
                rec.addModifier(OpinionModifier.of(OpinionReason.EMPRISONNE, today, 1));
                rec.remember(today, "crusadecraft.history.imprisoned");
                realm.setDread(realm.dread() + 5);
                yield Outcome.ok(Component.translatable("crusadecraft.character.imprisoned", name));
            }
            case RELEASE -> {
                if (rec.status() != CharacterStatus.EMPRISONNE) yield Outcome.no("crusadecraft.character.not_imprisoned", name);
                rec.setStatus(CharacterStatus.LIBRE, -1);
                rec.addModifier(OpinionModifier.of(OpinionReason.LIBERE, today, 1));
                rec.remember(today, "crusadecraft.history.released");
                yield Outcome.ok(Component.translatable("crusadecraft.character.released", name));
            }
            case EXILE -> {
                if (rec.status() == CharacterStatus.EXILE) yield Outcome.no("crusadecraft.character.already_exiled", name);
                realm.vacate(ref);
                int days = CrusadeConfig.SERVER.exileDays.get();
                rec.setStatus(CharacterStatus.EXILE, today + days);
                rec.addModifier(OpinionModifier.of(OpinionReason.EXILE, today, 1));
                rec.remember(today, "crusadecraft.history.exiled", String.valueOf(days));
                realm.setDread(realm.dread() + 3);
                yield Outcome.ok(Component.translatable("crusadecraft.character.exiled", name, days));
            }
            case RECALL -> {
                if (rec.status() != CharacterStatus.EXILE) yield Outcome.no("crusadecraft.character.not_exiled", name);
                rec.setStatus(CharacterStatus.LIBRE, -1);
                rec.addModifier(OpinionModifier.of(OpinionReason.RAPPELE, today, 1));
                rec.remember(today, "crusadecraft.history.recalled");
                yield Outcome.ok(Component.translatable("crusadecraft.character.recalled", name));
            }
        };
        data.setDirty();
        if (outcome.success()) syncTitles(server);
        return outcome;
    }

    private static Outcome appoint(ServerPlayer player, Realm realm, CharacterRecord rec, CitizenInfo info, Post post, long today) {
        MinecraftServer server = player.server;
        String name = info.name();
        if (post == null) return Outcome.no("crusadecraft.character.bad_post");
        if (info.child()) return Outcome.no("crusadecraft.character.too_young", name);
        if (rec.status() != CharacterStatus.LIBRE) return Outcome.no("crusadecraft.character.unavailable", name,
                Component.translatable(rec.status().key()));
        if (post == realm.postOf(rec.ref())) return Outcome.no("crusadecraft.character.already_holds", name);
        int opinion = Opinion.total(opinionLines(server, realm, rec));
        if (opinion < CrusadeConfig.SERVER.refuseOfficeBelowOpinion.get() && !rec.traits().contains(Trait.AMBITIEUX)) {
            rec.remember(today, "crusadecraft.history.refused_post", post.key(info.female()));
            return Outcome.no("crusadecraft.character.refuses", name, Component.translatable(post.key(info.female())), opinion);
        }
        CitizenRef previous = realm.appoint(post, rec.ref());
        if (previous != null && !previous.equals(rec.ref())) {
            CrusadeSavedData.get(server).character(previous).ifPresent(prev -> {
                prev.addModifier(OpinionModifier.of(OpinionReason.DESTITUE, today, 1));
                prev.remember(today, "crusadecraft.history.replaced", post.key(false), name);
            });
        }
        rec.addModifier(OpinionModifier.of(OpinionReason.NOMME, today, 1));
        rec.remember(today, "crusadecraft.history.appointed", post.key(info.female()));
        Component postName = Component.translatable(post.key(info.female()));
        CrusadeNetwork.toPlayer(player, ToastPacket.of(ToastPacket.Kind.COUNCIL,
                Component.translatable("crusadecraft.toast.appointed.title"),
                Component.translatable("crusadecraft.toast.appointed.body", name, postName)));
        return Outcome.ok(Component.translatable("crusadecraft.character.appointed", name, postName));
    }

    private static Outcome reward(ServerPlayer player, Realm realm, CharacterRecord rec, CitizenInfo info, long today) {
        // D'abord le trésor ; à défaut, l'inventaire du souverain.
        int gold = CrusadeConfig.SERVER.rewardGold.get();
        if (gold > 0 && TreasuryService.spend(player.server, realm, LedgerCategory.RECOMPENSES, gold)) {
            return rewarded(rec, info, today);
        }
        ResourceLocation id = ResourceLocation.tryParse(CrusadeConfig.SERVER.rewardItem.get());
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        int count = CrusadeConfig.SERVER.rewardCount.get();
        if (item == null || item == net.minecraft.world.item.Items.AIR) return Outcome.no("crusadecraft.character.reward_misconfigured");
        if (!player.getAbilities().instabuild && !take(player, item, count)) {
            return Outcome.no("crusadecraft.character.reward_missing", gold, count, new ItemStack(item).getHoverName());
        }
        return rewarded(rec, info, today);
    }

    private static Outcome rewarded(CharacterRecord rec, CitizenInfo info, long today) {
        double mult = rec.traits().contains(Trait.AVARE) ? 1.5 : 1.0;
        rec.addModifier(OpinionModifier.of(OpinionReason.RECOMPENSE, today, mult));
        rec.remember(today, "crusadecraft.history.rewarded");
        return Outcome.ok(Component.translatable("crusadecraft.character.rewarded", info.name()));
    }

    /** Retire {@code count} objets de l'inventaire, seulement s'il y en a assez (rien n'est perdu sinon). */
    private static boolean take(ServerPlayer player, Item item, int count) {
        var inv = player.getInventory();
        int have = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.is(item)) have += s.getCount();
        }
        if (have < count) return false;
        int left = count;
        for (int i = 0; i < inv.getContainerSize() && left > 0; i++) {
            ItemStack s = inv.getItem(i);
            if (!s.is(item)) continue;
            int n = Math.min(left, s.getCount());
            s.shrink(n);
            left -= n;
        }
        inv.setChanged();
        return true;
    }

    // ------------------------------------------------------------------ vie et mort

    /** L'exil prend fin à sa date ; les motifs expirés disparaissent. Renvoie vrai si la fiche a changé. */
    static boolean refreshStatus(CharacterRecord rec, long today) {
        boolean changed = Opinion.prune(rec.modifiers(), today);
        if (rec.status() == CharacterStatus.EXILE && rec.statusUntilDay() >= 0 && today >= rec.statusUntilDay()) {
            rec.setStatus(CharacterStatus.LIBRE, -1);
            rec.remember(today, "crusadecraft.history.exile_ended");
            changed = true;
        }
        return changed;
    }

    public static void onCitizenDied(MinecraftServer server, CitizenRef ref, String name) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        data.character(ref).ifPresent(rec -> {
            rec.setDeceased(true);
            rec.remember(today, "crusadecraft.history.died");
        });
        boolean changed = false;
        for (Realm realm : data.realms()) {
            Post post = realm.vacate(ref);
            if (post == null) continue;
            changed = true;
            notifyRuler(server, realm, ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.died.title"),
                    Component.translatable("crusadecraft.toast.died.body", name, Component.translatable(post.key(false))));
        }
        data.setDirty();
        if (changed) syncTitles(server);
    }

    /**
     * Passage périodique : fin des exils, motifs expirés, postes tenus par des citoyens disparus
     * (partis, colonie supprimée).
     */
    public static void periodic(MinecraftServer server) {
        if (!MineColoniesBridge.isReady()) return;
        CrusadeSavedData data = CrusadeSavedData.get(server);
        long today = RealmService.currentDay(server);
        boolean changed = false;
        for (CharacterRecord rec : data.characters()) {
            changed |= refreshStatus(rec, today);
        }
        boolean titles = false;
        for (Realm realm : data.realms()) {
            List<CitizenRef> holders = new ArrayList<>(realm.council().values());
            holders.addAll(realm.mayors().values());
            for (CitizenRef ref : holders) {
                // MineColonies garde toutes ses colonies en mémoire : un citoyen introuvable est bien parti.
                if (MineColoniesBridge.citizenExists(ref)) continue;
                Post post = realm.vacate(ref);
                if (post == null) continue;
                String name = nameOf(server, ref);
                notifyRuler(server, realm, ToastPacket.Kind.ALERT, Component.translatable("crusadecraft.toast.gone.title"),
                        Component.translatable("crusadecraft.toast.gone.body", name, Component.translatable(post.key(false))));
                changed = true;
                titles = true;
            }
        }
        if (changed) data.setDirty();
        if (titles) syncTitles(server);
    }

    private static void notifyRuler(MinecraftServer server, Realm realm, ToastPacket.Kind kind, Component title, Component body) {
        ServerPlayer ruler = server.getPlayerList().getPlayer(realm.ruler());
        if (ruler != null) CrusadeNetwork.toPlayer(ruler, ToastPacket.of(kind, title, body));
    }

    // ------------------------------------------------------------------ titres au-dessus des têtes

    public static boolean isDignitary(MinecraftServer server, CitizenRef ref) {
        return CrusadeSavedData.get(server).realmEmploying(ref).isPresent();
    }

    public static List<SyncTitlesPacket.Entry> titles(MinecraftServer server) {
        List<SyncTitlesPacket.Entry> out = new ArrayList<>();
        for (Realm realm : CrusadeSavedData.get(server).realms()) {
            for (Map.Entry<Post, CitizenRef> e : realm.council().entrySet()) {
                out.add(entry(e.getValue(), e.getKey(), realm.name()));
            }
            for (Map.Entry<fr.crusadecraft.core.ColonyRef, CitizenRef> e : realm.mayors().entrySet()) {
                out.add(entry(e.getValue(), Post.MAIRE, MineColoniesBridge.info(e.getKey()).name()));
            }
        }
        return out;
    }

    private static SyncTitlesPacket.Entry entry(CitizenRef ref, Post post, String place) {
        Optional<CitizenInfo> info = MineColoniesBridge.citizen(ref);
        String name = info.map(CitizenInfo::name).orElse("?");
        boolean female = info.map(CitizenInfo::female).orElse(false);
        return new SyncTitlesPacket.Entry(ref, Component.translatable("crusadecraft.title.format", name,
                Component.translatable(post.key(female)), place));
    }

    public static void syncTitles(MinecraftServer server) {
        CrusadeNetwork.toAll(new SyncTitlesPacket(titles(server)));
    }

    public static void syncTitles(ServerPlayer player) {
        CrusadeNetwork.toPlayer(player, new SyncTitlesPacket(titles(player.server)));
    }
}
