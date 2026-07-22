package org.slowtrees.treeevolution;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.bukkit.Material;

public final class TreeShapeSmokeTest {
    private static final Path OUT = Path.of("target", "tree-shape-smoke");
    private static final TreeEvolutionPlanner TREE_PLANNER = new TreeEvolutionPlanner();
    private static final TreeShapeEngine SHAPE_ENGINE = new TreeShapeEngine();

    private TreeShapeSmokeTest() {
    }

    public static void main(String[] args) throws Exception {
        Files.createDirectories(OUT);
        List<String> summary = new ArrayList<>();
        summary.add("## Simulated tree shape smoke test. These are generated target plans, not copied schematics.");
        summary.add("species,stage,wood,leaves,branches,floatingWood,unanchoredBranchSegments,branchTips,coveredBranchTips,topCovered,leafWoodRatio,normalEnough,png,txt");

        for (TreeSpecies species : TreeSpecies.values()) {
            for (TreeMaturityStage stage : TreeMaturityStage.values()) {
                TreeDna dna = sampleDna(species, stage);
                TreePlan plan = treeBodyPlan(dna);
                TreeShapeEngine.ShapeReport report = SHAPE_ENGINE.analyze(plan, dna);
                String name = species.id() + "-" + stage.name().toLowerCase();
                Path png = OUT.resolve(name + ".png");
                Path txt = OUT.resolve(name + ".txt");
                renderPng(plan, dna, report, png);
                Files.writeString(txt, renderText(plan, dna, report));
                summary.add(String.join(",",
                        species.id(),
                        stage.name(),
                        String.valueOf(report.wood()),
                        String.valueOf(report.leaves()),
                        String.valueOf(report.branches()),
                        String.valueOf(report.floatingWood()),
                        String.valueOf(report.unanchoredBranchSegments()),
                        String.valueOf(report.branchTips()),
                        String.valueOf(report.coveredBranchTips()),
                        String.valueOf(report.topCovered()),
                        String.valueOf(round(report.leafWoodRatio())),
                        String.valueOf(report.normalEnough()),
                        png.toString().replace('\\', '/'),
                        txt.toString().replace('\\', '/')
                ));
            }
        }
        Files.writeString(OUT.resolve("summary.csv"), String.join(System.lineSeparator(), summary) + System.lineSeparator());
        writeBiomeScenarioRenders();
        System.out.println("Wrote tree shape smoke renders to " + OUT.toAbsolutePath());
    }

    private static void writeBiomeScenarioRenders() throws IOException {
        Path biomeOut = OUT.resolve("biome-scenarios");
        Files.createDirectories(biomeOut);
        List<String> summary = new ArrayList<>();
        summary.add("## Fake BiomeContext smoke renders. These show tree body plus biome ground/detail personality without needing a live Paper biome registry.");
        summary.add("scenario,species,stage,path,wood,leaves,branches,floatingWood,unanchoredBranchSegments,branchTips,coveredBranchTips,topCovered,normalEnough,png,txt");

        for (BiomeSmokeContext context : biomeScenarios()) {
            for (TreeMaturityStage stage : List.of(TreeMaturityStage.MEDIUM, TreeMaturityStage.MATURE, TreeMaturityStage.ANCIENT)) {
                TreeDna dna = sampleDna(context.species(), stage, context);
                TreePlan plan = treeBodyPlan(dna);
                TreeShapeEngine.ShapeReport report = SHAPE_ENGINE.analyze(plan, dna);
                String name = context.id() + "-" + stage.name().toLowerCase();
                Path png = biomeOut.resolve(name + ".png");
                Path txt = biomeOut.resolve(name + ".txt");
                renderBiomePng(plan, dna, report, context, png);
                Files.writeString(txt, renderBiomeText(plan, dna, report, context));
                summary.add(String.join(",",
                        context.id(),
                        context.species().id(),
                        stage.name(),
                        context.pathName(),
                        String.valueOf(report.wood()),
                        String.valueOf(report.leaves()),
                        String.valueOf(report.branches()),
                        String.valueOf(report.floatingWood()),
                        String.valueOf(report.unanchoredBranchSegments()),
                        String.valueOf(report.branchTips()),
                        String.valueOf(report.coveredBranchTips()),
                        String.valueOf(report.topCovered()),
                        String.valueOf(report.normalEnough()),
                        png.toString().replace('\\', '/'),
                        txt.toString().replace('\\', '/')
                ));
            }
        }
        Files.writeString(biomeOut.resolve("summary.csv"), String.join(System.lineSeparator(), summary) + System.lineSeparator());
    }

