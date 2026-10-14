# Mechanical renames from NeoForge 1.21.11 to Forge 1.20.1 (Mojang names), applied after a port from main.
# Idempotent: safe to run on a tree it has already been run on. What it cannot do is done by hand on the branch.
import re, glob, sys
root = sys.argv[1] if len(sys.argv) > 1 else "src/main/java"
SIMPLE = [
    ("net.neoforged.bus.api.IEventBus", "net.minecraftforge.eventbus.api.IEventBus"),
    ("net.neoforged.fml.common.Mod", "net.minecraftforge.fml.common.Mod"),
    ("net.neoforged.fml.config.ModConfig", "net.minecraftforge.fml.config.ModConfig"),
    ("net.neoforged.fml.ModContainer", "net.minecraftforge.fml.ModContainer"),
    ("net.neoforged.neoforge.common.NeoForge", "net.minecraftforge.common.MinecraftForge"),
    ("NeoForge.EVENT_BUS", "MinecraftForge.EVENT_BUS"),
    ("net.neoforged.neoforge.common.ModConfigSpec", "net.minecraftforge.common.ForgeConfigSpec"),
    ("ModConfigSpec", "ForgeConfigSpec"),
    ("net.neoforged.neoforge.common.util.FakePlayer", "net.minecraftforge.common.util.FakePlayer"),
    ("net.neoforged.neoforge.registries.DeferredRegister", "net.minecraftforge.registries.DeferredRegister"),
    ("net.neoforged.neoforge.registries.DeferredHolder", "net.minecraftforge.registries.RegistryObject"),
    ("net.neoforged.neoforge.event.tick.ServerTickEvent", "net.minecraftforge.event.TickEvent"),
    ("net.neoforged.neoforge.event.tick.PlayerTickEvent", "net.minecraftforge.event.TickEvent"),
    ("net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent", "net.minecraftforge.event.entity.living.LivingAttackEvent"),
    ("LivingIncomingDamageEvent", "LivingAttackEvent"),
    ("net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent", "net.minecraftforge.event.entity.living.MobSpawnEvent.FinalizeSpawn"),
    ("net.neoforged.neoforge.event.AddServerReloadListenersEvent", "net.minecraftforge.event.AddReloadListenerEvent"),
    ("net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn", "net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn"),
    ("net.neoforged.neoforge.event.", "net.minecraftforge.event."),
    ("net.neoforged.neoforge.", "net.minecraftforge."),
    ("net.minecraft.resources.Identifier", "net.minecraft.resources.ResourceLocation"),
    ("Identifier.fromNamespaceAndPath(", "new ResourceLocation("),
    ("Identifier.withDefaultNamespace(", "new ResourceLocation("),
    ("Identifier.parse(", "new ResourceLocation("),
    ("Identifier.tryParse(", "ResourceLocation.tryParse("),
    ("net.minecraft.world.entity.EntitySpawnReason", "net.minecraft.world.entity.MobSpawnType"),
    ("EntitySpawnReason", "MobSpawnType"),
    ("MobSpawnType.MOB_SUMMONED", "MobSpawnType.MOB_SUMMONED"),
    (".snapTo(", ".moveTo("),
    ("Blocks.IRON_CHAIN", "Blocks.CHAIN"),
    ("Blocks.SHORT_GRASS", "Blocks.GRASS"),
    ("Blocks.POLISHED_TUFF", "Blocks.POLISHED_ANDESITE"),
    ("Blocks.TUFF_BRICKS", "Blocks.STONE_BRICKS"),
    ("Blocks.CHISELED_TUFF", "Blocks.CHISELED_STONE_BRICKS"),
    ("Blocks.PALE_OAK_PLANKS", "Blocks.BIRCH_PLANKS"),
    ("Blocks.PALE_OAK_LOG", "Blocks.BIRCH_LOG"),
    ("Blocks.PALE_OAK_DOOR", "Blocks.BIRCH_DOOR"),
    ("Blocks.PALE_MOSS_BLOCK", "Blocks.MOSS_BLOCK"),
    ("Blocks.WAXED_COPPER_GRATE", "Blocks.GLASS"),
    ("Items.TRIAL_KEY", "Items.TRIPWIRE_HOOK"),
    ("Items.FLOW_POTTERY_SHERD", "Items.ARCHER_POTTERY_SHERD"),
    ("EntityType.STRAY, EntityType.BOGGED", "EntityType.STRAY"),  # a set holding both: no duplicate once renamed
    ("EntityType.BOGGED", "EntityType.STRAY"),
    ('"minecraft:iron_chain"', '"minecraft:chain"'),
]
REGEX = [
    # type.create(level, reason) -> type.create(level)
    (r"\.create\(([\w.()]+), MobSpawnType\.\w+\)", r".create(\1)"),
    # entity.hurtServer(level, source, amount) -> entity.hurt(source, amount)
    (r"\.hurtServer\([\w.()]+, ", ".hurt("),
    # Identifier as a type
    (r"\bIdentifier\b", "ResourceLocation"),
    # CompoundTag getters with defaults
    (r"\.getBooleanOr\(([^,]+), false\)", r".getBoolean(\1)"),
    (r"\.getIntOr\(([^,]+), 0\)", r".getInt(\1)"),
    (r"\.getLongOr\(([^,]+), 0L?\)", r".getLong(\1)"),
    (r"\.getStringOr\(([^,]+), \"\"\)", r".getString(\1)"),
]
n = 0
for p in glob.glob(root + "/**/*.java", recursive=True):
    s = open(p).read(); o = s
    for a, b in SIMPLE:
        s = s.replace(a, b)
    for a, b in REGEX:
        s = re.sub(a, b, s)
    if s != o:
        open(p, "w").write(s); n += 1
