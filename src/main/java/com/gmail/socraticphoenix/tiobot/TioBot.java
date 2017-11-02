package com.gmail.socraticphoenix.tiobot;

import fr.tunaki.stackoverflow.chat.ChatHost;
import fr.tunaki.stackoverflow.chat.Room;
import fr.tunaki.stackoverflow.chat.StackExchangeClient;
import fr.tunaki.stackoverflow.chat.event.EventType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class TioBot {

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Expected one argument: <dir>");
            return;
        }

        File dir = new File(args[0]);
        if (!dir.exists()) {
            System.out.println("The configuration directory does not exist.");
            return;
        }

        File config = new File(dir, "config.json");
        File roomStates = new File(dir, "rooms.json");

        if (!config.exists()) {
            System.out.println("The config.json file could not be found.");
            return;
        }

        JSONObject configJson = new JSONObject(content(config));
        if (!configJson.keySet().contains("email") || !configJson.keySet().contains("password")) {
            System.out.println("'email' and/or 'password' fields are missing from the config.json.");
            return;
        }

        JSONObject roomConf;
        if (roomStates.exists()) {
            roomConf = new JSONObject(content(roomStates));
        } else {
            roomConf = new JSONObject();
        }

        String email = String.valueOf(configJson.get("email"));
        String password = String.valueOf(configJson.get("password"));

        LanguageCache cache = new LanguageCache();
        System.out.println("Caching language list and beginning cache daemon...");
        cache.queryLanguages();
        Thread thread = new Thread(() -> {
            try {
                TimeUnit.SECONDS.sleep(15);
            } catch (InterruptedException ignore) {

            }

            try {
                cache.queryLanguages();
            } catch (IllegalStateException e) {
                System.out.println("Failed to query languages.");
            }
        });
        thread.setDaemon(true);
        thread.setName("language-daemon");

        System.out.println("Beginning login...");
        Scanner scanner = new Scanner(System.in);

        StackExchangeClient client = new StackExchangeClient(email, password);
        email = null;
        password = null;

        Map<Room, MessageListener> rooms = new HashMap<>();

        if (configJson.keySet().contains("joins")) {
            JSONObject object = configJson.getJSONObject("joins");
            for (ChatHost host : ChatHost.values()) {
                if (object.keySet().contains(host.name())) {
                    JSONArray toJoin = object.getJSONArray(host.name());
                    for (int i = 0; i < toJoin.length(); i++) {
                        int k = toJoin.getInt(i);
                        join(client, rooms, host.name(), k, cache, roomConf);
                    }
                }
            }
        }

        while (true) {
            String command = scanner.nextLine();
            if (command.equals("exit")) {
                System.out.println("Logging off...");
                rooms.forEach((r, m) -> {
                    r.send("TIOBot logging off!");
                    m.saveState(roomConf, r);
                });

                client.close();

                try (FileWriter writer = new FileWriter(roomStates)) {
                    writer.write(roomConf.toString());
                }

                System.out.println("Saving config.");

                break;
            } else if (command.startsWith("join")) {
                String[] pieces = command.split(" ");
                try {
                    join(client, rooms, pieces[0], Integer.parseInt(pieces[1]), cache, roomConf);
                } catch (NumberFormatException e) {
                    System.out.println(pieces[2] + " must be an integer room id");
                } catch (IllegalArgumentException e) {
                    System.out.println(pieces[1] + " must be one of " + Arrays.toString(ChatHost.values()));
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println("Expected 2 arguments");
                }
            }
        }
    }

    private static void join(StackExchangeClient client, Map<Room, MessageListener> rooms, String host, int id, LanguageCache cache, JSONObject roomConf) {
        SessionCache sessions = new SessionCache();

        Room room = client.joinRoom(ChatHost.valueOf(host.toUpperCase()), id);
        MessageListener listener = new MessageListener(cache, sessions);
        listener.loadState(roomConf, room);

        rooms.put(room, listener);
        room.send("TIOBot logged in!");
        room.addEventListener(EventType.MESSAGE_POSTED, listener);
        room.addEventListener(EventType.USER_LEFT, ev -> {
            sessions.remove(ev.getUserId());
        });
        room.addEventListener(EventType.KICKED, ev -> {
            sessions.remove(ev.getUserId());
        });

        System.out.println("Joined room: (" + host.toUpperCase() + ", " + id + ")");
    }

    private static String content(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

}

