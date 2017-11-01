package com.gmail.socraticphoenix.tiobot;

import com.gmail.socraticphoenix.tioj.Tio;
import com.gmail.socraticphoenix.tioj.TioResponse;

import java.util.Set;

public class LanguageCache {
    private static Tio tio = Tio.MAIN;

    private Set<String> languages = null;

    public void queryLanguages() {
        if (languages == null) {
            TioResponse<Set<String>> response = tio.queryLanguages().get();
            if (response.getResult().isPresent()) {
                languages = response.getResult().get();
            } else {
                throw new IllegalStateException("Unable to query languages");
            }
        }
    }

    public boolean contains(String lang) {
        return languages.contains(lang);
    }

}
