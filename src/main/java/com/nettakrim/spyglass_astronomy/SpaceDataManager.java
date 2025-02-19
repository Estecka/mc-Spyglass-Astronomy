package com.nettakrim.spyglass_astronomy;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Scanner;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

import com.nettakrim.spyglass_astronomy.mixin.BiomeAccessAccessor;

import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.server.integrated.IntegratedServer;

public class SpaceDataManager {
    public static final int SAVE_FORMAT = 1;

    private long starSeed;
    private long planetSeed;
    private float yearLength;

    private File data = null;
    private Path storagePath;
    private final String fileName;

    public ArrayList<StarData> starDatas;
    public ArrayList<OrbitingBodyData> orbitingBodyDatas;

    private int changesMade;

    public SpaceDataManager(ClientWorld world) {
        //https://github.com/Johni0702/bobby/blob/d2024a2d63c63d0bccf2eafcab17dd7bf9d26710/src/main/java/de/johni0702/minecraft/bobby/FakeChunkManager.java#L86
        long seedHash = ((BiomeAccessAccessor) world.getBiomeAccess()).getSeed();
        boolean useDefault = true;
        storagePath = SpyglassAstronomyClient.client.runDirectory
                .toPath()
                .resolve(".spyglass_astronomy")
                .resolve(getCurrentWorldOrServerName().replaceAll("[\\\\/:*?\"<>|]", "_"));

        fileName = storagePath +"/"+seedHash + ".txt";

        if (Files.exists(storagePath)) {
            data = new File(fileName);
            if (data.exists()) {
                useDefault = !loadData();
            }
        }

        if (useDefault) {
            starSeed = seedHash;
            planetSeed = seedHash;
            yearLength = 8f;
        }
    }

    public boolean loadData() {
        try {
            data.createNewFile();
            Scanner scanner = new Scanner(data);
            int stage = 0;
            Decoder decoder = Base64.getDecoder();
            int starIndex = 0;
            starDatas = new ArrayList<>();
            orbitingBodyDatas = new ArrayList<>();
            changesMade = 0;
            boolean useDefault = false;
            while (scanner.hasNextLine()) {
                String s = scanner.nextLine();
                if (s.equals("---")) {
                    stage++;
                    continue;
                }
                try {
                    switch (stage) {
                        case 0 -> {
                            int format = Integer.parseInt(s.replace("Spyglass Astronomy - Format: ", ""));
                            if (format == 0) useDefault = true;
                        }
                        case 1 -> {
                            String[] seeds = s.split(" ");
                            if (seeds.length == 1) {
                                starSeed = Long.parseLong(s);
                                planetSeed = starSeed;
                            } else {
                                starSeed = Long.parseLong(seeds[0]);
                                planetSeed = Long.parseLong(seeds[1]);
                            }
                        }
                        case 2 -> {
                            String[] constellationParts = s.split(" \\| ");
                            SpyglassAstronomyClient.constellations.add(decodeConstellation(decoder, constellationParts[0], constellationParts[1]));
                        }
                        case 3 -> {
                            int starSplit = s.indexOf(' ');
                            starIndex += Integer.parseInt(s.substring(0, starSplit));
                            String starName = s.substring(starSplit + 1);
                            starDatas.add(new StarData(starIndex, starName));
                        }
                        case 4 -> {
                            int orbitingBodySplit = s.indexOf(' ');
                            int orbitingBodyIndex = Integer.parseInt(s.substring(0, orbitingBodySplit));
                            String orbitingBodyName = s.substring(orbitingBodySplit + 1);
                            orbitingBodyDatas.add(new OrbitingBodyData(orbitingBodyIndex, orbitingBodyName));
                        }
                        case 5 -> {
                            String[] parts = s.split(" ");
                            SpyglassAstronomyClient.setStarCount(Integer.parseInt(parts[0]));
                            if (parts.length > 1) setYearLength(Float.parseFloat(parts[1]));
                            else yearLength = 8;
                        }
                    }
                } catch (Exception e) {
                    SpyglassAstronomyClient.LOGGER.info("Failed to load line in stage "+stage);
                }
            }
            scanner.close();
            return !useDefault;
        } catch (IOException e) {
            SpyglassAstronomyClient.LOGGER.info("Failed to load data");
        }
        return false;
    }

