// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe;

import com.mojang.authlib.GameProfile;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.MessageType;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.WorldSavePath;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Predicate;

import static net.minecraft.util.Util.NIL_UUID;

public class Varoe {
    private static final Logger LOGGER = LoggerFactory.getLogger("varoe");

    private Instant countdownEnd;

    private VaroeData data;
    private final Commands commands;

    private MinecraftServer server;

    private static Varoe INSTANCE;

    public Varoe() {
        INSTANCE = this;

        ServerLifecycleEvents.SERVER_STARTING.register(server -> this.server = server);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            maybeLoadData();
            registerEventListeners();
        });

        commands = new Commands(this);


        registerCommands();
    }

    public static Varoe getInstance() {
        return INSTANCE;
    }

    public static Logger getLogger() {
        return LOGGER;
    }

    public Map<GameProfile, VaroPlayer> getRegisteredPlayers() {
        if (data == null)
            return null;

        return data.registeredPlayers;
    }

    public VaroeData.GameState getGameState() {
        if (data == null)
            return null;

        return data.state;
    }

    public void setGameState(VaroeData.GameState state) {
        data.state = state;
    }

    public Map<Vec3i, AbstractTeam> getTeamChests() {
        if (data == null)
            return null;

        return data.teamChests;
    }

    public void addTeamChest(BlockPos pos, AbstractTeam team) {
        getTeamChests().put(pos, team);
        saveData();
    }

    public void deleteTeamChest(BlockPos pos) {
        getTeamChests().remove(pos);
        saveData();
    }

    public void setCountdownEnd(Instant countdownEnd) {
        this.countdownEnd = countdownEnd;
    }

    public VaroeData getData() {
        return data;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public BannedItems getBannedItems() {
        return data.bannedItems;
    }

    public void registerEventListeners() {
        ServerTickEvents.END_SERVER_TICK.register((server) -> {
            if (data.state == VaroeData.GameState.COUNTDOWN) {
                Duration timeLeft = Duration.between(Instant.now(), countdownEnd);

                if (timeLeft.isNegative() || timeLeft.isZero()) {
                    // start the game
                    data.state = VaroeData.GameState.STARTED;

                    // set all join times to Instant.now() and change to survival mode
                    Instant joinTime = Instant.now();

                    data.joinTimes.clear();

                    for (var player : server.getPlayerManager().getPlayerList()) {
                        data.joinTimes.put(player.getGameProfile(), joinTime);
                        if (data.registeredPlayers.containsKey(player.getGameProfile()))
                            player.changeGameMode(GameMode.SURVIVAL);
                    }

                    // set difficulty to normal
                    server.setDifficulty(Difficulty.NORMAL, true);

                    for (var p : server.getPlayerManager().getPlayerList()) {
                        if (server.getPlayerManager().isOperator(p.getGameProfile()))
                            p.sendMessage(Text.of("Difficulty set to normal"), false);
                    }

                    LOGGER.info("Difficulty set to normal");

                    server.getPlayerManager().broadcast(Text.of("Varo starts NOW!"), MessageType.SYSTEM, NIL_UUID);

                    saveData();

                    var registeredPlayersNotOnline = data.registeredPlayers.keySet().stream().filter(
                            profile -> server.getPlayerManager().getPlayerList().stream().noneMatch(e -> e.getGameProfile().equals(profile))
                    ).map(GameProfile::getName).toArray();

                    for (var p : server.getPlayerManager().getPlayerList()) {
                        if (server.getPlayerManager().isOperator(p.getGameProfile()))
                            p.sendMessage(Text.of(String.format("Registered players not online: %s", Arrays.toString(registeredPlayersNotOnline))), false);
                    }

                    LOGGER.info("Registered players not online: {}", Arrays.toString(registeredPlayersNotOnline));

                    return;
                }

                if (server.getTicks() % 20 == 0)
                    server.getPlayerManager().broadcast(Text.of(String.format("Varo starts in %d seconds", timeLeft.toSeconds())), MessageType.SYSTEM, NIL_UUID);

            } else if (data.state == VaroeData.GameState.STARTED) {
                var playersToDisconnect = new ArrayList<ServerPlayerEntity>();

                for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                    if (data.registeredPlayers.containsKey(player.getGameProfile()) && data.joinTimes.containsKey(player.getGameProfile())) {
                        Duration timeLeft = data.playTime.minus(Duration.between(data.joinTimes.get(player.getGameProfile()), Instant.now()));

                        if (timeLeft.isNegative()) {
                            if (!isOpponentInRange(player, data.maxOpponentDistance))
                                playersToDisconnect.add(player);
                            else
                                player.sendMessage(new LiteralText("An opponent is nearby. You won't get get kicked currently.").formatted(Formatting.BLACK), true);

                            continue;
                        }

                        MutableText response = new LiteralText(String.format("%02d:%02d left", timeLeft.toMinutes(), timeLeft.toSecondsPart()));
                        response.formatted(Formatting.BLACK);

                        player.sendMessage(response, true);
                    }
                }

                for (var player : playersToDisconnect)
                    player.networkHandler.disconnect(Text.of("Your time is over"));
            }
        });
    }

    public boolean isOpponentInRange(PlayerEntity self, double range) {
        var scoreboard = server.getScoreboard();
        var playerManager = server.getPlayerManager();

        final Predicate<PlayerEntity> isOfDifferentTeam = (player) -> {
            var selfTeam = scoreboard.getPlayerTeam(self.getGameProfile().getName());
            var otherTeam = scoreboard.getPlayerTeam(player.getGameProfile().getName());

            if (!getRegisteredPlayers().containsKey(player.getGameProfile()))
                return false;

            if (playerManager.isOperator(player.getGameProfile()))
                return false;

            if (selfTeam == null || otherTeam == null)
                return false;

            return !selfTeam.equals(otherTeam);
        };

        var playersInRange = server.getPlayerManager().getPlayerList().stream()
                .filter((player) -> !player.equals(self))
                .filter(EntityPredicates.EXCEPT_SPECTATOR)
                .filter(EntityPredicates.VALID_LIVING_ENTITY)
                .filter(isOfDifferentTeam)
                .filter((player) -> player.squaredDistanceTo(self) < range * range);

        return playersInRange.findAny().isPresent();
    }

    private void registerCommands() {
        CommandRegistrationCallback.EVENT.register((dispatcher, dedicated) -> commands.registerCommands(dispatcher));
    }

    public void onPlayerJoin(ServerPlayerEntity player) {
        if (data.registeredPlayers.containsKey(player.getGameProfile())) {
            VaroPlayer varoPlayer = data.registeredPlayers.get(player.getGameProfile());

            if (data.state != VaroeData.GameState.STARTED) {
                // force the player to their spawn pos
                var spawnPos = varoPlayer.getSpawnPos();

                if (spawnPos != null)
                    player.teleport(spawnPos.getX() + 0.5, spawnPos.getY(), spawnPos.getZ() + 0.5);

                return;
            }


            if (data.joinTimes.containsKey(player.getGameProfile())) {
                Duration timeLeft = data.playTime.minus(Duration.between(data.joinTimes.get(player.getGameProfile()), Instant.now()));

                if (!timeLeft.isNegative())
                    return;
            }

            data.joinTimes.put(player.getGameProfile(), Instant.now());

            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, (int)data.safeTime.toSeconds() * 20, 4, false, false));

            saveData();
        }
    }

    public void saveData() {
//        try {
            File file = server.getSavePath(WorldSavePath.ROOT).resolve("varoe_data.json").toFile();
            File backupFile = server.getSavePath(WorldSavePath.ROOT).resolve("varoe_data.json.bak").toFile();

            if (file.exists()) {
                if (backupFile.exists())
                    assert backupFile.delete();

                assert file.renameTo(backupFile);
            }

            try {
                data.save(file);
            } catch (IOException e) {
                LOGGER.error("Failed to save \"{}\": {}", file.getAbsolutePath(), e.getMessage());
            }
//        } catch (Exception e) {
//            LOGGER.error("Unexpected exception caught while trying to save Varo data", e);
//        }
    }

    private void maybeLoadData() {
        File file = server.getSavePath(WorldSavePath.ROOT).resolve("varoe_data.json").toFile();

        if (file.exists()) {
            try {
                data = VaroeData.load(file);

                if (data == null)
                    data = new VaroeData();
            } catch (IOException e) {
                LOGGER.error("Failed to load \"{}\": {}", file.getAbsolutePath(), e.getMessage());
            }
        } else {
            data = new VaroeData();
        }
    }

    public void checkForVictory() {
        var aliveTeams = new HashSet<AbstractTeam>();
        var scoreboard = Varoe.getInstance().getServer().getScoreboard();

        for (var e : Varoe.getInstance().getRegisteredPlayers().entrySet()) {
            if (e.getValue().isAlive()) {
                var team = scoreboard.getPlayerTeam(e.getValue().getProfile().getName());

                if (team != null)
                    aliveTeams.add(team);
                else
                    LOGGER.error("Player \"{}\" is alive but has no team! All registered players must be in a team!", e.getValue().getProfile().getName());
            }
        }

        if (aliveTeams.size() == 1) {
            for (String name : aliveTeams.iterator().next().getPlayerList()) {
                Optional.ofNullable(Varoe.getInstance().getServer().getPlayerManager().getPlayer(name)).ifPresent(p -> {
                    p.networkHandler.sendPacket(new SubtitleS2CPacket(new LiteralText("You won Varo!").formatted(Formatting.GREEN)));
                    p.networkHandler.sendPacket(new TitleS2CPacket(new LiteralText("Congratulations").formatted(Formatting.GOLD)));
                });
            }
        }
    }
}