print("fix1201: changed", n, "files")

# ---- Loot tables: 1.20.1 keys them by plain ResourceLocation, through getLootData(). ----
def close_paren(s, i):
    """s[i-1] == '('; returns the index of its matching ')'."""
    depth = 1
    while depth:
        c = s[i]
        if c == '(':
            depth += 1
        elif c == ')':
            depth -= 1
        elif c == '"':
            i += 1
            while s[i] != '"':
                i += 2 if s[i] == '\\' else 1
        i += 1
    return i - 1

KEY = re.compile(r"net\.minecraft\.resources\.ResourceKey\.create\(\s*net\.minecraft\.core\.registries\.Registries\.LOOT_TABLE,\s*")
for p in glob.glob(root + "/**/*.java", recursive=True):
    s = open(p).read(); o = s
    while True:
        m = KEY.search(s)
        if not m:
            break
        open_at = s.index("(", m.start())
        end = close_paren(s, open_at + 1)
        s = s[:m.start()] + s[m.end():end] + s[end + 1:]
    s = s.replace(".reloadableRegistries().getLootTable(", ".getLootData().getLootTable(")
    s = s.replace(".reloadableRegistries()\n                .getLootTable(", ".getLootData()\n                .getLootTable(")
    s = s.replace("net.minecraft.world.RandomizableContainer.setBlockEntityLootTable(",
                  "net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity.setLootTable(")
    if s != o:
        open(p, "w").write(s)

# ---- Renames 1.20.5+ made, undone ----
MORE = [
    (r"\.spawnAtLocation\(\w+, ", ".spawnAtLocation("),
    (r"\.getMinY\(\)", ".getMinBuildHeight()"),
    (r"\.igniteForSeconds\(", ".setSecondsOnFire("),
    (r"\.getCompoundOrEmpty\(", ".getCompound("),
    (r"\.getNewAboutToBeSetTarget\(\)", ".getNewTarget()"),
    (r"MobEffects\.SLOWNESS\b", "MobEffects.MOVEMENT_SLOWDOWN"),
    (r"MobEffects\.STRENGTH\b", "MobEffects.DAMAGE_BOOST"),
    (r"MobEffects\.SPEED\b", "MobEffects.MOVEMENT_SPEED"),
    (r"MobEffects\.RESISTANCE\b", "MobEffects.DAMAGE_RESISTANCE"),
    (r"MobEffects\.HASTE\b", "MobEffects.DIG_SPEED"),
    (r"MobEffects\.JUMP_BOOST\b", "MobEffects.JUMP"),
    (r"MobEffects\.NAUSEA\b", "MobEffects.CONFUSION"),
    (r"MobEffects\.MINING_FATIGUE\b", "MobEffects.DIG_SLOWDOWN"),
    (r"MobEffects\.INSTANT_HEALTH\b", "MobEffects.HEAL"),
    (r"MobEffects\.INSTANT_DAMAGE\b", "MobEffects.HARM"),
    (r"AttributeModifier\.Operation\.ADD_VALUE\b", "AttributeModifier.Operation.ADDITION"),
    (r"AttributeModifier\.Operation\.ADD_MULTIPLIED_BASE\b", "AttributeModifier.Operation.MULTIPLY_BASE"),
    (r"AttributeModifier\.Operation\.ADD_MULTIPLIED_TOTAL\b", "AttributeModifier.Operation.MULTIPLY_TOTAL"),
    (r"Operation\.ADD_VALUE\b", "Operation.ADDITION"),
    (r"Operation\.ADD_MULTIPLIED_BASE\b", "Operation.MULTIPLY_BASE"),
    (r"Operation\.ADD_MULTIPLIED_TOTAL\b", "Operation.MULTIPLY_TOTAL"),
    (r"net\.minecraft\.world\.entity\.projectile\.arrow\.", "net.minecraft.world.entity.projectile."),
    (r"net\.minecraft\.world\.entity\.npc\.wanderingtrader\.", "net.minecraft.world.entity.npc."),
    (r"net\.minecraft\.core\.Holder<net\.minecraft\.world\.effect\.MobEffect>", "net.minecraft.world.effect.MobEffect"),
    (r"Holder<MobEffect>", "MobEffect"),
    (r"net\.minecraft\.core\.Holder<net\.minecraft\.world\.entity\.ai\.attributes\.Attribute>", "net.minecraft.world.entity.ai.attributes.Attribute"),
    (r"Holder<Attribute>", "Attribute"),
    # finalizeSpawn grew a CompoundTag in 1.20.1's signature; the event hook too.
    # Exactly four arguments in (level, difficulty, reason, data): idempotent, so a rerun adds nothing.
    (r"\.finalizeSpawn\(([^;,()]*(?:\([^()]*\))?[^;,()]*,[^;,()]*(?:\([^()]*\))?[^;,()]*,[^;,()]*), null\)(?!,)", r".finalizeSpawn(\1, null, null)"),
]
for p in glob.glob(root + "/**/*.java", recursive=True):
    s = open(p).read(); o = s
    for a, b in MORE:
        s = re.sub(a, b, s)
    if s != o:
        open(p, "w").write(s)

