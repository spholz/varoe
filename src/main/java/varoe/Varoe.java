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
import net.minecraft.world.GameMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

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
                                player.sendMessage(Text.of("An opponent is nearby. You won't get get kicked currently."), true);

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
        var players = server.getPlayerManager().getPlayerList().iterator();

        double d;
        do {
            PlayerEntity player;

            do {
                if (!players.hasNext())
                    return false;

                player = players.next();
                if (player == self) {
                    if (!players.hasNext())
                        return false;

                    player = players.next();
                }
            } while(!EntityPredicates.EXCEPT_SPECTATOR.test(player) && !EntityPredicates.VALID_LIVING_ENTITY.test(player));

            d = player.squaredDistanceTo(self);
        } while(!(range < 0.0D) && !(d < range * range));

        return true;
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
                aliveTeams.add(team);
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