    private static TreePlan treeBodyPlan(TreeDna dna) {
        return TREE_PLANNER.plan(dna, null, false);
    }

    private static TreeDna sampleDna(TreeSpecies species, TreeMaturityStage stage) {
        return sampleDna(species, stage, BiomeSmokeContext.defaultFor(species));
    }

    private static TreeDna sampleDna(TreeSpecies species, TreeMaturityStage stage, BiomeSmokeContext context) {
        int targetHeight = switch (species) {
            case OAK -> 13;
            case BIRCH -> 15;
            case SPRUCE -> 18;
            case JUNGLE -> 28;
            case ACACIA -> 12;
            case DARK_OAK -> 14;
            case MANGROVE -> 16;
            case CHERRY -> 13;
        };
        targetHeight += context.heightBonus();
        int branchCount = switch (species) {
            case BIRCH -> 2;
            case SPRUCE -> 7;
            case JUNGLE -> 7;
            case DARK_OAK -> 7;
            default -> 5;
        };
        int radiusX = switch (species) {
            case BIRCH -> 2;
            case SPRUCE -> 3;
            case JUNGLE -> 4;
            case ACACIA -> 4;
            case DARK_OAK -> 5;
            case CHERRY -> 5;
            default -> 3;
        };
        int radiusY = species == TreeSpecies.ACACIA || species == TreeSpecies.CHERRY ? 1 : 2;
        int radiusZ = species == TreeSpecies.ACACIA ? 3 : radiusX;
        radiusX = Math.max(1, radiusX + context.canopyRadiusBonus());
        radiusZ = Math.max(1, radiusZ + context.canopyRadiusBonus());
        int trunkWidth = species == TreeSpecies.JUNGLE && stage.ordinal() >= TreeMaturityStage.MATURE.ordinal() ? 4 : 1;
        int layerCount = switch (species) {
            case SPRUCE -> 3;
            case CHERRY -> 2;
            case JUNGLE -> stage.ordinal() >= TreeMaturityStage.MATURE.ordinal() ? 2 : 1;
            default -> 0;
        };
        TreePersonality personality = context.personality() == null ? switch (species) {
            case ACACIA -> TreePersonality.UMBRELLA;
            case JUNGLE -> TreePersonality.ANCIENT_LANDMARK;
            case DARK_OAK -> TreePersonality.DENSE;
            case CHERRY -> TreePersonality.WIDE;
            default -> TreePersonality.BALANCED;
        } : context.personality();
        TreeRarity rarity = species == TreeSpecies.JUNGLE && stage.ordinal() >= TreeMaturityStage.MATURE.ordinal()
                ? TreeRarity.LANDMARK
                : TreeRarity.COMMON;
        return new TreeDna(
                UUID.nameUUIDFromBytes(("smoke-" + species.id()).getBytes()),
                0,
                64,
                0,
                species,
                context.seed() ^ 0x5EEDL ^ species.ordinal() * 131L ^ stage.ordinal() * 17L,
                personality,
                rarity,
                targetHeight,
                branchCount,
                2,
                species == TreeSpecies.ACACIA || species == TreeSpecies.JUNGLE ? 5 : 4,
                species.ordinal(),
                Math.max(radiusX, radiusZ),
                radiusX,
                radiusY,
                radiusZ,
                Math.max(0.35D, Math.min(0.92D, (species == TreeSpecies.BIRCH ? 0.58D : 0.74D) + context.canopyDensityBonus())),
                species == TreeSpecies.SPRUCE ? 0.38D : 0.55D,
                species == TreeSpecies.BIRCH ? 0.12D : 0.35D,
                0.0D,
                species == TreeSpecies.JUNGLE || context.pathName().contains("tropical") ? 0.24D : context.vineChance(),
                context.detailChance(),
                trunkWidth,
                layerCount,
                Math.max(radiusX, radiusZ),
                species == TreeSpecies.ACACIA ? 1 : 0,
                0,
                species == TreeSpecies.ACACIA ? 0.42D : 0.72D,
                context.id() + "-smoke-profile",
                "TreeShapeSmokeTest/" + context.pathName(),
                "wild",
                0,
                TreeDna.CURRENT_SHAPE_REVISION,
                TreeGrowthIntent.HEIGHT,
                0,
                0,
                0,
                0,
                0,
                0,
                switch (stage) {
                    case SMALL -> 2;
                    case MEDIUM -> 12;
                    case MATURE -> 40;
                    case ANCIENT -> 240;
                },
                stage,
                0L,
                0L,
                0,
                true
        );
    }