# ---- Pass 3: signatures that changed shape ----
THIRD = [
    (r"net\.minecraft\.resources\.ResourceLocation\.fromNamespaceAndPath\(", "new net.minecraft.resources.ResourceLocation("),
    (r"ResourceLocation\.fromNamespaceAndPath\(", "new ResourceLocation("),
    (r"net\.minecraft\.resources\.new ResourceLocation\(", "new net.minecraft.resources.ResourceLocation("),
    # teleportTo(level, x, y, z, Set.of(), yaw, pitch, false) -> teleportTo(level, x, y, z, yaw, pitch)
    (r"\.teleportTo\(([^;]*?),\s*(?:java\.util\.)?Set\.of\(\),\s*([^;]*?),\s*false\)", r".teleportTo(\1, \2)"),
    (r"(SoundEvents\.\w+)\.value\(\)", r"\1"),
    (r"\beffect\.value\(\)", "effect"),
    (r"(\w+)\.getIntOr\((\"[^\"]+\"), ([^)]+)\)", r"(\1.contains(\2) ? \1.getInt(\2) : \3)"),
    (r"(\w+)\.getStringOr\((\"[^\"]+\"), ([^)]+)\)", r"(\1.contains(\2) ? \1.getString(\2) : \3)"),
    (r"\.identifier\(\)", ".location()"),
    (r"\.lookupOrThrow\((\w+\.)?(Registries\.\w+)\)\.getValue\(", r".registryOrThrow(\1\2).get("),
    (r"Commands\.hasPermission\(Commands\.LEVEL_GAMEMASTERS\)\.test\((\w+)\)", r"\1.hasPermission(2)"),
    (r"net\.minecraftforge\.event\.entity\.living\.MobSpawnEvent\.(?:SpawnPlacementCheck|PositionCheck)\.Result\.SUCCEED",
     "net.minecraftforge.eventbus.api.Event.Result.ALLOW"),
    (r"net\.minecraftforge\.event\.entity\.living\.MobSpawnEvent\.(?:SpawnPlacementCheck|PositionCheck)\.Result\.FAIL",
     "net.minecraftforge.eventbus.api.Event.Result.DENY"),
    (r"\.sendParticles\((\w+), (\w+), (false|true), (?:false|true), ", r".sendParticles(\1, \2, \3, "),
    (r"net\.minecraft\.world\.level\.block\.ChiseledBookShelfBlock\.FACING", "net.minecraft.world.level.block.HorizontalDirectionalBlock.FACING"),
    (r"BlockStateParser\.parseForBlock\(BuiltInRegistries\.BLOCK,", "BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK.asLookup(),"),
    (r"\.blockInteractionRange\(\)", ".getBlockReach()"),
    (r"new Arrow\((\w+), (\w+), (\w+), (\w+), new ItemStack\(Items\.ARROW\), null\)", r"new Arrow(\1, \2, \3, \4)"),
]
for p in glob.glob(root + "/**/*.java", recursive=True):
    s = open(p).read(); o = s
    for a, b in THIRD:
        s = re.sub(a, b, s)
    if s != o:
        open(p, "w").write(s)
