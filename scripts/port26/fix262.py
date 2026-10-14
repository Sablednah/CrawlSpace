# Renames for mc26.2 and mc26.3, applied to the neoforge sources after a port from main.
import re, glob
for p in glob.glob("src/main/java/com/sablednah/crawlspace/neoforge/**/*.java", recursive=True):
    s = open(p).read(); o = s
    s = re.sub(r"\bBlocks\.(WHITE|ORANGE|MAGENTA|LIGHT_BLUE|YELLOW|LIME|PINK|GRAY|LIGHT_GRAY|CYAN|PURPLE|BLUE|BROWN|GREEN|RED|BLACK)_CARPET\b",
               r"Blocks.CARPET.pick(net.minecraft.world.item.DyeColor.\1)", s)
    s = re.sub(r"\bBlocks\.WAXED_COPPER_GRATE\b", "Blocks.COPPER_GRATE.waxed().unaffected()", s)
    s = s.replace(".markPosForPostprocessing(", ".markPosForPostProcessing(")
    s = s.replace("net.minecraft.world.entity.monster.Slime", "net.minecraft.world.entity.monster.cubemob.Slime")
    s = re.sub(r"\b(\w+)\.getCenter\(\)", r"net.minecraft.world.phys.Vec3.atCenterOf(\1)", s)
    s = re.sub(r"\bEntityType\.([A-Z][A-Z_]+)\b", r"EntityTypes.\1", s)
    if "EntityTypes." in s and "import net.minecraft.world.entity.EntityTypes;" not in s:
        if "import net.minecraft.world.entity.EntityType;" in s:
            s = s.replace("import net.minecraft.world.entity.EntityType;", "import net.minecraft.world.entity.EntityType;\nimport net.minecraft.world.entity.EntityTypes;", 1)
        else:
            s = re.sub(r"(\nimport net\.minecraft[^\n]*;)", r"\1\nimport net.minecraft.world.entity.EntityTypes;", s, count=1)
    if s != o:
        open(p, "w").write(s)
