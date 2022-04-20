// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.command.CommandSource;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.command.argument.GameProfileArgumentType;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.MessageType;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.math.Vec3i;
import varoe.mixin.PlayerManagerAccessor;
import varoe.mixin.WorldSaveHandlerAccessor;

import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;
import static net.minecraft.util.Util.NIL_UUID;

public class Commands {
    private final Varoe varoe;

    public Commands(Varoe varoe) {
        this.varoe = varoe;
    }

    public void registerCommands(CommandDispatcher<ServerCommandSource> dispatcher) {
        LiteralArgumentBuilder<ServerCommandSource> vCmd = LiteralArgumentBuilder.literal("v");

        vCmd
                .requires((src) -> src.hasPermissionLevel(3))
                .then(literal("start")
                        .then(argument("countdown_time", IntegerArgumentType.integer())
                                .executes(this::executeStart)
                        )
                )
                .then(literal("stop")
                        .executes(this::executeStop)
                )
                .then(literal("register")
                        .then(argument("player_name", GameProfileArgumentType.gameProfile())
                                .suggests(((ctx, builder) -> {
                                    PlayerManager playerManager = ctx.getSource().getServer().getPlayerManager();

                                    return CommandSource.suggestMatching(playerManager.getPlayerList().stream()
                                            .filter(player -> !varoe.getData().registeredPlayers.containsKey(player.getGameProfile()))
                                            .map(player -> player.getName().asString()), builder);
                                }))
                                .executes(this::executeRegister)
                        )
                )
                .then(literal("team")
                        .then(literal("add")
                                .then(argument("team", StringArgumentType.word())
                                    .executes(this::executeTeamAdd)
                                )
                        )
//                        .then(literal("remove")
//                                .then(argument("team", TeamArgumentType.team())
//                                        .executes(this::executeTeamRemove)
//                                )
//                        )
//                        .then(literal("join")
//                                .then(argument("team", TeamArgumentType.team())
//                                        .then(argument("members", ScoreHolderArgumentType.scoreHolders())
//                                                .suggests(ScoreHolderArgumentType.SUGGESTION_PROVIDER)
//                                                .executes(this::executeTeamJoin)
//                                        )
//                                )
//                        )
//                        .then(literal("leave")
//                                .then(argument("members", ScoreHolderArgumentType.scoreHolders())
//                                        .suggests(ScoreHolderArgumentType.SUGGESTION_PROVIDER)
//                                        .executes(this::executeTeamLeave)
//                                )
//                        )
//                        .then(literal("list")
//                                .executes(this::executeTeamList)
//                                .then(argument("team", TeamArgumentType.team())
//                                        .executes(this::executeTeamListMembers)
//                                )
//                        )
                )
                .then(literal("unregister")
                        .then(argument("player_name", GameProfileArgumentType.gameProfile())
                                .suggests(((ctx, builder) -> CommandSource.suggestMatching(varoe.getData().registeredPlayers.keySet().stream()
                                        .filter(Objects::nonNull) // FIXME maybe don't use `orElse(new GameProfile(uuid, null))` and save current name of player in json
                                        .map(GameProfile::getName), builder)))
                                .executes(this::executeUnregister)
                        )
                )
                .then(literal("status")
                        .executes(this::executeStatus)
                )

                .then(literal("allowjoin")
                        .then(argument("allow_joining", BoolArgumentType.bool())
                                .executes(this::executeAllowJoin)
                        )
                )
                .then(literal("setalive")
                        .then(argument("player_name", GameProfileArgumentType.gameProfile())
                                .suggests(((ctx, builder) -> CommandSource.suggestMatching(varoe.getData().registeredPlayers.keySet().stream()
                                        .filter(Objects::nonNull) // FIXME maybe don't use `orElse(new GameProfile(uuid, null))` and save current name of player in json
                                        .map(GameProfile::getName), builder)))
                                .then(argument("alive", BoolArgumentType.bool())
                                        .executes(this::executeSetAlive)
                                )
                        )
                )
                .then(literal("setplaytime")
                        .then(argument("time", IntegerArgumentType.integer()) // TODO maybe use TimeArgumentType?
                                .executes(this::executeSetPlayTime)
                        )
                )
                .then(literal("setspawn")
                        .then(argument("player_name", GameProfileArgumentType.gameProfile())
                                .suggests(((ctx, builder) -> CommandSource.suggestMatching(varoe.getData().registeredPlayers.keySet().stream()
                                        .filter(Objects::nonNull) // FIXME maybe don't use `orElse(new GameProfile(uuid, null))` and save current name of player in json
                                        .map(GameProfile::getName), builder)))
                                .then(argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(this::executeSetSpawn)
                                )
                        )
                )
        ;

        dispatcher.register(vCmd);
    }