    public void saveData() {
        if (changesMade == 0) return;
        try {
            if (data == null) {
                Files.createDirectories(storagePath);
                data = new File(fileName);
            }
            FileWriter writer = new FileWriter(data);
            StringBuilder s = new StringBuilder("Spyglass Astronomy - Format: "+SAVE_FORMAT);
            s.append("\n---\n");
            s.append(starSeed);
            if (planetSeed != starSeed) {
                s.append(' ');
                s.append(planetSeed);
            }
            s.append("\n---");
            Encoder encoder = Base64.getEncoder();
            for (Constellation constellation : SpyglassAstronomyClient.constellations) {
                s.append('\n');
                s.append(encodeConstellation(encoder, constellation));
            }
            s.append("\n---");
            int lastIndex = 0;
            for (Star star : SpyglassAstronomyClient.stars) {
                if (!star.isUnnamed()) {
                    s.append('\n');
                    s.append(star.index - lastIndex).append(" ").append(star.name);
                    lastIndex = star.index;
                }
            }            
            s.append("\n---");
            int index = 0;
            for (OrbitingBody orbitingBody : SpyglassAstronomyClient.orbitingBodies) {
                if (!orbitingBody.isUnnamed()) {
                    s.append('\n');
                    s.append(index).append(" ").append(orbitingBody.name);
                }
                index++;
            }   
            s.append("\n---\n");
            s.append(SpyglassAstronomyClient.getStarCount());
            s.append(" ");
            s.append(yearLength);
            s.append("\n---");

            writer.write(s.toString());
            writer.close();
            changesMade = 0;
        } catch (IOException e) {
            SpyglassAstronomyClient.LOGGER.info("Failed to save data");
        }
    }

    public static String encodeConstellation(Encoder encoder, Constellation constellation) {
        if (encoder == null) {
            encoder = Base64.getEncoder();
        }
        StringBuilder encodedConstellation = new StringBuilder(constellation.name + " | ");
        for (StarLine line : constellation.getLines()) {
            encodedConstellation.append(encodeStarLine(encoder, line.getStars()));
        }
        return encodedConstellation.toString();
    }

    private static String encodeStarLine(Encoder encoder, Star[] stars) {
        int starA = stars[0].index;
        int starB = stars[1].index;
        int combined = starA + (starB << 12);
        ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES);
        bb.putInt(combined);
        byte[] array = bb.array();
        return encoder.encodeToString(array).substring(1,6);
    }

    public static Constellation decodeConstellation(Decoder decoder, String name, String lines) {
        if (decoder == null) {
            decoder = Base64.getDecoder();
        }
        Constellation constellation = new Constellation();
        constellation.name = name;
        int continueIf = lines.length()-5;
        for (int x = 0; x <= continueIf; x+=5) {
            constellation.addLine(decodeStarLine(decoder, lines.substring(x, x+5)));
        }
        return constellation;
    }

    private static StarLine decodeStarLine(Decoder decoder, String s) {
        byte[] array = decoder.decode("A"+s+"==");
        ByteBuffer bb = ByteBuffer.allocate(Integer.BYTES);
        bb.put(array);
        bb.rewind();
        int combined = bb.getInt();
        int starB = combined >> 12;
        int starA = combined - (starB << 12);
        return new StarLine(starA, starB, false);
    }

    public long getStarSeed() {
        return starSeed;
    }

    public long getPlanetSeed() {
        return planetSeed;
    }

    public void setStarSeed(long starSeed) {
        this.starSeed = starSeed;
    }

    public void setPlanetSeed(long planetSeed) {
        this.planetSeed = planetSeed;
    }

    public float getYearLength() {
        return yearLength;
    }

    public void setYearLength(float yearLength) {
        this.yearLength = yearLength;
    }

    private static String getCurrentWorldOrServerName() {
        // https://github.com/Johni0702/bobby/blob/master/src/main/java/de/johni0702/minecraft/bobby/FakeChunkManager.java#L357
        IntegratedServer integratedServer = SpyglassAstronomyClient.client.getServer();
        if (integratedServer != null) {
            return integratedServer.getSaveProperties().getLevelName();
        }

        ServerInfo serverInfo = SpyglassAstronomyClient.client.getCurrentServerEntry();
        if (serverInfo != null) {
            if (serverInfo.isRealm()) {
                return serverInfo.name;
            }
            return serverInfo.address.replace(':', '_');
        }

        return "unknown";
    }

    public void loadStarDatas() {
        if (starDatas == null) return;
        for (StarData starData : starDatas) {
            SpyglassAstronomyClient.stars.get(starData.index).name = starData.name;
        }
        starDatas = null;
    }

    public void loadOrbitingBodyDatas() {
        if (orbitingBodyDatas == null) return;
        for (OrbitingBodyData orbitingBodyData : orbitingBodyDatas) {
            SpyglassAstronomyClient.orbitingBodies.get(orbitingBodyData.index).name = orbitingBodyData.name;
        }
        orbitingBodyDatas = null;        
    }

    public static void makeChange() {
        SpyglassAstronomyClient.spaceDataManager.changesMade++;
        SpyglassAstronomyClient.updateKnowledge();
    }

    public static int getChanges() {
        return SpyglassAstronomyClient.spaceDataManager.changesMade;
    }

    public record StarData (int index, String name) {}

    public record OrbitingBodyData (int index, String name) {}
}
