package fr.crusadecraft.core.city;

import fr.crusadecraft.core.build.Site;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Repliques;
import fr.crusadecraft.core.logic.Fr;
import fr.crusadecraft.core.nation.NationDef;
import fr.crusadecraft.core.nation.NationRules;
import fr.crusadecraft.core.world.AiNames;
import fr.crusadecraft.data.CrusadeSavedData;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ce que dit un habitant (P42). Aucune intelligence artificielle ici : des textes écrits d'avance, en grand nombre, et
 * choisis selon qui parle (son métier, son caractère, son âge, sa nation) et qui écoute (ce qu'il en pense). Un texte
 * a des trous, {@code {ville}}, et des accords, {@code {Né|Née}} : la forme masculine, puis la féminine.
 */
public final class CityVoice {
    private static final Pattern GENDER = Pattern.compile("\\{([^{}|]*)\\|([^{}|]*)\\}");

    private CityVoice() {}

    /** Les accords selon le sexe de celui qui parle, en logique pure. */
    public static String agree(String text, boolean female) {
        Matcher m = GENDER.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) m.appendReplacement(out, Matcher.quoteReplacement(female ? m.group(2) : m.group(1)));
        m.appendTail(out);
        return out.toString();
    }

    /** Qui parle, pour le choix du texte. */
    static Repliques.Who who(MinecraftServer server, Site site, Person p, UUID listener) {
        String nation = CityPeople.nation(server, site).map(NationDef::key).orElse("");
        return new Repliques.Who(p.role(), p.traits(), p.job(), nation, site.name(), p.age(), p.female(), listener == null ? 0 : p.opinionOf(listener));
    }

    /** Une réplique de cette personne, ses trous remplis, si un texte lui convient. */
    public static Optional<String> text(MinecraftServer server, Site site, Person p, Repliques.Moment asked, ServerPlayer listener, Random random) {
        Repliques.Moment moment = forAge(p, asked);
        Component line = null;
        // P47 : ce qu'on a écrit pour lui dans l'éditeur passe avant tout, deux fois sur trois.
        if (p.hasSettings() && random.nextInt(3) != 0) {
            List<String> own = p.settings().lines.get(asked.name());
            if (own == null || own.isEmpty()) own = p.settings().lines.get(NpcSettings.ANY);
            if (own != null && !own.isEmpty()) line = Component.literal(own.get(random.nextInt(own.size())));
        }
        // Un personnage écrit d'avance parle d'abord avec ses propres mots, à l'accueil.
        if (line == null && (moment == Repliques.Moment.ACCUEIL || moment == Repliques.Moment.TRAVAIL)) {
            List<String> own = CityPeople.notable(server, site, p).map(NationDef.Notable::lines).orElse(List.of());
            if (!own.isEmpty() && random.nextInt(3) != 0) line = Component.literal(own.get(random.nextInt(own.size())));
        }
        Repliques.Who who = who(server, site, p, listener == null ? null : listener.getUUID());
        // Ce que le fichier de sa nation prévoit pour ce moment, une fois sur trois quand il y en a.
        if (line == null && random.nextInt(3) == 0) {
            List<Repliques.Entry> lots = CityPeople.nation(server, site).map(NationDef::lines).orElse(List.of());
            if (!lots.isEmpty()) line = Repliques.pick(lots, moment, who, random);
        }
        if (line == null) line = Repliques.pick(Repliques.entries(), moment, who, random);
        if (line == null) return Optional.empty();
        return Optional.of(fill(server, site, p, Fr.of(line), listener, random));
    }

    /** Un bébé babille, un enfant répond comme un enfant, quel que soit le sujet. La douleur, elle, n'a pas d'âge. */
    static Repliques.Moment forAge(Person p, Repliques.Moment m) {
        if (m == Repliques.Moment.DOULEUR || m.name().startsWith("VIE_") || m.name().startsWith("MESSAGE_")) return m;
        if (p.age() < 3) return Repliques.Moment.BEBE;
        if (p.age() < CityRules.CHILD && m != Repliques.Moment.ECOLE) return Repliques.Moment.ENFANT;
        return m;
    }

    /** P50 : ce qu'il dit à un autre habitant, avec quelques trous de plus. */
    public static Optional<String> line(MinecraftServer server, Site site, Person p, Repliques.Moment moment, Map<String, String> extra, Random random) {
        Component line = Repliques.pick(Repliques.entries(), moment, who(server, site, p, null), random);
        if (line == null) return Optional.empty();
        return Optional.of(fill(server, site, p, NationRules.fill(Fr.of(line), extra), null, random));
    }

    /** P49 : une lettre, pour un joueur qui n'est peut-être pas là, avec quelques trous de plus. */
    public static Optional<String> letter(MinecraftServer server, Site site, Person p, Repliques.Moment moment, UUID to, String toName, Map<String, String> extra, Random random) {
        Repliques.Who who = who(server, site, p, to);
        Component line = Repliques.pick(Repliques.entries(), moment, who, random);
        if (line == null) return Optional.empty();
        Map<String, String> all = new HashMap<>(extra);
        all.put("joueur", toName.isEmpty() ? Fr.key("crusadecraft.city.post.stranger") : toName);
        return Optional.of(fill(server, site, p, NationRules.fill(Fr.of(line), all), null, random));
    }

    /** Les trous et les accords d'un texte. */
    public static String fill(MinecraftServer server, Site site, Person p, String text, ServerPlayer listener, Random random) {
        CrusadeSavedData data = CrusadeSavedData.get(server);
        LineageState state = data.lineage();
        Map<String, String> vars = new HashMap<>();
        vars.put("prenom", p.name());
        vars.put("nom", state.fullName(p));
        vars.put("ville", site.name());
        vars.put("age", Integer.toString(p.age()));
        vars.put("joueur", listener == null ? "étranger" : listener.getGameProfile().getName());
        vars.put("metier", CityTalk.jobTitle(p).toLowerCase(java.util.Locale.ROOT));
        data.world().realm(site.owner()).ifPresent(r -> {
            vars.put("royaume", Fr.of(AiNames.realm(r.rank(), r.place())));
            vars.put("pays", r.place());
            vars.put("souverain", r.ruler().name());
        });
        vars.putIfAbsent("royaume", "le royaume");
        vars.putIfAbsent("pays", "le pays");
        vars.putIfAbsent("souverain", "le souverain");
        vars.put("conjoint", state.person(p.spouse()).filter(Person::alive).map(Person::name).orElse("personne"));
        vars.put("voisin", neighbour(state, site, p, random));
        vars.put("autre_ville", otherCity(data, site, random));
        vars.put("rumeur", CityRumors.pick(server, site, random).orElse("rien de neuf"));
        vars.put("jour", Long.toString(fr.crusadecraft.core.realm.RealmService.currentDay(server)));
        return NationRules.fill(agree(text, p.female()), vars);
    }

    private static String neighbour(LineageState state, Site site, Person p, Random random) {
        List<String> names = new ArrayList<>();
        for (CityBuilding b : site.buildings()) {
            for (UUID id : b.occupants()) {
                if (id.equals(p.id())) continue;
                state.person(id).filter(x -> x.alive() && x.age() >= 16).ifPresent(x -> names.add(x.name()));
            }
        }
        return names.isEmpty() ? "le voisin" : names.get(random.nextInt(names.size()));
    }

    private static String otherCity(CrusadeSavedData data, Site site, Random random) {
        List<String> names = new ArrayList<>();
        for (Site s : data.world().sites()) if (s != site && CityService.city(s) && s.owner() != null) names.add(s.name());
        return names.isEmpty() ? "la grande ville" : names.get(random.nextInt(names.size()));
    }
}
