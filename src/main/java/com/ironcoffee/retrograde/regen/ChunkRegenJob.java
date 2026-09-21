package com.ironcoffee.retrograde.regen;

import com.ironcoffee.retrograde.Main;
import com.ironcoffee.retrograde.chunk.ChunkTracker;
import com.ironcoffee.retrograde.retrogen.RetrogenIntegration;
import com.ironcoffee.retrograde.retrogen.RetrogenService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Runs one regen / undo / retrogen pass over a whole selection of chunks,
 * as a phase machine so the screen watching it has something real to show.
 *
 * The awkward part is that {@link ChunkRegenService} can only touch a chunk
 * that isn't loaded, and the player is what keeps chunks loaded. Rather
 * than telling people to walk away and hope (which is what the old
 * click-a-chunk flow did), the job moves the player out of range itself,
 * waits for the chunks to actually leave memory, does the work, and puts
 * them back exactly where they were. That's the whole reason for the
 * MOVING_PLAYER / WAITING_FOR_UNLOAD phases.
 *
 * Retrogen is the exception: another mod's retrogen command re-runs
 * features against the live chunk, so it wants chunks loaded, not
 * unloaded. That mode skips straight to WORKING and never moves anyone.
 *
 * Threading: everything in here runs on the server thread. The client
 * screen drives it by calling {@link #pump()} once per client tick, which
 * posts a single step onto the server thread - the same
 * MinecraftServer#execute hop the map screen already uses to read chunk
 * data. Singleplayer-only, like the rest of this mod; a dedicated server
 * would need a packet instead of reading these fields directly.
 *
 * The counters below are written on the server thread and read on the
 * client thread every frame, hence volatile.
 */
public final class ChunkRegenJob {
	/** How many chunks to handle per step, so one tick can't stall on a huge selection. */
	private static final int CHUNKS_PER_STEP = 4;
	/** Give up waiting for chunks to unload after 30s and report the stragglers as skipped. */
	private static final int UNLOAD_TIMEOUT_TICKS = 600;
	/** Extra chunks beyond view distance to put between the player and the work area. */
	private static final int STAGING_MARGIN_CHUNKS = 4;
	/** pump() is driven by the progress screen's client tick, so a step is a tick. */
	private static final int STEPS_PER_SECOND = 20;
	/**
	 * Move out, wait for the unload, move back. A guess, because the wait
	 * depends on autosave timing - but a defensible one, and the alternative
	 * is a preview screen that tells you a 20-chunk regen takes under a
	 * second and then makes you watch a spinner for ten.
	 */
	private static final int STAGING_SECONDS = 6;

	private static final Map<MinecraftServer, ChunkRegenJob> ACTIVE = new ConcurrentHashMap<>();

	public enum Mode {
		REGENERATE,
		UNDO,
		RETROGEN
	}

	public enum Phase {
		MOVING_PLAYER,
		WAITING_FOR_UNLOAD,
		WORKING,
		RETURNING_PLAYER,
		FINISHED
	}

	private final MinecraftServer server;
	private final ServerLevel level;
	private final UUID playerId;
	private final Mode mode;
	private final List<ChunkPos> targets;
	private final RetrogenIntegration integration;

	private final AtomicBoolean stepQueued = new AtomicBoolean();

	private volatile Phase phase;
	private volatile int index;
	private volatile int succeeded;
	private volatile int skipped;
	private volatile int failed;
	private volatile boolean cancelRequested;
	private volatile ChunkPos currentChunk;
	private volatile int waitTicks;

	// Where the player was before we moved them, so they can be put back.
	private boolean staged;
	private double returnX, returnY, returnZ;
	private float returnYRot, returnXRot;
	private boolean wasInvulnerable;
	private boolean wasNoGravity;

	private ChunkRegenJob(MinecraftServer server, ServerLevel level, UUID playerId, Mode mode,
			List<ChunkPos> targets, RetrogenIntegration integration) {
		this.server = server;
		this.level = level;
		this.playerId = playerId;
		this.mode = mode;
		this.targets = List.copyOf(targets);
		this.integration = integration;
		this.phase = mode == Mode.RETROGEN ? Phase.WORKING : Phase.MOVING_PLAYER;
	}

	/**
	 * Starts a job, or returns null if one is already running for this
	 * server - two jobs moving the same player around would fight over
	 * where to put them back.
	 */
	public static ChunkRegenJob start(MinecraftServer server, ServerLevel level, UUID playerId, Mode mode,
			List<ChunkPos> targets, RetrogenIntegration integration) {
		ChunkRegenJob job = new ChunkRegenJob(server, level, playerId, mode, targets, integration);
		return ACTIVE.putIfAbsent(server, job) == null ? job : null;
	}

	public static ChunkRegenJob active(MinecraftServer server) {
		return ACTIVE.get(server);
	}

	/**
	 * Roughly how long a job over this many chunks will run, for the preview
	 * screen to quote before anyone commits to it. The pacing lives here
	 * rather than in the screen because it's this class that decides it -
	 * CHUNKS_PER_STEP chunks per pump(), one pump per client tick.
	 */
	public static int estimateSeconds(Mode mode, int chunkCount) {
		int working = (int) Math.ceil(chunkCount / (double) (CHUNKS_PER_STEP * STEPS_PER_SECOND));
		// Retrogen hands each chunk to another mod in place, so it skips the
		// whole move-out-and-wait-for-unload dance that regen and undo need.
		int staging = mode == Mode.RETROGEN ? 0 : STAGING_SECONDS;
		return Math.max(1, working + staging);
	}

	/**
	 * Asks for one step on the server thread. Called from the client tick;
	 * the flag stops a slow step from piling up a queue of more steps
	 * behind it.
	 */
	public void pump() {
		if (phase == Phase.FINISHED || !stepQueued.compareAndSet(false, true)) {
			return;
		}
		server.execute(this::step);
	}

	public void requestCancel() {
		cancelRequested = true;
	}

	public Mode mode() {
		return mode;
	}

	public Phase phase() {
		return phase;
	}

	public boolean isFinished() {
		return phase == Phase.FINISHED;
	}

	public boolean isCancelled() {
		return cancelRequested;
	}

	public int total() {
		return targets.size();
	}

	public int processed() {
		return index;
	}

	public int succeeded() {
		return succeeded;
	}

	public int skipped() {
		return skipped;
	}

	public int failed() {
		return failed;
	}

	public ChunkPos currentChunk() {
		return currentChunk;
	}

	public List<ChunkPos> targets() {
		return targets;
	}

	/** 0..1 across the work phase, or 0 while still getting the player clear. */
	public float progress() {
		return targets.isEmpty() ? 1.0F : (float) index / targets.size();
	}

	/** Seconds spent waiting for chunks to unload, for the screen to show. */
	public int waitSeconds() {
		return waitTicks / 20;
	}

	private void step() {
		try {
			switch (phase) {
				case MOVING_PLAYER -> stepMovePlayer();
				case WAITING_FOR_UNLOAD -> stepWaitForUnload();
				case WORKING -> stepWork();
				case RETURNING_PLAYER -> stepReturnPlayer();
				case FINISHED -> {}
			}
		} catch (Exception e) {
			Main.LOGGER.error("[{}] Chunk job failed in phase {}", Main.MOD_ID, phase, e);
			failed += Math.max(0, targets.size() - index);
			index = targets.size();
			// Whatever went wrong, the one thing we must not skip is putting
			// the player back - unless that's what already threw.
			if (phase == Phase.RETURNING_PLAYER) {
				finish();
			} else {
				phase = Phase.RETURNING_PLAYER;
			}
		} finally {
			stepQueued.set(false);
		}
	}

	private void stepMovePlayer() {
		if (cancelRequested) {
			skipAllRemaining();
			finish();
			return;
		}
		ServerPlayer player = player();
		if (player == null || !needsStaging(player)) {
			// Nobody in range to move (or nobody online at all) - the chunks
			// may still be loaded for other reasons, which the work phase
			// reports honestly rather than forcing.
			phase = Phase.WAITING_FOR_UNLOAD;
			return;
		}

		returnX = player.getX();
		returnY = player.getY();
		returnZ = player.getZ();
		returnYRot = player.getYRot();
		returnXRot = player.getXRot();
		wasInvulnerable = readInvulnerable(player);
		wasNoGravity = player.isNoGravity();

		// Parked above the build height with gravity and damage off, so the
		// staging spot can be over an ocean or a lava lake without it
		// mattering, and so we never have to generate terrain just to find
		// somewhere safe to stand.
		setInvulnerable(player, true);
		player.setNoGravity(true);
		teleport(player, stagingX(player), stagingY(), player.getZ(), returnYRot, returnXRot);
		player.resetFallDistance();
		staged = true;
		phase = Phase.WAITING_FOR_UNLOAD;
	}

	private void stepWaitForUnload() {
		if (cancelRequested) {
			skipAllRemaining();
			phase = staged ? Phase.RETURNING_PLAYER : Phase.FINISHED;
			if (phase == Phase.FINISHED) finish();
			return;
		}
		waitTicks++;
		boolean allClear = true;
		for (ChunkPos pos : targets) {
			if (ChunkRegenService.isLoaded(level, pos)) {
				allClear = false;
				break;
			}
		}
		if (allClear || waitTicks > UNLOAD_TIMEOUT_TICKS) {
			// On timeout we don't force anything: whatever is still loaded
			// gets reported as skipped by the work phase below.
			phase = Phase.WORKING;
		}
	}

	private void stepWork() {
		if (cancelRequested) {
			skipAllRemaining();
		}
		for (int i = 0; i < CHUNKS_PER_STEP && index < targets.size(); i++) {
			ChunkPos pos = targets.get(index);
			currentChunk = pos;
			apply(pos);
			index++;
		}
		if (index >= targets.size()) {
			currentChunk = null;
			if (staged) {
				phase = Phase.RETURNING_PLAYER;
			} else {
				finish();
			}
		}
	}

	private void apply(ChunkPos pos) {
		switch (mode) {
			case REGENERATE -> {
				ChunkRegenService.Result result = ChunkRegenService.regenerate(level, pos);
				record(result);
				if (result == ChunkRegenService.Result.OK) {
					// The chunk's player changes are gone as of the next load,
					// so the touched mark that was recording them should be too.
					ChunkTracker.forServer(server).clearTouched(level, pos);
				}
			}
			case UNDO -> {
				ChunkRegenService.Result result = ChunkRegenService.undo(level, pos);
				record(result);
				if (result == ChunkRegenService.Result.OK) {
					// Restoring the snapshot brings back whatever was in the
					// chunk when it was regenerated. We don't record whether it
					// was marked touched back then, so this re-marks it either
					// way - erring toward "be careful with this one", which is
					// the same stance the tracker takes everywhere else.
					ChunkTracker.forServer(server).markTouched(level, pos);
				}
			}
			case RETROGEN -> {
				ServerPlayer player = player();
				if (player == null || integration == null) {
					failed++;
					return;
				}
				RetrogenService.run(server, player, integration, pos);
				succeeded++;
			}
		}
	}

	private void record(ChunkRegenService.Result result) {
		switch (result) {
			case OK -> succeeded++;
			case CHUNK_LOADED, NOTHING_TO_UNDO -> skipped++;
			case IO_ERROR -> failed++;
		}
	}

	private void stepReturnPlayer() {
		ServerPlayer player = player();
		if (player != null && staged) {
			// No flush/wait needed before pulling the player back: chunk
			// loads and our writes both go through the level's single IO
			// worker in submission order, so the reload that follows this
			// teleport can't overtake the write that queued before it.
			teleport(player, returnX, returnY, returnZ, returnYRot, returnXRot);
			setInvulnerable(player, wasInvulnerable);
			player.setNoGravity(wasNoGravity);
			player.resetFallDistance();
		}
		staged = false;
		finish();
	}

	private void skipAllRemaining() {
		skipped += Math.max(0, targets.size() - index);
		index = targets.size();
		currentChunk = null;
	}

	private void finish() {
		phase = Phase.FINISHED;
		ACTIVE.remove(server, this);
	}

	private ServerPlayer player() {
		return server.getPlayerList().getPlayer(playerId);
	}

	/** True if any target is close enough to the player to still be loaded. */
	private boolean needsStaging(ServerPlayer player) {
		int clearance = clearanceChunks();
		ChunkPos playerChunk = chunkOf(player);
		for (ChunkPos pos : targets) {
			if (playerChunk.getChessboardDistance(pos) <= clearance) {
				return true;
			}
		}
		return false;
	}

	private int clearanceChunks() {
		return server.getPlayerList().getViewDistance() + STAGING_MARGIN_CHUNKS;
	}

	/**
	 * Straight out along +X from the far edge of the selection, far enough
	 * that every target is outside view distance. Keeping the player's own Z
	 * means the shortest possible hop, and chessboard distance only needs one
	 * axis to be clear.
	 */
	private double stagingX(ServerPlayer player) {
		int maxChunkX = Integer.MIN_VALUE;
		for (ChunkPos pos : targets) {
			maxChunkX = Math.max(maxChunkX, chunkX(pos));
		}
		int stagingChunkX = maxChunkX + clearanceChunks() + 1;
		// If the player is already further out than that, don't drag them back in.
		int playerChunkX = chunkX(chunkOf(player));
		return Math.max(stagingChunkX, playerChunkX) * 16.0 + 8.0;
	}

	private double stagingY() {
		//? if >=26 {
		/*return level.getMaxY() + 32.0;
		*///?} else {
		return level.getMaxBuildHeight() + 32.0;
		//?}
	}

	private void teleport(ServerPlayer player, double x, double y, double z, float yRot, float xRot) {
		//? if >=26 {
		/*player.teleportTo(level, x, y, z, java.util.Set.of(), yRot, xRot, false);
		*///?} else {
		player.teleportTo(level, x, y, z, yRot, xRot);
		//?}
	}

	// 26.3 split invulnerability in two: the old permanent flag became
	// setPermanentlyInvulnerable, leaving isInvulnerable() to mean "immune
	// right now" (which includes the temporary post-hit window). Reading
	// isInvulnerable() to save-and-restore would therefore risk leaving the
	// player permanently invulnerable just because we caught them mid
	// damage-cooldown, so read the permanent flag specifically.
	private static boolean readInvulnerable(ServerPlayer player) {
		//? if >=26.3 {
		/*return player.isPermanentlyInvulnerable();
		*///?} else {
		return player.isInvulnerable();
		//?}
	}

	private static void setInvulnerable(ServerPlayer player, boolean invulnerable) {
		//? if >=26.3 {
		/*player.setPermanentlyInvulnerable(invulnerable);
		*///?} else {
		player.setInvulnerable(invulnerable);
		//?}
	}

	/** Human-readable one-liner for the progress screen. */
	public Component statusLine() {
		return switch (phase) {
			case MOVING_PLAYER -> Component.translatable("gui.retrograde.job.phase.moving");
			case WAITING_FOR_UNLOAD -> Component.translatable("gui.retrograde.job.phase.unloading", waitSeconds());
			case WORKING -> {
				ChunkPos pos = currentChunk;
				yield pos == null
					? Component.translatable("gui.retrograde.job.phase.working")
					: Component.translatable("gui.retrograde.job.phase.working_chunk", chunkX(pos), chunkZ(pos));
			}
			case RETURNING_PLAYER -> Component.translatable("gui.retrograde.job.phase.returning");
			case FINISHED -> cancelRequested
				? Component.translatable("gui.retrograde.job.phase.cancelled")
				: Component.translatable("gui.retrograde.job.phase.done");
		};
	}

	/** Title for the progress screen, per mode. */
	public Component titleLine() {
		return switch (mode) {
			case REGENERATE -> Component.translatable("gui.retrograde.job.title.regen", targets.size());
			case UNDO -> Component.translatable("gui.retrograde.job.title.undo", targets.size());
			case RETROGEN -> Component.translatable("gui.retrograde.job.title.retrogen",
				integration == null ? "?" : integration.displayName(), targets.size());
		};
	}

	/** The chunks this job actually changed, for the map to redraw. */
	public List<ChunkPos> touchedByJob() {
		return new ArrayList<>(targets);
	}

	private ChunkPos chunkOf(ServerPlayer player) {
		//? if >=26 {
		/*return ChunkPos.containing(player.blockPosition());
		*///?} else {
		return new ChunkPos(player.blockPosition());
		//?}
	}

	private static int chunkX(ChunkPos pos) {
		//? if >=26 {
		/*return pos.x();
		*///?} else {
		return pos.x;
		//?}
	}

	private static int chunkZ(ChunkPos pos) {
		//? if >=26 {
		/*return pos.z();
		*///?} else {
		return pos.z;
		//?}
	}
}
