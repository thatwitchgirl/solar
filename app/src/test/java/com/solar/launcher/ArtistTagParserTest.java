package com.solar.launcher;

import org.junit.Test;

import java.util.List;

public class ArtistTagParserTest {

    @Test
    public void splitSemicolonList() {
        List<ArtistTagParser.Credit> credits = ArtistTagParser.parseField("Skepta;bbno$; Yung Gravy");
        if (credits.size() != 3) throw new AssertionError("expected 3, got " + credits.size());
        if (!"Skepta".equals(credits.get(0).display)) throw new AssertionError(credits.get(0).display);
        if (!"bbno$".equals(credits.get(1).display)) throw new AssertionError(credits.get(1).display);
        if (!"Yung Gravy".equals(credits.get(2).display)) throw new AssertionError(credits.get(2).display);
        for (ArtistTagParser.Credit c : credits) {
            if (c.role != ArtistTagParser.Role.EQUAL_PRIMARY) {
                throw new AssertionError("expected EQUAL_PRIMARY for " + c.display);
            }
        }
    }

    @Test
    public void canonicalKeyCaseFold() {
        if (!ArtistTagParser.canonicalKey("Skepta").equals(ArtistTagParser.canonicalKey("skepta"))) {
            throw new AssertionError("case fold");
        }
    }

    @Test
    public void featMarksFeatured() {
        List<ArtistTagParser.Credit> credits = ArtistTagParser.parseField("Snoop Dogg feat. Dr. Dre");
        if (credits.size() != 2) throw new AssertionError("size " + credits.size());
        if (credits.get(0).role != ArtistTagParser.Role.PRIMARY) throw new AssertionError("primary");
        if (credits.get(1).role != ArtistTagParser.Role.FEATURED) throw new AssertionError("featured");
    }

    @Test
    public void andInBandNameNotSplit() {
        List<String> names = ArtistTagParser.splitNames("Florence and the Machine");
        if (names.size() != 1) throw new AssertionError("expected 1 name, got " + names);
    }

    @Test
    public void splitNamesForReach() {
        List<String> names = ArtistTagParser.splitNames("A, B; C");
        if (names.size() != 3) throw new AssertionError("size " + names.size());
    }
}