    private int executeStart(CommandContext<ServerCommandSource> ctx) {
        varoe.setCountdownEnd(Instant.now().plusSeconds(IntegerArgumentType.getInteger(ctx, "countdown_time")));

        varoe.setGameState(VaroeData.GameState.COUNTDOWN);

        ctx.getSource().sendFeedback(Text.of("Countdown started"), false);
        ctx.getSource().getServer().getPlayerManager().broadcast(Text.of(String.format("Varo starts in %d seconds", IntegerArgumentType.getInteger(ctx, "countdown_time"))), MessageType.SYSTEM, NIL_UUID);

        varoe.saveData();

        return Command.SINGLE_SUCCESS;
    }

    private int executeStop(CommandContext<ServerCommandSource> ctx) {
        varoe.setGameState(VaroeData.GameState.NOT_STARTED);
        varoe.getData().joinTimes.clear();

        for (var player : varoe.getData().registeredPlayers.values())
            player.setAlive(true);

        ctx.getSource().sendFeedback(Text.of("Varo stopped"), false);

        varoe.saveData();

        return Command.SINGLE_SUCCESS;
    }

    private int executeRegister(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var gameProfiles = GameProfileArgumentType.getProfileArgument(ctx, "player_name");
        if (gameProfiles.size() > 1)
            throw new SimpleCommandExceptionType(new LiteralText("Too many selections")).create();

        GameProfile profile = gameProfiles.iterator().next();

        if (varoe.getData().registeredPlayers.containsKey(profile))
            throw new SimpleCommandExceptionType(new LiteralText(String.format("\"%s\" isn't registered!", profile.getName()))).create();

        varoe.getData().registeredPlayers.put(profile, new VaroPlayer(profile));
        ctx.getSource().sendFeedback(Text.of(String.format("\"%s\" registered", profile.getName())), false);

        varoe.saveData();

        return Command.SINGLE_SUCCESS;
    }

    private int executeUnregister(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var gameProfiles = GameProfileArgumentType.getProfileArgument(ctx, "player_name");
        if (gameProfiles.size() > 1)
            throw new SimpleCommandExceptionType(new LiteralText("Too many selections")).create();

        GameProfile profile = gameProfiles.iterator().next();

        if (!varoe.getData().registeredPlayers.containsKey(profile))
            throw new SimpleCommandExceptionType(new LiteralText(String.format("\"%s\" isn't registered!", profile.getName()))).create();

        varoe.getData().registeredPlayers.remove(profile);
        varoe.getData().joinTimes.remove(profile);
        ctx.getSource().sendFeedback(Text.of(String.format("\"%s\" unregistered", profile.getName())), false);

        varoe.saveData();

        return Command.SINGLE_SUCCESS;
    }

