# Renames for every 26.x line, applied after a port from main.
import re, glob
for p in glob.glob("src/main/java/com/sablednah/crawlspace/**/*.java", recursive=True):
    s = open(p).read(); o = s
    s = re.sub(r"\.getTags\(\)", ".entityTags()", s)
    s = re.sub(r"(\w+)\.displayClientMessage\(([^;]*?), true\);", r"\1.sendOverlayMessage(\2);", s, flags=re.S)
    s = re.sub(r"new ((?:net\.minecraft\.server\.level\.)?)ServerBossEvent\((?!java\.util\.UUID)", r"new \1ServerBossEvent(java.util.UUID.randomUUID(), ", s)
    s = s.replace("net.neoforged.neoforge.event.level.BlockEvent.BreakEvent", "net.neoforged.neoforge.event.level.block.BreakBlockEvent")
    s = re.sub(r"new net\.minecraft\.world\.item\.component\.DamageResistant\(\s*net\.minecraft\.tags\.DamageTypeTags\.IS_FIRE\)",
               "new net.minecraft.world.item.component.DamageResistant(level.registryAccess().lookupOrThrow("
               "net.minecraft.core.registries.Registries.DAMAGE_TYPE).getOrThrow(net.minecraft.tags.DamageTypeTags.IS_FIRE))", s)
    if s != o:
        open(p, "w").write(s)
