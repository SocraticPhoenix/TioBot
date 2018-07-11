package com.gmail.socraticphoenix.tiobot;

import fr.tunaki.stackoverflow.chat.ChatHost;
import fr.tunaki.stackoverflow.chat.ChatOperationException;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TioBot {
    public static final String VERSION = "0.0.6";

    private static boolean silentJoin = false;
    public static boolean running = true;
    public static boolean silentLeave = false;

    public static void main(String[] args) throws IOException {
        if (args.length == 0) {
            System.out.println("Expected at least one argument: <dir>");
            return;
        }

        silentJoin = args.length >= 2 && args[1].equals("silent");

        System.out.println("TIOBot v " + TioBot.VERSION);

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

        Map<Room, MessageListener> rooms = Collections.synchronizedMap(new HashMap<>());
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicBoolean finished = new AtomicBoolean(false);
        Thread saveThread = new Thread(() -> {
            long ms = System.currentTimeMillis();
            while (running.get()) {
                long now = System.currentTimeMillis();
                if (now - ms >= TimeUnit.SECONDS.toMillis(15)) {
                    synchronized (roomConf) {
                        rooms.forEach((r, m) -> m.saveState(roomConf, r));
                        String content = roomConf.toString();
                        try (FileWriter writer = new FileWriter(roomStates)) {
                            writer.write(content);
                        } catch (IOException e) {
                            System.err.println("Failed to save config");
                            e.printStackTrace();
                        }
                    }

                    synchronized (config) {
                        String content = configJson.toString(0);
                        try (FileWriter writer = new FileWriter(config)) {
                            writer.write(content);
                        } catch (IOException e) {
                            System.err.println("Failed to save config");
                            e.printStackTrace();
                        }
                    }
                }

                try {
                    TimeUnit.MILLISECONDS.sleep(250);
                } catch (InterruptedException ignore) {
                }
            }
            finished.set(true);
        });
        saveThread.setName("save-thread");
        saveThread.start();

        if (configJson.keySet().contains("joins")) {
            JSONObject object = configJson.getJSONObject("joins");
            for (ChatHost host : ChatHost.values()) {
                if (object.keySet().contains(host.name())) {
                    JSONArray toJoin = object.getJSONArray(host.name());
                    for (int i = 0; i < toJoin.length(); i++) {
                        int k = toJoin.getInt(i);
                        synchronized (roomConf) {
                            join(client, rooms, host.name(), k, cache, roomConf);
                        }
                    }
                }
            }
        }

        while (TioBot.running) {
            String command = scanner.nextLine();
            if (command.startsWith("exit")) {
                TioBot.silentLeave = command.equals("exit silent");
                TioBot.running = false;
                break;
            } else if (command.startsWith("leave")) {
                String[] pieces = command.split(" ");
                try {
                    synchronized (roomConf) {
                        int id = Integer.parseInt(pieces[2]);
                        ChatHost host = ChatHost.valueOf(pieces[1]);
                        boolean silent = pieces.length >= 4 && pieces[3].equals("silent");

                        Optional<Room> target = rooms.keySet().stream().filter(r -> r.getRoomId() == id && r.getHost() == host).findFirst();

                        if (target.isPresent()) {
                            Room room = target.get();

                            if (!silent) {
                                room.send("TIOBot (" + TioBot.VERSION + ") logging off!");
                            }

                            room.leave();
                            rooms.remove(room);
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println(pieces[2] + " must be an integer room id");
                } catch (IllegalArgumentException e) {
                    System.out.println(pieces[1] + " must be one of " + Arrays.toString(ChatHost.values()));
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println("Expected 2 arguments");
                }
            } else if (command.startsWith("autoleave")) {
                String[] pieces = command.split(" ");
                try {
                    synchronized (roomConf) {
                        int id = Integer.parseInt(pieces[2]);
                        ChatHost host = ChatHost.valueOf(pieces[1]);
                        boolean silent = pieces.length >= 4 && pieces[3].equals("silent");

                        Optional<Room> target = rooms.keySet().stream().filter(r -> r.getRoomId() == id && r.getHost() == host).findFirst();

                        if (target.isPresent()) {
                            Room room = target.get();

                            if (!silent) {
                                room.send("TIOBot ("   + TioBot.VERSION + ") logging off!");
                            }

                            room.leave();
                            rooms.remove(room);

                            if (roomConf.keySet().contains(host.toString())) {
                                roomConf.getJSONObject(host.toString()).remove(String.valueOf(id));
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println(pieces[2] + " must be an integer room id");
                } catch (IllegalArgumentException e) {
                    System.out.println(pieces[1] + " must be one of " + Arrays.toString(ChatHost.values()));
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println("Expected 2 arguments");
                }
            }  else if (command.startsWith("join")) {
                String[] pieces = command.split(" ");
                try {
                    synchronized (roomConf) {
                        join(client, rooms, pieces[1], Integer.parseInt(pieces[2]), cache, roomConf);
                    }
                } catch (NumberFormatException e) {
                    System.out.println(pieces[2] + " must be an integer room id");
                } catch (IllegalArgumentException e) {
                    System.out.println(pieces[1] + " must be one of " + Arrays.toString(ChatHost.values()));
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println("Expected 2 arguments");
                }
            } else if (command.startsWith("autojoin")) {
                String[] pieces = command.split(" ");
                try {
                    synchronized (roomConf) {
                        join(client, rooms, pieces[1], Integer.parseInt(pieces[2]), cache, roomConf);
                        if (!configJson.keySet().contains("joins")) {
                            configJson.put("joins", new JSONObject());
                        }

                        synchronized (configJson) {
                            JSONObject joins = configJson.getJSONObject("joins");
                            String host = pieces[1].toUpperCase();
                            if (!joins.keySet().contains(host)) {
                                joins.put(host, new JSONArray());
                            }
                            joins.getJSONArray(host).put(Integer.parseInt(pieces[2]));
                            System.out.println("Added room to autojoin list");
                        }
                    }
                } catch (NumberFormatException e) {
                    System.out.println(pieces[2] + " must be an integer room id");
                } catch (IllegalArgumentException e) {
                    System.out.println(pieces[1] + " must be one of " + Arrays.toString(ChatHost.values()));
                } catch (ArrayIndexOutOfBoundsException e) {
                    System.out.println("Expected 2 arguments");
                }
            }
        }

        running.set(false);
        System.out.println("Logging off...");
        System.out.println("Saving config.");

        while (!finished.get());

        synchronized (roomConf) {
            rooms.forEach((r, m) -> {
                if (!TioBot.silentLeave) {
                    r.send("TIOBot (" + TioBot.VERSION + ")logging off!");
                }
                m.saveState(roomConf, r);
            });

            client.close();

            try (FileWriter writer = new FileWriter(roomStates)) {
                writer.write(roomConf.toString());
            }
        }

        synchronized (configJson) {
            String content = configJson.toString(0);
            try (FileWriter writer = new FileWriter(config)) {
                writer.write(content);
            }
        }
    }

    private static void join(StackExchangeClient client, Map<Room, MessageListener> rooms, String host, int id, LanguageCache cache, JSONObject roomConf) {
        try {
            SessionCache sessions = new SessionCache();

            Room room = client.joinRoom(ChatHost.valueOf(host.toUpperCase()), id);
            MessageListener listener = new MessageListener(cache, sessions);
            listener.loadState(roomConf, room);

            rooms.put(room, listener);
            if (!silentJoin) {
                room.send("TIOBot (" + TioBot.VERSION + ") logged in!");
            }
            room.addEventListener(EventType.MESSAGE_POSTED, listener);
            room.addEventListener(EventType.USER_LEFT, ev -> {
                sessions.remove(ev.getUserId());
            });
            room.addEventListener(EventType.KICKED, ev -> {
                sessions.remove(ev.getUserId());
            });

            System.out.println("Joined room: (" + host.toUpperCase() + ", " + id + ")");
        } catch (ChatOperationException e) {
            System.out.println("Cannot join a room twice");
        }
    }

    private static String content(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

}

