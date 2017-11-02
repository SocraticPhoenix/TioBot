package com.gmail.socraticphoenix.tiobot;

import com.gmail.socraticphoenix.tioj.Tio;
import com.gmail.socraticphoenix.tioj.TioResponse;
import com.gmail.socraticphoenix.tioj.TioResult;
import fr.tunaki.stackoverflow.chat.Room;
import fr.tunaki.stackoverflow.chat.event.MessagePostedEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class MessageListener implements Consumer<MessagePostedEvent> {
    private static Tio tio = Tio.MAIN;
    private Map<Long, CodeSession> sessions = new HashMap<>();
    private LanguageCache cache;

    public MessageListener(LanguageCache cache) {
        this.cache = cache;
    }

    @Override
    public void accept(MessagePostedEvent messagePostedEvent) {
        try {
            Room room = messagePostedEvent.getRoom();
            long id = messagePostedEvent.getUserId();
            String handle = "@" + messagePostedEvent.getUser().get().getName();

            String text = messagePostedEvent.getMessage().getPlainContent();
            if (text.startsWith("#TIO ")) {
                text = text.substring(5);
                while (text.startsWith(" ")) {
                    text = text.replace(" ", "");
                }
                String[] cmdContent = text.split(" ", 2);
                String cmd = cmdContent[0];
                String content = cmdContent.length > 1 ? cmdContent[1] : null;

                if (cmd.equals("arg")) {
                    if (content == null) {
                        room.send(handle + " expected more arguments...");
                        return;
                    }

                    if (sessions.containsKey(id)) {
                        CodeSession session = sessions.get(id);
                        session.getArgs().add(content);
                        room.send("Added argument to " + handle + "'s session");
                    } else {
                        room.send("No session found for " + handle);
                    }
                } else if (cmd.equals("input")) {
                    if (content == null) {
                        room.send(handle + " expected more arguments...");
                        return;
                    }

                    if (sessions.containsKey(id)) {
                        if (sessions.containsKey(id)) {
                            CodeSession session = sessions.get(id);
                            session.getInput().append(content).append("\n");
                            room.send("Added input to " + handle + "'s session");
                        } else {
                            room.send("No session found for " + handle);
                        }
                    } else {
                        room.send("No session found for " + handle);
                    }
                } else if (cmd.equals("code")) {
                    if (content == null) {
                        room.send(handle + " expected more arguments...");
                        return;
                    }

                    if (sessions.containsKey(id)) {
                        CodeSession session = sessions.get(id);
                        session.getCode().append(content).append("\n");
                        room.send("Added code to " + handle + "'s session");
                    } else {
                        room.send("No session found for " + handle);
                    }
                } else if (cmd.equals("flag")) {
                    if (content == null) {
                        room.send(handle + " expected more arguments...");
                        return;
                    }

                    if (sessions.containsKey(id)) {
                        CodeSession session = sessions.get(id);
                        session.getCmdFlags().add(content);
                        room.send("Added flag to " + handle + "'s session");
                    } else {
                        room.send("No session found for " + handle);
                    }
                } else if (cmd.equals("cflag")) {
                    if (content == null) {
                        room.send(handle + " expected more arguments...");
                        return;
                    }

                    if (sessions.containsKey(id)) {
                        CodeSession session = sessions.get(id);
                        session.getCompFlags().add(content);
                        room.send("Added compiler flag to " + handle + "'s session");
                    } else {
                        room.send("No session found for " + handle);
                    }
                } else if (cmd.equals("submit")) {
                    if (sessions.containsKey(id)) {
                        CodeSession session = sessions.remove(id);
                        new Thread(() -> {
                            TioResponse<TioResult> response = tio.send(session.format()).get();
                            if (response.getResult().isPresent()) {
                                TioResult result = response.getResult().get();
                                room.send(codeBlock(handle + ":\n" + result.get(TioResult.Field.OUTPUT) + "\n============\n\n" + result.get(TioResult.Field.DEBUG)));
                            } else {
                                room.send(handle + " sorry, but an error occurred while executing your code: " + response.getCode());
                            }
                        }).start();
                    } else {
                        room.send("No session found for " + handle);
                    }
                } else if (cmd.equals("session")) {
                    if (content == null) {
                        room.send(handle + " expected more arguments...");
                        return;
                    }

                    if (!sessions.containsKey(id)) {
                        String lang = content.toLowerCase();
                        if (this.cache.contains(lang)) {
                            sessions.put(id, new CodeSession(lang));
                            room.send(handle + " began session in " + lang);
                        } else {
                            room.send(handle + " sorry, but I couldn't find any languages matching \"" + lang + "\"");
                        }
                    } else {
                        room.send(handle + " already has a session. Cancel or submit it to create a new one.");
                    }
                } else if (cmd.equals("view")) {
                    if (sessions.containsKey(id)) {
                        room.send(codeBlock(handle + ":\n" + sessions.get(id).view()));
                    } else {
                        room.send("No session found for " + handle);
                    }
                } else if (cmd.equals("cancel")) {
                    sessions.remove(id);
                } else if (cmd.equals("help")) {
                    room.send("[TIOBot command list](https://gist.github.com/SocraticPhoenix/bf98c72d0c1274acce76bc02ac6ee253)");
                } else {
                    boolean single = cmd.equals("do");

                    if (content == null) {
                        room.send(handle + " expected more arguments...");
                        return;
                    }

                    if (!sessions.containsKey(id)) {
                        String lang;
                        String code;
                        if (cmd.equals("do") || cmd.equals("run")) {
                            String[] langContent = content.split(" ", 2);
                            lang = langContent[0];
                            code = langContent.length > 1 ? langContent[1] : "";
                        } else {
                            lang = cmd;
                            code = content;
                        }

                        lang = lang.toLowerCase();

                        if (this.cache.contains(lang)) {
                            CodeSession session = new CodeSession(lang);
                            session.getCode().append(code);
                            new Thread(() -> {
                                TioResponse<TioResult> response = tio.send(session.format()).get();
                                if (response.getResult().isPresent()) {
                                    TioResult result = response.getResult().get();

                                    String output = result.get(TioResult.Field.OUTPUT);
                                    if (single) {
                                        room.send(codeBlock(handle + " " + output.split("\n")[0]));
                                    } else {
                                        if (!output.isEmpty()) {
                                            room.send(codeBlock(handle + "\n" + result.get(TioResult.Field.OUTPUT)));
                                        } else {
                                            room.send(codeBlock(handle + "\n" + result.get(TioResult.Field.DEBUG)));
                                        }
                                    }
                                } else {
                                    room.send(handle + " sorry, but an error occurred while executing your code: " + response.getCode());
                                }
                            }).start();
                        } else {
                            room.send(handle + " sorry, but I couldn't find any languages matching \"" + lang + "\"");
                        }
                    } else {
                        room.send(handle + " already has a session. Cancel or submit it to create a new one.");
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private static String codeBlock(String s) {
        String[] pieces = s.split("\n");
        StringBuilder builder = new StringBuilder();
        for (String k : pieces) {
            builder.append("    ").append(k).append("\n");
        }
        return builder.toString();
    }

}
