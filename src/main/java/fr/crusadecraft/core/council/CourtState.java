package fr.crusadecraft.core.council;

import fr.crusadecraft.core.ColonyRef;
import fr.crusadecraft.core.character.CitizenRef;
import fr.crusadecraft.core.character.Post;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * La vie de la cour d'un royaume : où siège le conseil, où travaillent les maires, quelles tâches sont
 * confiées, quelles requêtes attendent une réponse.
 */
public final class CourtState {
    private ResourceLocation tableDimension;
    private BlockPos tablePos;
    private final Map<ColonyRef, BlockPos> mayorDesks = new LinkedHashMap<>();
    private final Map<Post, CouncilTask> tasks = new EnumMap<>(Post.class);
    /** Jamais traité / jamais tenue. Pas -1 : un monde neuf commence au jour 0, et la veille serait -1. */
    public static final long NEVER = Long.MIN_VALUE;

    private long lastProcessedDay = NEVER;
    private long meetingBonusDay = NEVER;
    private long conveneUntilTick = -1;
    private long lastConveneDay = -1;
    private final List<Request> requests = new ArrayList<>();
    private final Map<String, Long> cooldowns = new HashMap<>();
    /** Cible d'une tâche (royaume, province), sous forme de texte. */
    private final Map<Post, String> taskTargets = new EnumMap<>(Post.class);
    private int claimProgress;

    public boolean hasTable() { return tablePos != null; }
    public ResourceLocation tableDimension() { return tableDimension; }
    public BlockPos tablePos() { return tablePos; }
    public Map<ColonyRef, BlockPos> mayorDesks() { return mayorDesks; }
    public long lastProcessedDay() { return lastProcessedDay; }
    public long meetingBonusDay() { return meetingBonusDay; }
    public long conveneUntilTick() { return conveneUntilTick; }
    public long lastConveneDay() { return lastConveneDay; }
    public List<Request> requests() { return requests; }

    public void setTable(ResourceLocation dimension, BlockPos pos) {
        this.tableDimension = dimension;
        this.tablePos = pos == null ? null : pos.immutable();
    }

    public void clearTable() {
        this.tableDimension = null;
        this.tablePos = null;
    }

    public void setLastProcessedDay(long day) { this.lastProcessedDay = day; }
    public void setMeetingBonusDay(long day) { this.meetingBonusDay = day; }

    public void convene(long untilTick, long today) {
        this.conveneUntilTick = untilTick;
        this.lastConveneDay = today;
    }

    /** Tâche confiée au titulaire d'un poste, ou la tâche par défaut. */
    public CouncilTask task(Post post) {
        CouncilTask t = tasks.get(post);
        return t != null && t.post() == post ? t : CouncilTask.defaultFor(post);
    }

    public void setTask(Post post, CouncilTask task) {
        if (task != null && task.post() == post) tasks.put(post, task);
    }

    public String taskTarget(Post post) { return taskTargets.get(post); }

    public void setTaskTarget(Post post, String target) {
        if (target == null) taskTargets.remove(post);
        else taskTargets.put(post, target);
    }

    public int claimProgress() { return claimProgress; }
    public void setClaimProgress(int progress) { this.claimProgress = Math.max(0, progress); }

    public boolean onCooldown(RequestType type, CitizenRef from, long today) {
        Long last = cooldowns.get(cooldownKey(type, from));
        return last != null && today - last < type.cooldownDays();
    }

    public void markRequested(RequestType type, CitizenRef from, long today) {
        cooldowns.put(cooldownKey(type, from), today);
    }

    private static String cooldownKey(RequestType type, CitizenRef from) {
        return type.name() + "|" + from;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        if (tablePos != null) {
            t.putString("tableDim", tableDimension.toString());
            t.put("tablePos", NbtUtils.writeBlockPos(tablePos));
        }
        ListTag desks = new ListTag();
        for (Map.Entry<ColonyRef, BlockPos> e : mayorDesks.entrySet()) {
            CompoundTag d = e.getKey().save();
            d.put("pos", NbtUtils.writeBlockPos(e.getValue()));
            desks.add(d);
        }
        t.put("desks", desks);
        CompoundTag taskTag = new CompoundTag();
        for (Map.Entry<Post, CouncilTask> e : tasks.entrySet()) taskTag.putString(e.getKey().name(), e.getValue().name());
        t.put("tasks", taskTag);
        t.putLong("lastDay", lastProcessedDay);
        t.putLong("bonusDay", meetingBonusDay);
        t.putLong("conveneUntil", conveneUntilTick);
        t.putLong("conveneDay", lastConveneDay);
        ListTag req = new ListTag();
        for (Request r : requests) req.add(r.save());
        t.put("requests", req);
        CompoundTag cd = new CompoundTag();
        for (Map.Entry<String, Long> e : cooldowns.entrySet()) cd.putLong(e.getKey(), e.getValue());
        t.put("cooldowns", cd);
        CompoundTag targets = new CompoundTag();
        for (Map.Entry<Post, String> e : taskTargets.entrySet()) targets.putString(e.getKey().name(), e.getValue());
        t.put("targets", targets);
        t.putInt("claimProgress", claimProgress);
        return t;
    }

    public static CourtState load(CompoundTag t) {
        CourtState s = new CourtState();
        if (t.contains("tablePos")) {
            s.tableDimension = ResourceLocation.tryParse(t.getString("tableDim"));
            s.tablePos = NbtUtils.readBlockPos(t.getCompound("tablePos"));
            if (s.tableDimension == null) s.tablePos = null;
        }
        ListTag desks = t.getList("desks", Tag.TAG_COMPOUND);
        for (int i = 0; i < desks.size(); i++) {
            CompoundTag d = desks.getCompound(i);
            s.mayorDesks.put(ColonyRef.load(d), NbtUtils.readBlockPos(d.getCompound("pos")));
        }
        CompoundTag taskTag = t.getCompound("tasks");
        for (String key : taskTag.getAllKeys()) {
            Post post = Post.byName(key);
            CouncilTask task = CouncilTask.byName(taskTag.getString(key));
            if (post != null && task != null) s.setTask(post, task);
        }
        s.lastProcessedDay = t.contains("lastDay") ? t.getLong("lastDay") : NEVER;
        s.meetingBonusDay = t.contains("bonusDay") ? t.getLong("bonusDay") : NEVER;
        s.conveneUntilTick = t.contains("conveneUntil") ? t.getLong("conveneUntil") : -1;
        s.lastConveneDay = t.contains("conveneDay") ? t.getLong("conveneDay") : -1;
        ListTag req = t.getList("requests", Tag.TAG_COMPOUND);
        for (int i = 0; i < req.size(); i++) {
            Request r = Request.load(req.getCompound(i));
            if (r != null) s.requests.add(r);
        }
        CompoundTag cd = t.getCompound("cooldowns");
        for (String key : cd.getAllKeys()) s.cooldowns.put(key, cd.getLong(key));
        CompoundTag targets = t.getCompound("targets");
        for (String key : targets.getAllKeys()) {
            Post post = Post.byName(key);
            if (post != null) s.taskTargets.put(post, targets.getString(key));
        }
        s.claimProgress = t.getInt("claimProgress");
        return s;
    }
}
