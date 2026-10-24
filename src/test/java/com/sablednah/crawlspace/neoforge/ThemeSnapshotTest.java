package com.sablednah.crawlspace.neoforge;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sablednah.crawlspace.plan.Dressing;
import com.sablednah.crawlspace.plan.Theme;

/**
 * A dungeon keeps a copy of its themes (Site), and is planned again from that
 * copy for every chunk it touches, after every restart. The copy has to
 * rebuild exactly the themes it was taken from, or walls move.
 */
class ThemeSnapshotTest {

    @Test
    void theBuiltInListIsNotCopied() {
        assertNull(ThemeJson.snapshot(Theme.BY_DEPTH));
        assertSame(Theme.BY_DEPTH, ThemeJson.restore(null));
    }

    @Test
    void everyBuiltInThemeSurvivesJson() {
        for (Theme t : Theme.BUILT_IN) {
            Theme back = ThemeJson.read(t.name(), ThemeJson.write(t), Theme.CRYPT);
            assertEquals(ThemeJson.write(t).toString(), ThemeJson.write(back).toString(), t.name());
            assertEquals(t.dressing(), back.dressing(), t.name());
        }
    }

    @Test
    void aCustomListSurvivesItsSnapshot() {
        Theme lab = ThemeJson.read("Research Lab", JsonParser.parseString("""
                {"layout": {"shapes": {"rect": 5, "hall": 2, "cave": 0}, "narrow_chance": 0.123456789, "room_scale": 1.17},
                 "dressing": {"flavour": "old_mines", "clutter": ["barrel", "lectern"], "cobwebs": 0.05, "timber": false}}
                """).getAsJsonObject(), Theme.DEEP_HALLS);
        List<Theme> list = List.of(lab, lab, Theme.CAVERNS);
        String snap = ThemeJson.snapshot(list);
        List<Theme> back = ThemeJson.restore(snap);
        assertEquals(3, back.size());
        assertSame(Theme.CAVERNS, back.get(2)); // an untouched built-in theme is stored by name only
        assertEquals(ThemeJson.write(lab).toString(), ThemeJson.write(back.get(0)).toString());
        assertEquals(snap, ThemeJson.snapshot(back)); // and saving it again writes the same thing
        assertEquals(new Dressing("old_mines", true, true, true, 0.05, 0, List.of("barrel", "lectern"), 0.1, false, 0, false, false, 0.5, 0.2, 0.06),
                back.get(0).dressing());
    }

    @Test
    void aFileOnlyChangesWhatItSays() {
        Theme t = ThemeJson.read("X", new JsonObject(), Theme.OLD_MINES);
        assertEquals(ThemeJson.write(Theme.OLD_MINES).toString(), ThemeJson.write(t).toString());
    }

    @Test
    void nonsenseIsClampedOrIgnored() {
        Theme t = ThemeJson.read("X", JsonParser.parseString("""
                {"layout": {"shapes": {"rect": 0, "rounded": 0, "circle": 0, "octagon": 0, "cross": 0, "l": 0, "hall": 0, "blob": 9},
                            "room_scale": 9, "height_spread": 40, "narrow_chance": -3},
                 "dressing": {"flavour": "spaceship", "clutter": ["skull", "teapot"]}}
                """).getAsJsonObject(), Theme.CAVERNS);
        assertEquals(Theme.MAX_SCALE, t.roomScale());
        assertEquals(Theme.MAX_SPREAD, t.heightSpread());
        assertEquals(0, t.narrowChance());
        assertEquals("caverns", t.dressing().flavour());
        assertEquals(List.of("skull"), t.dressing().clutter());
        // Caverns is all cave already, so zeroing the rest still leaves one shape to draw.
        assertEquals(6, t.shapeWeights()[Theme.SHAPES.indexOf("cave")]);
    }
}
