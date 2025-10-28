package com.matochess.managers;

import com.matochess.MatoChessPlugin;
import com.matochess.data.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 체스판 생성 및 관리자
 * 40개의 체스판을 생성하고 할당/반환을 관리합니다
 */
public class ArenaManager {

    private final MatoChessPlugin plugin;
    private final List<Arena> arenas;

    public ArenaManager(MatoChessPlugin plugin) {
        this.plugin = plugin;
        this.arenas = new ArrayList<>();
    }

    /**
     * 플러그인 시작 시 기본 월드 및 보드판 초기화
     * 커스텀 보드판이 없으면 잔디블럭으로 기본 보드판 생성
     */
    public boolean initializeDefaultWorld() {
        String worldName = "matochessWorld";

        // 이미 월드가 있는지 확인
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            plugin.getLogger().info("Arena world already exists: " + worldName);

            // 보드판이 이미 생성되어 있는지 확인
            if (!arenas.isEmpty()) {
                plugin.getLogger().info("Arenas already initialized (" + arenas.size() + " boards)");
                return true;
            }

            // 보드판이 없으면 생성
            return generateDefaultArenas(existingWorld);
        }

        // 새 월드 생성
        plugin.getLogger().info("Creating new arena world: " + worldName);
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        // 완전히 빈 슈퍼플랫 월드 (void 프리셋)
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}");

        World arenaWorld = Bukkit.createWorld(creator);
        if (arenaWorld == null) {
            plugin.getLogger().severe("Failed to create arena world!");
            return false;
        }

        // 월드 설정
        arenaWorld.setKeepSpawnInMemory(true);
        arenaWorld.setAutoSave(false);

        // config에 저장
        plugin.getConfig().set("arena.arena-world", worldName);
        plugin.saveConfig();

        plugin.getLogger().info("Arena world created successfully!");

        // 기본 보드판 생성
        return generateDefaultArenas(arenaWorld);
    }

    /**
     * 기본 보드판 40개 생성 (잔디블럭 8x6)
     */
    private boolean generateDefaultArenas(World arenaWorld) {
        plugin.getLogger().info("Generating 40 default grass boards (8x6)...");

        // 기존 아레나 초기화
        arenas.clear();

        int maxArenas = plugin.getConfig().getInt("arena.max-arenas", 40);
        int spacing = plugin.getConfig().getInt("arena.arena-spacing", 30);

        int boardWidth = 8;
        int boardLength = 6;
        int columns = 8; // 한 줄에 8개
        int startY = 100; // 하늘 높이

        for (int i = 0; i < maxArenas; i++) {
            int row = i / columns;
            int col = i % columns;

            // 위치 계산
            int startX = col * (boardWidth + spacing);
            int startZ = row * (boardLength + spacing);

            // 잔디블럭으로 8x6 보드판 생성
            for (int x = 0; x < boardWidth; x++) {
                for (int z = 0; z < boardLength; z++) {
                    Block block = arenaWorld.getBlockAt(startX + x, startY, startZ + z);
                    block.setType(Material.GRASS_BLOCK);
                }
            }

            // Arena 객체 생성
            Location pos1 = new Location(arenaWorld, startX, startY, startZ);
            Location pos2 = new Location(arenaWorld, startX + boardWidth - 1, startY, startZ + boardLength - 1);
            Arena arena = new Arena(i, pos1, pos2);
            arenas.add(arena);

            if ((i + 1) % 10 == 0) {
                plugin.getLogger().info("Default boards created: " + (i + 1) + "/" + maxArenas);
            }
        }

        plugin.getLogger().info("All 40 default boards created successfully!");
        return true;
    }

    /**
     * 체스판 템플릿이 설정되어 있는지 확인
     */
    public boolean isBoardTemplateConfigured() {
        FileConfiguration config = plugin.getConfig();
        String world = config.getString("arena.board-template.world", "");
        return !world.isEmpty();
    }

    /**
     * 아레나 월드가 설정되어 있는지 확인
     */
    public boolean isArenaWorldConfigured() {
        FileConfiguration config = plugin.getConfig();
        String world = config.getString("arena.arena-world", "");
        return !world.isEmpty();
    }

    /**
     * 커스텀 보드판으로 새 월드 재생성
     * /mcadmin setworld 명령어로 호출됩니다
     */
    public boolean createWorldAndGenerateArenas() {
        // 커스텀 보드판이 설정되어 있는지 확인
        if (!isBoardTemplateConfigured()) {
            plugin.getLogger().warning("커스텀 보드판이 설정되지 않았습니다!");
            plugin.getLogger().warning("먼저 /mcadmin setboard 명령어를 사용하세요");
            return false;
        }

        String worldName = "matochessWorld";

        // 기존 월드 언로드
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            plugin.getLogger().info("기존 월드를 언로드합니다: " + worldName);
            Bukkit.unloadWorld(existingWorld, false);
        }

        // 월드 폴더 삭제
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists()) {
            plugin.getLogger().info("기존 월드 폴더를 삭제합니다...");
            deleteDirectory(worldFolder);
        }

        // 새 빈 월드 생성
        plugin.getLogger().info("새로운 빈 월드를 생성합니다: " + worldName);
        WorldCreator creator = new WorldCreator(worldName);
        creator.type(WorldType.FLAT);
        creator.generateStructures(false);
        // 완전히 빈 슈퍼플랫 월드 (void 프리셋)
        creator.generatorSettings("{\"layers\":[{\"block\":\"minecraft:air\",\"height\":1}],\"biome\":\"minecraft:plains\"}");

        World arenaWorld = Bukkit.createWorld(creator);
        if (arenaWorld == null) {
            plugin.getLogger().severe("월드 생성 실패!");
            return false;
        }

        // 월드 설정
        arenaWorld.setKeepSpawnInMemory(true);
        arenaWorld.setAutoSave(false);

        // config에 월드 이름 저장
        plugin.getConfig().set("arena.arena-world", worldName);
        plugin.saveConfig();

        plugin.getLogger().info("월드 생성 완료! 커스텀 체스판 생성을 시작합니다...");

        // 커스텀 체스판 생성
        return generateCustomArenas();
    }

    /**
     * 디렉토리 재귀 삭제
     */
    private void deleteDirectory(File directory) {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    /**
     * 커스텀 보드판 40개 생성 (템플릿 복사)
     * createWorldAndGenerateArenas()에서 호출됩니다
     */
    public boolean generateCustomArenas() {
        if (!isBoardTemplateConfigured()) {
            plugin.getLogger().warning("체스판 템플릿이 설정되지 않았습니다!");
            return false;
        }

        if (!isArenaWorldConfigured()) {
            plugin.getLogger().warning("아레나 월드가 설정되지 않았습니다!");
            return false;
        }

        FileConfiguration config = plugin.getConfig();

        // 템플릿 정보 로드
        String templateWorldName = config.getString("arena.board-template.world");
        World templateWorld = Bukkit.getWorld(templateWorldName);
        if (templateWorld == null) {
            plugin.getLogger().warning("템플릿 월드를 찾을 수 없습니다: " + templateWorldName);
            return false;
        }

        double tx1 = config.getDouble("arena.board-template.pos1.x");
        double ty1 = config.getDouble("arena.board-template.pos1.y");
        double tz1 = config.getDouble("arena.board-template.pos1.z");
        double tx2 = config.getDouble("arena.board-template.pos2.x");
        double ty2 = config.getDouble("arena.board-template.pos2.y");
        double tz2 = config.getDouble("arena.board-template.pos2.z");

        Location templatePos1 = new Location(templateWorld, tx1, ty1, tz1);
        Location templatePos2 = new Location(templateWorld, tx2, ty2, tz2);

        // 아레나 월드 로드
        String arenaWorldName = config.getString("arena.arena-world", "matochessWorld");
        World arenaWorld = Bukkit.getWorld(arenaWorldName);
        if (arenaWorld == null) {
            plugin.getLogger().warning("아레나 월드를 찾을 수 없습니다: " + arenaWorldName);
            plugin.getLogger().warning("먼저 /mcadmin setworld 명령어로 월드를 생성하세요");
            return false;
        }

        int maxArenas = config.getInt("arena.max-arenas", 40);
        int spacing = config.getInt("arena.arena-spacing", 30);

        // 기존 아레나 초기화
        arenas.clear();

        // 체스판 크기 계산
        int templateWidth = (int) Math.abs(tx2 - tx1) + 1;
        int templateHeight = (int) Math.abs(ty2 - ty1) + 1;
        int templateLength = (int) Math.abs(tz2 - tz1) + 1;

        plugin.getLogger().info("체스판 생성 시작... (크기: " + templateWidth + "x" + templateHeight + "x" + templateLength + ")");

        // 40개 체스판을 8x5 그리드로 배치
        int columns = 8; // 한 줄에 8개
        int startY = 100; // 하늘 높이

        for (int i = 0; i < maxArenas; i++) {
            int row = i / columns;
            int col = i % columns;

            // 새 위치 계산
            int newX = col * (templateWidth + spacing);
            int newY = startY;
            int newZ = row * (templateLength + spacing);

            Location newPos1 = new Location(arenaWorld, newX, newY, newZ);
            Location newPos2 = new Location(arenaWorld,
                newX + templateWidth - 1,
                newY + templateHeight - 1,
                newZ + templateLength - 1);

            // 블록 복사
            copyBlocks(templatePos1, templatePos2, newPos1);

            // Arena 객체 생성 및 추가
            Arena arena = new Arena(i, newPos1, newPos2);
            arenas.add(arena);

            if ((i + 1) % 10 == 0) {
                plugin.getLogger().info("체스판 생성 진행중... " + (i + 1) + "/" + maxArenas);
            }
        }

        plugin.getLogger().info("체스판 생성 완료! 총 " + maxArenas + "개");
        return true;
    }

    /**
     * 블록 복사 (템플릿 -> 새 위치)
     */
    private void copyBlocks(Location source1, Location source2, Location destination) {
        World sourceWorld = source1.getWorld();
        World destWorld = destination.getWorld();

        int minX = Math.min(source1.getBlockX(), source2.getBlockX());
        int minY = Math.min(source1.getBlockY(), source2.getBlockY());
        int minZ = Math.min(source1.getBlockZ(), source2.getBlockZ());

        int maxX = Math.max(source1.getBlockX(), source2.getBlockX());
        int maxY = Math.max(source1.getBlockY(), source2.getBlockY());
        int maxZ = Math.max(source1.getBlockZ(), source2.getBlockZ());

        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    Block sourceBlock = sourceWorld.getBlockAt(x, y, z);
                    int offsetX = x - minX;
                    int offsetY = y - minY;
                    int offsetZ = z - minZ;

                    Block destBlock = destWorld.getBlockAt(
                        destination.getBlockX() + offsetX,
                        destination.getBlockY() + offsetY,
                        destination.getBlockZ() + offsetZ
                    );

                    destBlock.setType(sourceBlock.getType());
                    destBlock.setBlockData(sourceBlock.getBlockData());
                }
            }
        }
    }

    /**
     * 사용 가능한 체스판 할당
     */
    public Optional<Arena> allocateArena() {
        return arenas.stream()
            .filter(arena -> !arena.isInUse())
            .findFirst()
            .map(arena -> {
                arena.setInUse(true);
                return arena;
            });
    }

    /**
     * 체스판 반환
     */
    public void releaseArena(Arena arena) {
        if (arena != null) {
            arena.setInUse(false);
        }
    }

    /**
     * 사용 가능한 아레나 개수
     */
    public int getAvailableArenaCount() {
        return (int) arenas.stream().filter(arena -> !arena.isInUse()).count();
    }

    /**
     * 전체 아레나 개수
     */
    public int getTotalArenaCount() {
        return arenas.size();
    }
}
