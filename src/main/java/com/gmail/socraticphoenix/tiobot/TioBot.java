package com.gmail.socraticphoenix.tiobot;

import fr.tunaki.stackoverflow.chat.ChatHost;
import fr.tunaki.stackoverflow.chat.Room;
import fr.tunaki.stackoverflow.chat.StackExchangeClient;
import fr.tunaki.stackoverflow.chat.event.EventType;
import org.json.JSONObject;

import java.io.Console;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class TioBot {

    public static void main(String[] args) {
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

        File config;
        String email = null, password = null;
        if (args.length >= 1 && args[0].startsWith("-config=")) {
            config = new File(args[0].replaceFirst("-config=", ""));
            if (config.exists()) {
                System.out.print("Config detected; loading email and password...");
                StringBuilder content = new StringBuilder();
                try {
                    Files.readAllLines(config.toPath()).forEach(s -> content.append(s).append("\n"));
                } catch (IOException e) {
                    System.err.println("Failed to read config");
                    e.printStackTrace();
                    return;
                }

                JSONObject object = new JSONObject(content.toString());
                if (object.keySet().contains("email") && object.keySet().contains("password")) {
                    email = String.valueOf(object.get("email"));
                    password = String.valueOf(object.get("password"));
                } else {
                    System.err.println("Config does not contain email & password elements");
                    return;
                }
            }
        } else {
            System.out.print("Email> ");
            email = scanner.nextLine();
            
            Console console = System.console();
            if (console != null) {
                password = String.valueOf(console.readPassword("Password> "));
            } else {
                System.out.println("No console instance found; Maybe you are in an IDE? The Password will not be hidden.");
                System.out.print("Password> ");
                password = scanner.nextLine();
            }
        }
        


        StackExchangeClient client = new StackExchangeClient(email, password);
        email = null;
        password = null;

        List<Room> rooms = new ArrayList<>();

        while (true) {
            String command = scanner.nextLine();
            if (command.equals("exit")) {
                System.out.println("Logging off...");
                rooms.forEach(r -> r.send("TIOBot logging off!"));
                client.close();
                break;
            } else if (command.startsWith("join")) {
                String[] pieces = command.split(" ");
                try {
                    SessionCache sessions = new SessionCache();

                    Room room = client.joinRoom(ChatHost.valueOf(pieces[1].toUpperCase()), Integer.parseInt(pieces[2]));
                    rooms.add(room);
                    room.send("TIOBot logged in!");
                    room.addEventListener(EventType.MESSAGE_POSTED, new MessageListener(cache, sessions));
                    room.addEventListener(EventType.USER_LEFT, ev -> {
                        sessions.remove(ev.getUserId());
                    });
                    room.addEventListener(EventType.KICKED, ev -> {
                        sessions.remove(ev.getUserId());
                    });

                    System.out.println("Joined room");
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

}