    private static void renderPng(TreePlan plan, TreeDna dna, TreeShapeEngine.ShapeReport report, Path path) throws IOException {
        List<PlannedTreeBlock> blocks = plan.orderedBlocks().stream()
                .sorted(Comparator.comparingInt(PlannedTreeBlock::y)
                        .thenComparingInt(PlannedTreeBlock::z)
                        .thenComparingInt(PlannedTreeBlock::x))
                .toList();
        BufferedImage image = new BufferedImage(1100, 820, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g.setColor(new Color(32, 38, 46));
        g.drawString(dna.species().id() + " " + dna.maturityStage()
                + " normal=" + report.normalEnough()
                + " floatingWood=" + report.floatingWood()
                + " topCovered=" + report.topCovered()
                + " leafWood=" + round(report.leafWoodRatio()), 24, 28);

        int originX = image.getWidth() / 2;
        int originY = 720;
        int scaleX = 14;
        int scaleY = 7;
        int blockH = 9;
        for (PlannedTreeBlock block : blocks) {
            int sx = originX + (block.x() - block.z()) * scaleX;
            int sy = originY - (block.y() - dna.baseY()) * blockH + (block.x() + block.z()) * scaleY;
            drawBlock(g, sx, sy, colorForBlock(block));
        }
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void renderBiomePng(TreePlan plan, TreeDna dna, TreeShapeEngine.ShapeReport report, BiomeSmokeContext context, Path path) throws IOException {
        List<PlannedTreeBlock> blocks = plan.orderedBlocks().stream()
                .sorted(Comparator.comparingInt(PlannedTreeBlock::y)
                        .thenComparingInt(PlannedTreeBlock::z)
                        .thenComparingInt(PlannedTreeBlock::x))
                .toList();
        BufferedImage image = new BufferedImage(1220, 900, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(245, 247, 250));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        g.setColor(new Color(32, 38, 46));
        g.drawString(context.label() + " | " + dna.species().id() + " " + dna.maturityStage()
                + " | path=" + context.pathName()
                + " | normal=" + report.normalEnough()
                + " | floatingWood=" + report.floatingWood()
                + " | topCovered=" + report.topCovered(), 24, 28);

        int originX = image.getWidth() / 2;
        int originY = 760;
        int scaleX = 14;
        int scaleY = 7;
        int blockH = 9;
        renderBiomeGround(g, context, dna, originX, originY, scaleX, scaleY);
        for (PlannedTreeBlock block : blocks) {
            int sx = originX + (block.x() - block.z()) * scaleX;
            int sy = originY - (block.y() - dna.baseY()) * blockH + (block.x() + block.z()) * scaleY;
            drawBlock(g, sx, sy, colorForBlock(block));
        }
        renderLegend(g, context, 24, 54);
        g.dispose();
        ImageIO.write(image, "png", path.toFile());
    }

    private static void renderBiomeGround(Graphics2D g, BiomeSmokeContext context, TreeDna dna, int originX, int originY, int scaleX, int scaleY) {
        Random random = new Random(context.seed() ^ dna.seed());
        int radius = context.groundRadius();
        for (int z = -radius; z <= radius; z++) {
            for (int x = -radius; x <= radius; x++) {
                int distance = (x * x) + (z * z);
                if (distance > radius * radius + random.nextInt(12)) {
                    continue;
                }
                Material ground = context.groundAt(distance, random);
                int sx = originX + (x - z) * scaleX;
                int sy = originY + (x + z) * scaleY;
                drawFlatTile(g, sx, sy, colorForMaterial(ground));
                if (random.nextDouble() < context.detailChanceFor(distance)) {
                    Material detail = context.detailAt(distance, random);
                    drawDetail(g, sx, sy - 9, colorForMaterial(detail));
                }
            }
        }
    }

    private static void drawFlatTile(Graphics2D g, int x, int y, Color color) {
        int w = 14;
        int h = 7;
        int[] xs = {x, x + w, x, x - w};
        int[] ys = {y - h, y, y + h, y};
        g.setColor(color);
        g.fillPolygon(xs, ys, 4);
        g.setColor(new Color(42, 47, 54, 45));
        g.drawPolygon(xs, ys, 4);
    }

    private static void drawDetail(Graphics2D g, int x, int y, Color color) {
        g.setColor(color);
        g.fillOval(x - 4, y - 4, 8, 8);
        g.setColor(new Color(42, 47, 54, 70));
        g.drawOval(x - 4, y - 4, 8, 8);
    }

    private static void renderLegend(Graphics2D g, BiomeSmokeContext context, int x, int y) {
        g.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        g.setColor(new Color(32, 38, 46));
        g.drawString("ground=" + context.groundLegend(), x, y);
        g.drawString("detail=" + context.detailLegend(), x, y + 18);
        g.drawString("## Fake context only; validates biome personality render without a live server biome registry.", x, y + 36);
    }

    private static void drawBlock(Graphics2D g, int x, int y, Color color) {
        int w = 14;
        int h = 10;
        int[] xs = {x, x + w, x, x - w};
        int[] ys = {y - h, y, y + h, y};
        g.setColor(color);
        g.fillPolygon(xs, ys, 4);
        g.setColor(new Color(42, 47, 54, 90));
        g.setStroke(new BasicStroke(1.0F));
        g.drawPolygon(xs, ys, 4);
    }

    private static String renderText(TreePlan plan, TreeDna dna, TreeShapeEngine.ShapeReport report) {
        StringBuilder text = new StringBuilder();
        text.append("## Simulated 3D target plan for ").append(dna.species().id()).append(" ").append(dna.maturityStage()).append(System.lineSeparator());
        text.append("normalEnough=").append(report.normalEnough())
                .append(" wood=").append(report.wood())
                .append(" leaves=").append(report.leaves())
                .append(" branches=").append(report.branches())
                .append(" floatingWood=").append(report.floatingWood())
                .append(" unanchoredBranchSegments=").append(report.unanchoredBranchSegments())
                .append(" branchTips=").append(report.branchTips())
                .append(" coveredBranchTips=").append(report.coveredBranchTips())
                .append(" topCovered=").append(report.topCovered())
                .append(" leafWoodRatio=").append(round(report.leafWoodRatio()))
                .append(System.lineSeparator());
        int minY = plan.orderedBlocks().stream().mapToInt(PlannedTreeBlock::y).min().orElse(dna.baseY());
        int maxY = plan.orderedBlocks().stream().mapToInt(PlannedTreeBlock::y).max().orElse(dna.baseY());
        int radius = 10;
        for (int y = maxY; y >= minY; y--) {
            String layer = layer(plan.orderedBlocks(), dna, y, radius);
            if (!layer.isBlank()) {
                text.append("y=").append(y).append(" rel=").append(y - dna.baseY()).append(System.lineSeparator());
                text.append(layer);
            }
        }
        return text.toString();
    }

    private static String renderBiomeText(TreePlan plan, TreeDna dna, TreeShapeEngine.ShapeReport report, BiomeSmokeContext context) {
        return "## Simulated biome tree render for " + context.label() + System.lineSeparator()
                + "path=" + context.pathName()
                + " ground=" + context.groundLegend()
                + " detail=" + context.detailLegend()
                + System.lineSeparator()
                + renderText(plan, dna, report);
    }

    private static String layer(List<PlannedTreeBlock> blocks, TreeDna dna, int y, int radius) {
        StringBuilder text = new StringBuilder();
        boolean hasContent = false;
        for (int z = -radius; z <= radius; z++) {
            StringBuilder row = new StringBuilder();
            for (int x = -radius; x <= radius; x++) {
                char token = '.';
                for (PlannedTreeBlock block : blocks) {
                    if (block.x() == x && block.y() == y && block.z() == z) {
                        token = tokenFor(block);
                    }
                }
                if (x == dna.baseX() && y == dna.baseY() && z == dna.baseZ()) {
                    token = token == '.' ? 'O' : Character.toLowerCase(token);
                }
                if (token != '.') {
                    hasContent = true;
                }
                row.append(token);
            }
            text.append(row).append(System.lineSeparator());
        }
        return hasContent ? text.toString() : "";
    }

    private static char tokenFor(PlannedTreeBlock block) {
        if (block.role() == TreeBlockRole.BRANCH && block.branchStep() == 1) {
            return 'A';
        }
        return switch (block.role()) {
            case TRUNK -> 'T';
            case BRANCH -> 'B';
            case CANOPY -> 'L';
            case ROOT -> 'R';
            case VINE -> 'V';
            case GROUND_DETAIL -> 'U';
            case FALLEN_LOG -> 'F';
            case SAPLING -> 'S';
        };
    }

    private static Color colorFor(TreeBlockRole role) {
        return switch (role) {
            case TRUNK -> new Color(116, 78, 43);
            case BRANCH -> new Color(102, 66, 34);
            case CANOPY -> new Color(65, 143, 72, 210);
            case ROOT -> new Color(94, 70, 44);
            case VINE -> new Color(53, 129, 54);
            case GROUND_DETAIL -> new Color(119, 165, 73);
            case FALLEN_LOG -> new Color(130, 91, 52);
            case SAPLING -> new Color(82, 164, 84);
        };
    }

    private static Color colorForBlock(PlannedTreeBlock block) {
        if (block.role() == TreeBlockRole.CANOPY) {
            return switch (block.material()) {
                case CHERRY_LEAVES -> new Color(224, 143, 171, 218);
                case SPRUCE_LEAVES -> new Color(50, 104, 70, 218);
                case JUNGLE_LEAVES -> new Color(53, 137, 70, 218);
                case ACACIA_LEAVES -> new Color(75, 139, 76, 218);
                case DARK_OAK_LEAVES -> new Color(45, 103, 58, 222);
                case MANGROVE_LEAVES -> new Color(58, 123, 67, 222);
                case BIRCH_LEAVES -> new Color(111, 159, 76, 214);
                default -> new Color(65, 143, 72, 210);
            };
        }
        return colorFor(block.role());
    }

    private static Color colorForMaterial(Material material) {
        return switch (material) {
            case GRASS_BLOCK -> new Color(101, 157, 73);
            case PODZOL -> new Color(110, 89, 55);
            case MOSS_BLOCK, MOSS_CARPET -> new Color(79, 135, 69);
            case ROOTED_DIRT, COARSE_DIRT, DIRT -> new Color(125, 92, 58);
            case MUD -> new Color(82, 76, 73);
            case SAND -> new Color(210, 190, 126);
            case TERRACOTTA -> new Color(164, 96, 70);
            case GRAVEL -> new Color(137, 132, 126);
            case SHORT_GRASS, TALL_GRASS, FERN, LARGE_FERN -> new Color(74, 147, 69);
            case DANDELION -> new Color(230, 203, 67);
            case POPPY -> new Color(201, 54, 45);
            case OXEYE_DAISY, LILY_OF_THE_VALLEY, AZURE_BLUET -> new Color(234, 234, 218);
            case BLUE_ORCHID -> new Color(89, 151, 205);
            case PINK_PETALS -> new Color(229, 142, 174);
            case DEAD_BUSH -> new Color(143, 111, 62);
            case CACTUS -> new Color(66, 129, 74);
            case SUGAR_CANE -> new Color(151, 200, 87);
            case BAMBOO -> new Color(117, 172, 56);
            case MELON -> new Color(107, 158, 64);
            case PUMPKIN -> new Color(212, 123, 43);
            case BROWN_MUSHROOM -> new Color(129, 94, 73);
            case RED_MUSHROOM -> new Color(191, 63, 60);
            case LEAF_LITTER -> new Color(161, 96, 50);
            default -> new Color(124, 154, 94);
        };
    }

    private static List<BiomeSmokeContext> biomeScenarios() {
        return List.of(
                new BiomeSmokeContext("oak-plains", "oak + plains", TreeSpecies.OAK, "temperate open meadow", TreePersonality.BALANCED, 0, 0, 0.01D, 0.02D, 5, 8,
                        List.of(Material.GRASS_BLOCK, Material.GRASS_BLOCK, Material.ROOTED_DIRT),
                        List.of(Material.SHORT_GRASS, Material.TALL_GRASS, Material.DANDELION, Material.POPPY, Material.AZURE_BLUET, Material.PUMPKIN),
                        0x1001L),
                new BiomeSmokeContext("oak-forest", "oak + forest", TreeSpecies.OAK, "temperate young woodland", TreePersonality.DENSE, 2, 1, 0.05D, 0.08D, 7, 9,
                        List.of(Material.GRASS_BLOCK, Material.ROOTED_DIRT, Material.MOSS_BLOCK),
                        List.of(Material.FERN, Material.SHORT_GRASS, Material.MOSS_CARPET, Material.BROWN_MUSHROOM, Material.LEAF_LITTER, Material.OXEYE_DAISY),
                        0x1002L),
                new BiomeSmokeContext("spruce-taiga", "spruce + taiga", TreeSpecies.SPRUCE, "cold conifer taiga", TreePersonality.DENSE, 3, 0, -0.03D, 0.04D, 7, 10,
                        List.of(Material.PODZOL, Material.PODZOL, Material.MOSS_BLOCK, Material.COARSE_DIRT),
                        List.of(Material.FERN, Material.LARGE_FERN, Material.SWEET_BERRY_BUSH, Material.LEAF_LITTER, Material.BROWN_MUSHROOM),
                        0x1003L),
                new BiomeSmokeContext("jungle-jungle", "jungle + jungle", TreeSpecies.JUNGLE, "tropical dense jungle", TreePersonality.ANCIENT_LANDMARK, 8, 2, 0.06D, 0.24D, 9, 12,
                        List.of(Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.ROOTED_DIRT),
                        List.of(Material.FERN, Material.SHORT_GRASS, Material.BAMBOO, Material.MELON, Material.VINE, Material.BROWN_MUSHROOM),
                        0x1004L),
                new BiomeSmokeContext("acacia-savanna", "acacia + savanna", TreeSpecies.ACACIA, "dry savanna scrub", TreePersonality.SPARSE, 0, -1, -0.18D, 0.01D, 6, 7,
                        List.of(Material.GRASS_BLOCK, Material.COARSE_DIRT, Material.SAND),
                        List.of(Material.SHORT_GRASS, Material.DEAD_BUSH, Material.POPPY, Material.DANDELION, Material.CACTUS),
                        0x1005L),
                new BiomeSmokeContext("mangrove-swamp", "mangrove + swamp", TreeSpecies.MANGROVE, "wetland swamp thicket", TreePersonality.DENSE, 2, 1, 0.04D, 0.12D, 7, 10,
                        List.of(Material.MUD, Material.MUD, Material.GRASS_BLOCK, Material.MOSS_BLOCK),
                        List.of(Material.BLUE_ORCHID, Material.FERN, Material.SUGAR_CANE, Material.BROWN_MUSHROOM, Material.MOSS_CARPET),
                        0x1006L),
                new BiomeSmokeContext("cherry-grove", "cherry + cherry_grove", TreeSpecies.CHERRY, "cherry grove", TreePersonality.WIDE, 1, 2, 0.08D, 0.04D, 7, 10,
                        List.of(Material.GRASS_BLOCK, Material.MOSS_BLOCK, Material.ROOTED_DIRT),
                        List.of(Material.PINK_PETALS, Material.SHORT_GRASS, Material.FERN, Material.POPPY, Material.DANDELION),
                        0x1007L)
        );
    }

    private static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }

    private record BiomeSmokeContext(
            String id,
            String label,
            TreeSpecies species,
            String pathName,
            TreePersonality personality,
            int heightBonus,
            int canopyRadiusBonus,
            double canopyDensityBonus,
            double vineChance,
            int groundRadius,
            int detailDensity,
            List<Material> groundPalette,
            List<Material> detailPalette,
            long seed
    ) {
        private static BiomeSmokeContext defaultFor(TreeSpecies species) {
            return new BiomeSmokeContext(species.id() + "-default", species.id() + " default", species, "species-only", null, 0, 0, 0.0D, 0.0D, 5, 6,
                    List.of(Material.GRASS_BLOCK, Material.ROOTED_DIRT),
                    List.of(Material.SHORT_GRASS, Material.FERN, Material.LEAF_LITTER),
                    0xCAFE0000L + species.ordinal());
        }

        private double detailChance() {
            return Math.min(0.75D, Math.max(0.08D, detailDensity / 18.0D));
        }

        private double detailChanceFor(int distanceSquared) {
            double distanceScale = distanceSquared <= 9 ? 1.15D : distanceSquared > groundRadius * groundRadius * 0.72D ? 0.65D : 1.0D;
            return Math.min(0.82D, detailChance() * distanceScale);
        }

        private Material groundAt(int distanceSquared, Random random) {
            if (distanceSquared <= 6 && groundPalette.contains(Material.MOSS_BLOCK) && random.nextInt(100) < 36) {
                return Material.MOSS_BLOCK;
            }
            return groundPalette.get(random.nextInt(groundPalette.size()));
        }

        private Material detailAt(int distanceSquared, Random random) {
            if (distanceSquared > groundRadius * groundRadius * 0.70D && detailPalette.contains(Material.SHORT_GRASS) && random.nextBoolean()) {
                return Material.SHORT_GRASS;
            }
            return detailPalette.get(random.nextInt(detailPalette.size()));
        }

        private String groundLegend() {
            return materialList(groundPalette);
        }

        private String detailLegend() {
            return materialList(detailPalette);
        }

        private static String materialList(List<Material> materials) {
            return materials.stream().map(Material::name).map(String::toLowerCase).distinct().toList().toString();
        }
    }
}
