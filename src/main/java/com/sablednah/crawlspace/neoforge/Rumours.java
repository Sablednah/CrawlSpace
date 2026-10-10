package com.sablednah.crawlspace.neoforge;

import java.util.ArrayList;
import java.util.List;

import com.sablednah.crawlspace.build.Blueprinter;
import com.sablednah.crawlspace.build.Trigger;
import com.sablednah.crawlspace.plan.Dice;
import com.sablednah.crawlspace.plan.DungeonPlan;
import com.sablednah.crawlspace.plan.Feeling;
import com.sablednah.crawlspace.plan.LevelPlan;
import com.sablednah.crawlspace.plan.Role;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * The rumour book on the first level's lectern (the Keep on the Borderlands'
 * rumour table): what is below, from the dungeon's own plan, so most of it is
 * true, with two rumours that are not. A delver who reads it knows to look
 * for secret doors on a hollow level, or to hurry through a hunted one.
 */
public final class Rumours {

    private Rumours() {
    }

    static ItemStack book(Site site, Site.Built built) {
        DungeonPlan plan = built.plan();
        Dice dice = Dice.of(site.seed(), 0x5EADL);
        List<String> rumours = new ArrayList<>();
        for (int i = 1; i < plan.levels().size(); i++) {
            LevelPlan l = plan.levels().get(i);
            String n = "level " + (i + 1);
            switch (l.feeling) {
                case HOLLOW -> rumours.add("The walls of " + n + " sound hollow. There are doors there that look like stone.");
                case DAMP -> rumours.add(n + " is flooded, they say. Mind the water.");
                case DARK -> rumours.add("No lamp burns on " + n + ", but the dark guards rich things.");
                case CROWDED -> rumours.add(n + " is full of them. Go quietly, or not at all.");
                case TRAPPED -> rumours.add("Watch the floor on " + n + ". It clicks.");
                case HUNTED -> rumours.add("Something hunts " + n + ". Do not linger there.");
                default -> {
                }
            }
            for (Trigger t : built.blueprint().triggers()) {
                if (t.level() != i) {
                    continue;
                }
                if (t.kind() == Trigger.Kind.VAULT && t.targets()[0][0] == t.x() && t.targets()[0][2] == t.z()) {
                    rumours.add("On " + n + " there is a vault with three treasures. You may take only one.");
                }
                if (t.kind() == Trigger.Kind.PORTCULLIS) {
                    rumours.add("The portcullis on " + n + " has a key. It is in a chest on the same level.");
                    break;
                }
            }
        }
        int bottom = plan.levels().size() - 1;
        if (plan.levels().get(bottom).roomWith(Role.LAIR) != null) {
            rumours.add("At the very bottom waits " + Bestiary.bossName(site, bottom) + ".");
        }
        // Two that are not so.
        String[] lies = {
                "The " + plan.levels().get(Math.min(1, bottom)).theme.name() + " is safe. Nothing lives there now.",
                "Throw gold into any pool and it will heal you.",
                "The first lever you find opens every door in the place.",
                "There is no treasure below the second level. Turn back.",
                "The monsters here fear fire. Carry a torch and they will not come near."};
        for (int k = 0; k < 2; k++) {
            int at = dice.nextInt(rumours.size() + 1);
            rumours.add(at, lies[dice.nextInt(lies.length)]);
        }
        List<Filterable<Component>> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder("What they say of this place, and not all of it is true:\n\n");
        for (String r : rumours) {
            String line = "- " + Character.toUpperCase(r.charAt(0)) + r.substring(1) + "\n\n";
            if (page.length() + line.length() > 230) {
                pages.add(Filterable.passThrough(Component.literal(page.toString().trim())));
                page = new StringBuilder();
            }
            page.append(line);
        }
        if (!page.isEmpty()) {
            pages.add(Filterable.passThrough(Component.literal(page.toString().trim())));
        }
        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough("Rumours of the Deep"),
                "a frightened delver", 0, pages, true));
        return book;
    }
}
