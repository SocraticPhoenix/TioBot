package com.gmail.socraticphoenix.tiobot;

import fr.tunaki.stackoverflow.chat.ChatHost;
import fr.tunaki.stackoverflow.chat.Room;
import fr.tunaki.stackoverflow.chat.StackExchangeClient;
import fr.tunaki.stackoverflow.chat.event.EventType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class TioBot {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

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
        System.out.print("Email> ");
        String email = scanner.nextLine();
        System.out.print("Password> ");
        String password = scanner.nextLine();

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
                    Room room = client.joinRoom(ChatHost.valueOf(pieces[1].toUpperCase()), Integer.parseInt(pieces[2]));
                    rooms.add(room);
                    room.send("TIOBot logged in!");
                    room.addEventListener(EventType.MESSAGE_POSTED, new MessageListener(cache));
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
