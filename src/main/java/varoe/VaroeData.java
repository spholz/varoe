// Copyright (c) 2022 Sönke Holz
// Licensed under the MIT license. (See LICENSE.txt for more details)

package varoe;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.mojang.authlib.GameProfile;
import net.minecraft.scoreboard.AbstractTeam;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3i;

import java.io.*;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class VaroeData {
    public final Map<GameProfile, VaroPlayer> registeredPlayers;
    public final Map<GameProfile, Instant> joinTimes;
    public final Map<Vec3i, AbstractTeam> teamChests;
    public final BannedItems bannedItems;
    public Duration playTime = Duration.ofSeconds(15);
    public Duration safeTime = Duration.ofSeconds(10);
    public int maxOpponentDistance = 25;

    transient private static final Gson gson = new GsonBuilder()
            // Gson doesn't provide a TypeAdapter for java.time.Instant and java.time.Duration
            .registerTypeAdapter(Instant.class,
                    new TypeAdapter<Instant>() {
                        @Override
                        public void write(JsonWriter out, Instant value) throws IOException {
                            out.value(value.toString());
                        }

                        @Override
                        public Instant read(JsonReader in) throws IOException {
                            return Instant.parse(in.nextString());
                        }
                    }.nullSafe()
            )
            .registerTypeAdapter(Duration.class,
                    new TypeAdapter<Duration>() {
                        @Override
                        public void write(JsonWriter out, Duration value) throws IOException {
                            out.value(value.toString());
                        }

                        @Override
                        public Duration read(JsonReader in) throws IOException {
                            return Duration.parse(in.nextString());
                        }
                    }.nullSafe()
            )
            .registerTypeAdapter(AbstractTeam.class,
                    new TypeAdapter<AbstractTeam>() {
                        @Override
                        public void write(JsonWriter out, AbstractTeam value) throws IOException {
                            out.value(value.getName());
                        }

                        @Override
                        public AbstractTeam read(JsonReader in) throws IOException {
                            var teamStr = in.nextString();
                            var team = Varoe.getInstance().getServer().getScoreboard().getTeam(teamStr);
                            if (team == null)
                                throw new RuntimeException(String.format("unknown team in varoe data json: \"%s\"", teamStr));
                            return team;
                        }
                    }.nullSafe()
            )
//            .registerTypeAdapter(GameProfile.class,
//                    new TypeAdapter<GameProfile>() {
//                        @Override
//                        public void write(JsonWriter out, GameProfile value) throws IOException {
//                            out.value(value.getId().toString());
//                        }
//
//                        @Override
//                        public GameProfile read(JsonReader in) throws IOException {
//                            return new GameProfile(UUID.fromString(in.nextString()), null);
//                        }
//                    }.nullSafe()
//            )
            .registerTypeAdapter(GameProfile.class,
                    new TypeAdapter<GameProfile>() {
                        @Override
                        public void write(JsonWriter out, GameProfile value) throws IOException {
                            out.value(value.getId().toString());
                        }

                        @Override
                        public GameProfile read(JsonReader in) throws IOException {
                            var uuid = UUID.fromString(in.nextString());
                            return Varoe.getInstance().getServer().getUserCache().getByUuid(uuid).orElse(new GameProfile(uuid, null));
                        }
                    }.nullSafe()
            )
            .registerTypeAdapter(Identifier.class,
                    new TypeAdapter<Identifier>() {
                        @Override
                        public void write(JsonWriter out, Identifier value) throws IOException {
                            out.value(String.format(value.toString()));
                        }

                        @Override
                        public Identifier read(JsonReader in) throws IOException {
                            return new Identifier(in.nextString());
                        }
                    }.nullSafe()
            )
            .excludeFieldsWithModifiers(Modifier.TRANSIENT | Modifier.STATIC)
            .enableComplexMapKeySerialization() // FIXME maybe high performance impact?
            .serializeNulls()
            .setPrettyPrinting()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create();

    public enum GameState {
        // project not yet started
        NOT_STARTED,
        // joining is allowed in this state
        JOINING_ALLOWED,
        // varo is about to start
        COUNTDOWN,
        STARTED,
    }

    public GameState state = GameState.NOT_STARTED;

    public VaroeData() {
        registeredPlayers = new HashMap<>();
        joinTimes = new HashMap<>();
        teamChests = new HashMap<>();
        bannedItems = new BannedItems();
    }

    public void save(File file) throws IOException {
        assert file.createNewFile();

        final var writer = new FileWriter(file);

        writer.write(gson.toJson(this));

        writer.close();
    }

    public static VaroeData load(File file) throws IOException {
        final var reader = new BufferedReader(new FileReader(file));

        return gson.fromJson(reader, VaroeData.class);
    }
}