    private int executeStatus(CommandContext<ServerCommandSource> ctx) {
        ctx.getSource().sendFeedback(Text.of(String.format("Current game state: %s", Varoe.getInstance().getGameState().toString())), false);

        // get player save data for coords
        var playerManager = ctx.getSource().getServer().getPlayerManager();
        var saveHandler = ((PlayerManagerAccessor)playerManager).getSaveHandler();
        File playerDataDir = ((WorldSaveHandlerAccessor)saveHandler).getPlayerDataDir();

        String[] savedPlayerIds = saveHandler.getSavedPlayerIds();

        ctx.getSource().sendFeedback(Text.of(String.format("%d player(s) registered:", varoe.getData().registeredPlayers.size())), false);
        for (var player : varoe.getData().registeredPlayers.values()) {
            var joinTime = varoe.getData().joinTimes.get(player.getProfile());

            String line = String.format(" %s: %s", player.getProfile().getName(), player.isAlive() ? "alive" : "dead");

            // TODO check if player logged in
            if (joinTime != null) {
                Duration timeLeft = varoe.getData().playTime.minus(Duration.between(joinTime, Instant.now()));

                if (!timeLeft.isNegative())
                    line += String.format(", %02d:%02d left", timeLeft.toMinutes(), timeLeft.toSecondsPart());
            }

//                if (timeLeft.isNegative())
//                    ctx.getSource().sendFeedback(Text.of(String.format(" %s: %s", player.getProfile().getName(), player.isAlive() ? "alive" : "dead")), false);
//                else
//                    ctx.getSource().sendFeedback(Text.of(String.format(" %s: %s, %02d:%02d left", player.getProfile().getName(), player.isAlive() ? "alive" : "dead", timeLeft.toMinutes(), timeLeft.toSecondsPart())), false);
//            } else {
//                ctx.getSource().sendFeedback(Text.of(String.format(" %s: %s", player.getProfile().getName(), player.isAlive() ? "alive" : "dead")), false);
//            }

            var onlinePlayer = playerManager.getPlayerList().stream().filter(e -> e.getGameProfile().equals(player.getProfile())).findAny();
            if (onlinePlayer.isPresent()) {
                var pos = onlinePlayer.get().getPos();
                line += String.format(" (x: %.2f, y: %.2f, z: %.2f)", pos.x, pos.y, pos.z);
            } else {
                if (Arrays.stream(savedPlayerIds).anyMatch(e -> e.equals(player.getProfile().getId().toString()))) {
                    // get coords from offline player
                    File file = new File(playerDataDir, player.getProfile().getId().toString() + ".dat");
                    if (file.isFile()) {
                        try {
                            var data = NbtIo.readCompressed(file);
                            var pos = data.getList("Pos", NbtElement.DOUBLE_TYPE);

                            line += String.format(" (x: %.2f, y: %.2f, z: %.2f)", pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            ctx.getSource().sendFeedback(Text.of(line), false);
        }

        return Command.SINGLE_SUCCESS;
    }

    private int executeAllowJoin(CommandContext<ServerCommandSource> ctx) {
        boolean allowed = BoolArgumentType.getBool(ctx, "allow_joining");
        final var gameState = varoe.getGameState();

        if (gameState == VaroeData.GameState.NOT_STARTED || gameState == VaroeData.GameState.JOINING_ALLOWED) {
            if (allowed) {
                if (gameState == VaroeData.GameState.JOINING_ALLOWED) {
                    ctx.getSource().sendFeedback(Text.of("Non-OPs are already allowed to join"), false);
                } else {
                    varoe.setGameState(VaroeData.GameState.JOINING_ALLOWED);
                    ctx.getSource().sendFeedback(Text.of("Non-OPs are now also allowed to join"), false);
                }
            } else {
                if (gameState != VaroeData.GameState.JOINING_ALLOWED) {
                    ctx.getSource().sendFeedback(Text.of("Non-OPs are already not allowed to join"), false);
                } else {
                    varoe.setGameState(VaroeData.GameState.NOT_STARTED);
                    ctx.getSource().sendFeedback(Text.of("Only OPs are allowed to join now"), false);
                }
            }

            varoe.saveData();

            return Command.SINGLE_SUCCESS;
        } else {
            ctx.getSource().sendError(Text.of("This setting can't be changed while Varo is started"));

            // TODO throw an exception here?
            return -Command.SINGLE_SUCCESS;
        }
    }

    private int executeSetAlive(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var gameProfiles = GameProfileArgumentType.getProfileArgument(ctx, "player_name");
        if (gameProfiles.size() > 1)
            throw new SimpleCommandExceptionType(new LiteralText("Too many selections")).create();

        GameProfile profile = gameProfiles.iterator().next();

        var player = varoe.getData().registeredPlayers.get(profile);

        if (player == null)
            throw new SimpleCommandExceptionType(new LiteralText(String.format("\"%s\" isn't registered!", profile.getName()))).create();

        player.setAlive(BoolArgumentType.getBool(ctx, "alive"));
        ctx.getSource().sendFeedback(Text.of(String.format("\"%s\" is now %s", player.getProfile().getName(), player.isAlive() ? "alive" : "dead")), false);

        varoe.saveData();

        if (!player.isAlive())
            varoe.checkForVictory();

        return Command.SINGLE_SUCCESS;
    }

    private int executeSetPlayTime(CommandContext<ServerCommandSource> ctx) {
        varoe.getData().playTime = Duration.ofSeconds(IntegerArgumentType.getInteger(ctx, "time"));
        ctx.getSource().sendFeedback(Text.of(String.format("Play time set to %ds", varoe.getData().playTime.toSeconds())), false);

        varoe.saveData();

        return Command.SINGLE_SUCCESS;
    }

    private int executeSetSpawn(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var gameProfiles = GameProfileArgumentType.getProfileArgument(ctx, "player_name");
        if (gameProfiles.size() > 1)
            throw new SimpleCommandExceptionType(new LiteralText("Too many selections")).create();

        GameProfile profile = gameProfiles.iterator().next();

        if (!varoe.getData().registeredPlayers.containsKey(profile))
            throw new SimpleCommandExceptionType(new LiteralText(String.format("\"%s\" isn't registered!", profile.getName()))).create();

        VaroPlayer player = varoe.getData().registeredPlayers.get(profile);

        var blockPos = BlockPosArgumentType.getBlockPos(ctx, "pos");

        player.setSpawnPos(new Vec3i(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
        ctx.getSource().sendFeedback(Text.of(String.format("spawn pos for \"%s\" set to %s", profile.getName(), player.getSpawnPos().toString())), false);

        varoe.saveData();

        return Command.SINGLE_SUCCESS;
    }

    private int executeTeamAdd(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
        var teamName = StringArgumentType.getString(ctx, "team");
        var scoreboard = ctx.getSource().getServer().getScoreboard();

        if (scoreboard.getTeam(teamName) != null)
            throw new SimpleCommandExceptionType(new TranslatableText("commands.team.add.duplicate")).create();
        else {
            var team = scoreboard.addTeam(teamName);
            team.setPrefix(new LiteralText(String.format("[%s] ", teamName)));
            ctx.getSource().sendFeedback(new TranslatableText("commands.team.add.success", team.getFormattedName()), true);

            return scoreboard.getTeams().size();
        }
    }

//    private int executeTeamRemove(CommandContext<ServerCommandSource> ctx) throws CommandSyntaxException {
//        ctx.getSource().sendError(new LiteralText("Use the vanilla /team command"));
//
//        return Command.SINGLE_SUCCESS;
//    }
//
//    private int executeTeamJoin(CommandContext<ServerCommandSource> ctx) {
//        ctx.getSource().sendError(new LiteralText("Use the vanilla /team command"));
//        // ScoreHolderArgumentType.getScoreboardScoreHolders(context, "members")
//        return Command.SINGLE_SUCCESS;
//    }
//
//    private int executeTeamLeave(CommandContext<ServerCommandSource> ctx) {
//        ctx.getSource().sendError(new LiteralText("Use the vanilla /team command"));
//        return Command.SINGLE_SUCCESS;
//    }
//
//    private int executeTeamList(CommandContext<ServerCommandSource> ctx) {
//        ctx.getSource().sendError(new LiteralText("Use the vanilla /team command"));
//        return Command.SINGLE_SUCCESS;
//    }
//
//    private int executeTeamListMembers(CommandContext<ServerCommandSource> ctx) {
//        ctx.getSource().sendError(new LiteralText("Use the vanilla /team command"));
//        return Command.SINGLE_SUCCESS;
//    }
}
