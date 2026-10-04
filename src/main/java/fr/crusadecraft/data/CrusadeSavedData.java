package fr.crusadecraft.data;

import fr.crusadecraft.CrusadeCraft;
import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CharacterRecord;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.realm.Realm;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Toutes les données de CrusadeCraft pour un monde, dans {@code saves/<monde>/data/crusadecraft.dat}.
 *
 * <p>Jamais dans {@code config/} : ce dossier est commun à tous les mondes d'une instance, et
 * War 'N Taxes y mélangeait ainsi les royaumes de mondes différents.
 *
 * <p>Le format est versionné. {@link #DATA_VERSION} augmente à chaque changement de structure, et
 * {@link DataMigrations} met à niveau les anciennes sauvegardes au chargement.
 */
public final class CrusadeSavedData extends AtomicSavedData {
    public static final String FILE_NAME = CrusadeCraft.MOD_ID;
    public static final int DATA_VERSION = 13;

    private final Map<UUID, Realm> realms = new LinkedHashMap<>();
    private final Map<CitizenRef, CharacterRecord> characters = new LinkedHashMap<>();
    /** Les royaumes IA et les royaumes de jure. */
    private fr.crusadecraft.core.world.WorldState world = new fr.crusadecraft.core.world.WorldState();
    /** Relations entre royaumes : traités, vassalité, tributs, opinions. */
    private fr.crusadecraft.core.diplomacy.Diplomacy diplomacy = new fr.crusadecraft.core.diplomacy.Diplomacy();
    /** Guerres en cours et registre des dégâts. */
    private fr.crusadecraft.core.war.WarState wars = new fr.crusadecraft.core.war.WarState();
    /** Le registre des lignées : personnes, maisons, communautés (P13). */
    private fr.crusadecraft.core.lineage.LineageState lineage = new fr.crusadecraft.core.lineage.LineageState();
    /** Les lieux : titres et places, leur hiérarchie et leurs fiches (P15). */
    private fr.crusadecraft.core.place.PlaceState places = new fr.crusadecraft.core.place.PlaceState();

    public static CrusadeSavedData get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getDataStorage()
                .computeIfAbsent(tag -> load(server, tag), CrusadeSavedData::new, FILE_NAME);
    }

    /** Avant qu'une autre version du mod ne touche aux données, une copie datée du fichier tel qu'il est. */
    private static CrusadeSavedData load(MinecraftServer server, CompoundTag tag) {
        int version = tag.contains("version") ? tag.getInt("version") : DATA_VERSION;
        if (version != DATA_VERSION) {
            java.nio.file.Path file = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                    .resolve("data").resolve(FILE_NAME + ".dat");
            try {
                SaveBackups.backup(file, version, version > DATA_VERSION, java.time.LocalDateTime.now()).ifPresent(copy ->
                        CrusadeCraft.LOGGER.info("CrusadeCraft : copie de secours des données en version {} : {}", version, copy));
            } catch (java.io.IOException | RuntimeException e) {
                CrusadeCraft.LOGGER.error("CrusadeCraft : copie de secours impossible avant la mise à niveau ; il ne reste que le .bak", e);
            }
        }
        return load(tag);
    }

    /** Lit des données, quelle que soit leur version (public pour les tests de non-régression des sauvegardes). */
    public static CrusadeSavedData load(CompoundTag tag) {
        CompoundTag migrated = DataMigrations.migrate(tag);
        CrusadeSavedData data = new CrusadeSavedData();
        ListTag list = migrated.getList("realms", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                Realm realm = Realm.load(list.getCompound(i));
                data.realms.put(realm.id(), realm);
            } catch (RuntimeException e) {
                // Un royaume illisible ne doit pas empêcher de charger les autres.
                CrusadeCraft.LOGGER.error("CrusadeCraft : royaume illisible ignoré (index {})", i, e);
            }
        }
        ListTag chars = migrated.getList("characters", Tag.TAG_COMPOUND);
        for (int i = 0; i < chars.size(); i++) {
            try {
                CharacterRecord c = CharacterRecord.load(chars.getCompound(i));
                data.characters.put(c.ref(), c);
            } catch (RuntimeException e) {
                CrusadeCraft.LOGGER.error("CrusadeCraft : personnage illisible ignoré (index {})", i, e);
            }
        }
        try {
            data.world = fr.crusadecraft.core.world.WorldState.load(migrated.getCompound("world"));
        } catch (RuntimeException e) {
            // Le monde IA se régénère ; mieux vaut le perdre que perdre les royaumes des joueurs.
            CrusadeCraft.LOGGER.error("CrusadeCraft : royaumes IA illisibles, ils seront régénérés", e);
        }
        data.diplomacy = fr.crusadecraft.core.diplomacy.Diplomacy.load(migrated, "diplomacy");
        data.wars = fr.crusadecraft.core.war.WarState.load(migrated.getCompound("wars"));
        data.lineage = fr.crusadecraft.core.lineage.LineageState.load(migrated.getCompound("lineage"));
        data.places = fr.crusadecraft.core.place.PlaceState.load(migrated.getCompound("places"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", DATA_VERSION);
        ListTag list = new ListTag();
        for (Realm realm : realms.values()) list.add(realm.save());
        tag.put("realms", list);
        ListTag chars = new ListTag();
        for (CharacterRecord c : characters.values()) chars.add(c.save());
        tag.put("characters", chars);
        tag.put("world", world.save());
        tag.put("diplomacy", diplomacy.save());
        tag.put("wars", wars.save());
        tag.put("lineage", lineage.save());
        tag.put("places", places.save());
        return tag;
    }

    public fr.crusadecraft.core.world.WorldState world() {
        return world;
    }

    public fr.crusadecraft.core.diplomacy.Diplomacy diplomacy() {
        return diplomacy;
    }

    public fr.crusadecraft.core.lineage.LineageState lineage() {
        return lineage;
    }

    public fr.crusadecraft.core.place.PlaceState places() {
        return places;
    }

    public fr.crusadecraft.core.war.WarState wars() {
        return wars;
    }

    public Collection<Realm> realms() {
        return Collections.unmodifiableCollection(realms.values());
    }

    public Optional<Realm> realm(UUID id) {
        return Optional.ofNullable(realms.get(id));
    }

    public Optional<Realm> realmOfRuler(UUID player) {
        for (Realm realm : realms.values()) {
            if (realm.ruler().equals(player)) return Optional.of(realm);
        }
        return Optional.empty();
    }

    public Optional<Realm> realmHolding(ColonyRef colony) {
        for (Realm realm : realms.values()) {
            if (realm.holds(colony)) return Optional.of(realm);
        }
        return Optional.empty();
    }

    public Optional<Realm> realmWithCapital(ColonyRef colony) {
        for (Realm realm : realms.values()) {
            if (realm.capital().equals(colony)) return Optional.of(realm);
        }
        return Optional.empty();
    }

    public Optional<CharacterRecord> character(CitizenRef ref) {
        return Optional.ofNullable(characters.get(ref));
    }

    /**
     * Oublie les personnages d'une colonie supprimée. MineColonies peut redonner son numéro à une nouvelle
     * colonie : sans cela, ses premiers citoyens hériteraient des souvenirs, des traits et du statut des anciens.
     */
    public void forgetColony(ColonyRef colony) {
        if (characters.keySet().removeIf(ref -> ref.colony().equals(colony))) setDirty();
    }

    public Collection<CharacterRecord> characters() {
        return Collections.unmodifiableCollection(characters.values());
    }

    public void putCharacter(CharacterRecord record) {
        characters.put(record.ref(), record);
        setDirty();
    }

    /** Le royaume dans lequel ce citoyen occupe un poste, s'il y en a un. */
    public Optional<Realm> realmEmploying(CitizenRef citizen) {
        for (Realm realm : realms.values()) {
            if (realm.postOf(citizen) != null) return Optional.of(realm);
        }
        return Optional.empty();
    }

    public void add(Realm realm) {
        realms.put(realm.id(), realm);
        setDirty();
    }

    public boolean remove(UUID id) {
        boolean removed = realms.remove(id) != null;
        if (removed) setDirty();
        return removed;
    }
}
