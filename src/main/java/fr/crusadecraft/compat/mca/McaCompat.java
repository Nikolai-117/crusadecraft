package fr.crusadecraft.compat.mca;

import forge.net.mca.entity.VillagerEntityMCA;
import forge.net.mca.entity.VillagerLike;
import forge.net.mca.entity.ai.relationship.AgeState;
import forge.net.mca.entity.ai.relationship.Gender;
import forge.net.mca.entity.ai.relationship.RelationshipState;
import forge.net.mca.server.world.data.FamilyTree;
import forge.net.mca.server.world.data.FamilyTreeNode;
import fr.crusadecraft.core.lineage.LineageState;
import fr.crusadecraft.core.lineage.Person;
import fr.crusadecraft.core.lineage.Role;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Collection;
import java.util.Optional;

/**
 * Ce qui parle vraiment à Comes Alive. Jamais chargé sans le mod : on n'y entre que par {@link McaBridge}.
 *
 * <p>Comes Alive range ses classes Forge dans le paquet {@code forge.net.mca} (un seul fichier pour Forge, Fabric et
 * Quilt). Son arbre généalogique accepte des membres qui n'existent pas encore dans le monde : on y inscrit une
 * lignée entière, et chaque villageois qu'on fait apparaître plus tard, portant le même identifiant, s'y retrouve.
 */
final class McaCompat {

    private McaCompat() {}

    static Gender gender(Person p) {
        return p.female() ? Gender.FEMALE : Gender.MALE;
    }

    /** Les âges de Comes Alive : bébé, bambin, enfant, adolescent, adulte. */
    static AgeState ageState(int years) {
        if (years < 2) return AgeState.BABY;
        if (years < 5) return AgeState.TODDLER;
        if (years < 11) return AgeState.CHILD;
        if (years < 16) return AgeState.TEEN;
        return AgeState.ADULT;
    }

    static int years(AgeState state) {
        return switch (state) {
            case BABY -> 1;
            case TODDLER -> 3;
            case CHILD -> 8;
            case TEEN -> 14;
            default -> 30;
        };
    }

    static Optional<Entity> spawn(ServerLevel level, Person person, String fullName, BlockPos pos) {
        VillagerEntityMCA v = gender(person).getVillagerType().create(level);
        if (v == null) return Optional.empty();
        v.setUUID(person.id());
        v.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360f, 0);
        // Le nom d'abord : Comes Alive n'en tire un au hasard que si le villageois n'en a pas.
        v.setName(fullName);
        v.initialize(MobSpawnType.COMMAND);
        v.setName(fullName);
        AgeState age = ageState(person.age());
        v.setAge(age.toAge());
        v.setAgeState(age);
        profession(person.role()).ifPresent(v::setProfession);
        v.addTag(McaBridge.TAG);
        if (!level.addFreshEntity(v)) return Optional.empty();
        return Optional.of(v);
    }

    private static Optional<VillagerProfession> profession(Role role) {
        if (role.profession() == null) return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(role.profession());
        return id == null ? Optional.empty() : Optional.ofNullable(ForgeRegistries.VILLAGER_PROFESSIONS.getValue(id));
    }

    static void syncTree(ServerLevel level, LineageState state, Collection<Person> people) {
        FamilyTree tree = FamilyTree.get(level);
        for (Person p : people) node(tree, state, p);
        for (Person p : people) {
            FamilyTreeNode n = node(tree, state, p);
            state.person(p.father()).ifPresent(f -> n.setFather(node(tree, state, f)));
            state.person(p.mother()).ifPresent(m -> n.setMother(node(tree, state, m)));
            Optional<Person> spouse = state.person(p.spouse());
            if (spouse.isPresent()) {
                FamilyTreeNode s = node(tree, state, spouse.get());
                n.updatePartner(s);
                s.updatePartner(n);
                n.setRelationshipState(spouse.get().alive() ? RelationshipState.MARRIED_TO_VILLAGER : RelationshipState.WIDOW);
                s.setRelationshipState(p.alive() ? RelationshipState.MARRIED_TO_VILLAGER : RelationshipState.WIDOW);
            }
        }
        tree.setDirty();
    }

    private static FamilyTreeNode node(FamilyTree tree, LineageState state, Person p) {
        String name = state.fullName(p);
        FamilyTreeNode n = tree.getOrCreate(p.id(), name, gender(p));
        n.setName(name);
        n.setGender(gender(p));
        n.setDeceased(!p.alive());
        return n;
    }

    static Optional<McaBridge.Villager> read(Entity entity) {
        if (!(entity instanceof VillagerEntityMCA v) || !(entity.level() instanceof ServerLevel level)) return Optional.empty();
        FamilyTreeNode n = FamilyTree.get(level).getOrCreate(entity);
        String name = v.getTrackedValue(VillagerLike.VILLAGER_NAME);
        boolean female = v.getGenetics().getGender() == Gender.FEMALE;
        return Optional.of(new McaBridge.Villager(entity.getUUID(), name == null || name.isBlank() ? entity.getName().getString() : name,
                female, years(v.getAgeState()), valid(n.father()), valid(n.mother()), valid(n.partner())));
    }

    private static java.util.UUID valid(java.util.UUID id) {
        return id != null && FamilyTreeNode.isValid(id) ? id : null;
    }

    static Optional<McaBridge.TreeEntry> treeEntry(ServerLevel level, java.util.UUID id) {
        return FamilyTree.get(level).getOrEmpty(id)
                .map(n -> new McaBridge.TreeEntry(n.getName(), valid(n.father()), valid(n.mother()), valid(n.partner()), n.isDeceased()));
    }

    static void rename(Entity entity, String fullName) {
        if (entity instanceof VillagerEntityMCA v) v.setName(fullName);
        if (entity.level() instanceof ServerLevel level) {
            FamilyTree.get(level).getOrEmpty(entity.getUUID()).ifPresent(n -> n.setName(fullName));
            FamilyTree.get(level).setDirty();
        }
    }
}
