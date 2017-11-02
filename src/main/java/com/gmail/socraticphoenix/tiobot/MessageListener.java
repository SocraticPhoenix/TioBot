package com.gmail.socraticphoenix.tiobot;

import com.gmail.socraticphoenix.tioj.Tio;
import com.gmail.socraticphoenix.tioj.TioResponse;
import com.gmail.socraticphoenix.tioj.TioResult;
import fr.tunaki.stackoverflow.chat.Room;
import fr.tunaki.stackoverflow.chat.User;
import fr.tunaki.stackoverflow.chat.event.MessagePostedEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public class MessageListener implements Consumer<MessagePostedEvent> {
    private static Tio tio = Tio.MAIN;
    private SessionCache sessions;
    private LanguageCache cache;
    private Set<Long> permitted = new HashSet<>();
    private Map<String, String> languages = new HashMap<>();
    private Map<String, String> commands = new HashMap<>();

    public MessageListener(LanguageCache cache, SessionCache sessions) {
        this.cache = cache;
        this.sessions = sessions;
        this.permitted.add(226059L); //Socratic_Phoenix's user id, for testing. You can always revoke his permission if you like.
    }

    @Override
    public void accept(MessagePostedEvent messagePostedEvent) {
        try {
            if (!messagePostedEvent.getUser().isPresent()) {
                return;
            }

            User user = messagePostedEvent.getUser().get();
            Room room = messagePostedEvent.getRoom();
            long id = messagePostedEvent.getUserId();
            String handle = "@" + messagePostedEvent.getUser().get().getName().replace(" ", "");

            String text = messagePostedEvent.getMessage().getPlainContent();

            executeCommand(user, room, id, handle, text);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private int limit = 0;
    private void executeCommand(User user, Room room, long id, String handle, String text) {
        if (limit >= 255) {
            limit = 0;
            return;
        }

        limit++;
        if (text.length() >= 5 && text.substring(0, 5).equalsIgnoreCase("#TIO ")) {
            text = text.substring(5);
            while (text.startsWith(" ")) {
                text = text.replace(" ", "");
            }

            if (text.isEmpty()) {
                room.send(handle + " expected more arguments...");
                limit--;
                return;
            }

            String[] cmdContent = text.split(" ", 2);
            String cmd = cmdContent[0];
            String content = cmdContent.length > 1 ? cmdContent[1] : null;

            if (cmd.equals("arg")) {
                if (content == null) {
                    room.send(handle + " expected more arguments...");
                    limit--;
                    return;
                }

                if (sessions.contains(id)) {
                    CodeSession session = sessions.get(id);
                    session.getArgs().add(content);
                    room.send("Added argument to " + handle + "'s session");
                } else {
                    room.send("No session found for " + handle);
                }
            } else if (cmd.equals("input")) {
                if (content == null) {
                    room.send(handle + " expected more arguments...");
                    limit--;
                    return;
                }

                if (sessions.contains(id)) {
                    if (sessions.contains(id)) {
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
                    limit--;
                    return;
                }

                if (sessions.contains(id)) {
                    CodeSession session = sessions.get(id);
                    session.getCode().append(content).append("\n");
                    room.send("Added code to " + handle + "'s session");
                } else {
                    room.send("No session found for " + handle);
                }
            } else if (cmd.equals("flag")) {
                if (content == null) {
                    room.send(handle + " expected more arguments...");
                    limit--;
                    return;
                }

                if (sessions.contains(id)) {
                    CodeSession session = sessions.get(id);
                    session.getCmdFlags().add(content);
                    room.send("Added flag to " + handle + "'s session");
                } else {
                    room.send("No session found for " + handle);
                }
            } else if (cmd.equals("cflag")) {
                if (content == null) {
                    room.send(handle + " expected more arguments...");
                    limit--;
                    return;
                }

                if (sessions.contains(id)) {
                    CodeSession session = sessions.get(id);
                    session.getCompFlags().add(content);
                    room.send("Added compiler flag to " + handle + "'s session");
                } else {
                    room.send("No session found for " + handle);
                }
            } else if (cmd.equals("submit")) {
                if (sessions.contains(id)) {
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
                    limit--;
                    return;
                }

                if (!sessions.contains(id)) {
                    if (this.hasLanguage(content)) {
                        String lang = this.getLanguage(content);
                        sessions.create(id, new CodeSession(lang));
                        room.send(handle + " began session in " + lang);
                    } else {
                        room.send(handle + " sorry, but I couldn't find any languages matching \"" + content + "\"");
                    }
                } else {
                    room.send(handle + " already has a session. Cancel or submit it to create a new one.");
                }
            } else if (cmd.equals("view")) {
                if (sessions.contains(id)) {
                    room.send(codeBlock(handle + ":\n" + sessions.get(id).view()));
                } else {
                    room.send("No session found for " + handle);
                }
            } else if (cmd.equals("cancel")) {
                sessions.remove(id);
            } else if (cmd.equals("help")) {
                room.send(handle + " [TIOBot command list](https://gist.github.com/SocraticPhoenix/bf98c72d0c1274acce76bc02ac6ee253)");
            } else if (cmd.equals("version")) {
                room.send(handle + " TIOBot v 0.0.3");
            } else if (cmd.equals("alias")) {
                if (content == null) {
                    room.send(handle + " expected more arguments...");
                    limit--;
                    return;
                }

                if (this.hasPermission(user)) {
                    String[] tc = content.split(" ", 2);
                    if (tc[0].equals("view")) {
                        StringBuilder builder = new StringBuilder();
                        builder.append("Command Aliases:\n");
                        this.commands.forEach((k, v) -> builder.append(k).append(" -> ").append(v).append("\n"));
                        builder.append("Language Aliases:\n");
                        this.languages.forEach((k, v) -> builder.append(v).append(" -> ").append(k).append("\n"));
                        room.send(codeBlock(builder.toString()));
                    } else
                    if (tc.length < 2) {
                        room.send(handle + " expected more arguments...");
                    } else {
                        String type = tc[0];
                        if (type.equals("rlang")) {
                            String k = languages.remove(tc[1].toLowerCase());
                            if (k == null) {
                                room.send(handle + " no alias exists for \"" + tc[1] + "\"");
                            } else {
                                room.send(handle + " removed alias for \"" + tc[1] + "\"");
                            }
                        } else if (type.equals("rcommand")) {
                            String k = commands.remove(tc[1].toLowerCase());
                            if (k == null) {
                                room.send(handle + " no alias exists for \"" + tc[1] + "\"");
                            } else {
                                room.send(handle + " removed alias for \"" + tc[1] + "\"");
                            }
                        } else {
                            String[] aliasContent = tc[1].split(" ", 2);
                            if (aliasContent.length < 2) {
                                room.send(handle + " expected more arguments...");
                            } else if (type.equals("lang")) {
                                String lang = aliasContent[0].toLowerCase();
                                languages.put(lang, aliasContent[1]);
                                room.send(handle + " Added alias for " + lang);
                            } else if (type.equals("command")) {
                                String command = aliasContent[0].toLowerCase();
                                commands.put(command, aliasContent[1]);
                                room.send(handle + " Added alias for " + command);
                            } else {
                                room.send(handle + " No alias type called \"" + type + "\"");
                            }
                        }
                    }

                } else {
                    room.send(handle + " you do not have permission to edit settings for this room!");
                }
            } else if (cmd.equals("grant")) {
                if (this.hasPermission(user)) {
                    try {
                        long targetId = Long.valueOf(content == null ? "" : content);
                        User targetUser = room.getUser(targetId);

                        if (this.hasPermission(targetUser)) {
                            room.send(handle + " " + targetUser.getName() + " already has permissions in this room.");
                        } else {
                            permitted.add(targetId);
                            room.send(handle + " granted permission to " + targetUser.getName());
                        }
                    } catch (NumberFormatException e) {
                        room.send(handle + " " + content + " is not number.");
                    } catch (IndexOutOfBoundsException e) {
                        room.send(handle + " " + content + " is not a valid user id.");
                    }
                } else {
                    room.send(handle + " you do not have permission to edit settings for this room!");
                }
            } else if (cmd.equals("revoke")) {
                if (this.hasPermission(user)) {
                    try {
                        long targetId = Long.valueOf(content == null ? "" : content);
                        User targetUser = room.getUser(targetId);

                        if (!this.hasPermission(targetUser)) {
                            room.send(handle + " " + targetUser.getName() + " does not have permissions in this room.");
                        } else if (!permitted.contains(targetId)) {
                            room.send(handle + " " + targetUser.getName() + " cannot have permissions revoked.");
                        } else {
                            room.send(handle + " revoked permission from " + targetUser.getName());
                            permitted.remove(targetId);
                        }
                    } catch (NumberFormatException e) {
                        room.send(handle + " " + content + " is not number.");
                    } catch (IndexOutOfBoundsException e) {
                        room.send(handle + " " + content + " is not a valid user id.");
                    }
                } else {
                    room.send(handle + " you do not have permission to edit settings for this room!");
                }
            } else {
                boolean single = cmd.equals("do");

                if (content == null) {
                    room.send(handle + " expected more arguments...");
                    limit--;
                    return;
                }

                if (!sessions.contains(id)) {
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

                    if (this.hasLanguage(lang)) {
                        lang = this.getLanguage(lang);
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
        } else {
            String finalText = text;
            Optional<String> key = this.commands.keySet().stream().filter(s -> finalText.length() >= s.length() && finalText.substring(0, s.length()).equalsIgnoreCase(s)).sorted((a, b) -> Integer.compare(b.length(), a.length())).findFirst();
            if (key.isPresent()) {
                text = text.substring(key.get().length());
                if (text.startsWith(" ")) {
                    text = text.substring(1);
                }

                String cmd = this.commands.get(key.get()).replace("%args%", text);
                executeCommand(user, room, id, handle, cmd);
            }
        }
        limit--;
    }

    public void saveState(JSONObject rooms, Room room) {
        String id = String.valueOf(room.getRoomId());

        JSONObject object = new JSONObject();

        JSONObject langAliases = new JSONObject();
        JSONObject commandAliases = new JSONObject();

        this.languages.forEach(langAliases::put);
        this.commands.forEach(commandAliases::put);

        JSONArray permitted = new JSONArray();
        this.permitted.forEach(permitted::put);

        object.put("languages", langAliases);
        object.put("commands", commandAliases);
        object.put("permitted", permitted);

        rooms.put(id, object);
    }

    public void loadState(JSONObject rooms, Room room) {
        String key = String.valueOf(room.getRoomId());
        if (rooms.keySet().contains(key)) {
            JSONObject object = rooms.getJSONObject(key);

            JSONObject langAliases = object.getJSONObject("languages");
            JSONObject commandAliases = object.getJSONObject("commands");

            langAliases.keySet().forEach(s -> languages.put(s, String.valueOf(langAliases.get(s))));
            commandAliases.keySet().forEach(s -> commands.put(s, String.valueOf(commandAliases.get(s))));

            JSONArray permitted = object.getJSONArray("permitted");
            for (int i = 0; i < permitted.length(); i++) {
                this.permitted.add(permitted.getLong(i));
            }
        }
    }

    private String getLanguage(String lang) {
        lang = lang.toLowerCase();
        if (this.cache.contains(lang)) {
            return lang;
        } else if (this.cache.contains(this.languages.get(lang))) {
            return this.languages.get(lang);
        } else {
            return null;
        }
    }

    private boolean hasLanguage(String lang) {
        return this.getLanguage(lang) != null;
    }

    private boolean hasPermission(User user) {
        return user.isRoomOwner() || user.isModerator() || this.permitted.contains(user.getId());
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
