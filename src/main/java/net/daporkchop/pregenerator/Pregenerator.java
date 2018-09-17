package net.daporkchop.pregenerator;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.ArrayBlockingQueue;

public final class Pregenerator extends JavaPlugin {
    private static final ArrayBlockingQueue<ChunkPos> positions = new ArrayBlockingQueue<ChunkPos>(2);

    @Override
    public void onEnable() {
        this.getCommand("pregen").setExecutor(
                new CommandExecutor() {
                    @Override
                    public boolean onCommand(final CommandSender sender, Command command, String label, String[] args) {
                        if (!sender.hasPermission("pregen.pregen")) {
                            return true;
                        }
                        try {
                            Server server = Pregenerator.this.getServer();
                            if (!(args.length == 3 || args.length == 4)) {
                                throw new IndexOutOfBoundsException();
                            }
                            final int x = Integer.parseInt(args[0]);
                            final int z = Integer.parseInt(args[1]);
                            int rA = Integer.parseInt(args[2]);

                            if (rA <= 0) {
                                sender.sendMessage("Radius must be at least 1!");
                                return true;
                            }
                            final int r = rA >> 1;

                            final World world;

                            {
                                String worldName = "world";
                                if (sender instanceof Player && args.length != 4) {
                                    worldName = ((Player) sender).getWorld().getName();
                                }
                                if (args.length == 4) {
                                    worldName = args[3];
                                }
                                world = server.getWorld(worldName);
                                if (world == null) {
                                    sender.sendMessage(String.format("Unknown world: %s", worldName));
                                    return true;
                                }
                            }

                            new Thread() {
                                @Override
                                public void run() {
                                    try {
                                        int m = 0;
                                        int total = (r << 1) * (r << 1);
                                        long start = System.currentTimeMillis();
                                        for (int xx = x - r; xx < x + r; xx++) {
                                            for (int zz = z - r; zz < z + r; zz++) {
                                                positions.put(new ChunkPos(xx, zz, world));
                                                if ((m++) % 100 == 0) {
                                                    System.out.println(String.format("Generated %d/%d chunks", m, total));
                                                }
                                            }
                                        }
                                        sender.sendMessage(String.format("Done! Processed %d chunks in %dms!", m, (int) (System.currentTimeMillis() - start)));
                                    } catch (InterruptedException e) {
                                        e.printStackTrace();
                                    }
                                }
                            }.start();
                        } catch (Exception e) {
                            sender.sendMessage("Usage: /pregen <x> <z> <radius> [worldName]");
                        }
                        return true;
                    }
                });
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                ChunkPos pos = positions.poll();
                if (pos != null) {
                    for (int x = -1; x <= 1; x++) {
                        for (int z = -1; z <= 1; z++) {
                            Chunk chunk = pos.world.getChunkAt(pos.x + x, pos.z + z);
                            if (!chunk.isLoaded()) {
                                chunk.load(true);
                            }
                        }
                    }
                }
            }
        }, 20, 1);
        this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
                if (!positions.isEmpty()) {
                    for (World world : Pregenerator.this.getServer().getWorlds()) {
                        int loaded = world.getLoadedChunks().length;
                        if (loaded == 0) {
                            continue;
                        }
                        System.out.println(String.format("World \"%s\" has %d chunks loaded", world.getName(), loaded));
                    }
                }
            }
        }, 20, 40);
        this.getCommand("setworldspawn").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (!sender.hasPermission("pregen.pregen")) {
                    return true;
                }
                int x, y, z;
                String worldName;
                switch (args.length) {
                    case 0: {
                        if (sender instanceof Player) {
                            Player player = (Player) sender;
                            Location loc = player.getLocation();
                            x = loc.getBlockX();
                            y = loc.getBlockY();
                            z = loc.getBlockZ();
                            worldName = player.getWorld().getName();
                        } else {
                            sender.sendMessage(command.getUsage());
                            return true;
                        }
                    }
                    break;
                    case 3: {
                        try {
                            if (sender instanceof Player) {
                                Player player = (Player) sender;
                                x = Integer.parseInt(args[0]);
                                y = Integer.parseInt(args[1]);
                                z = Integer.parseInt(args[2]);
                                worldName = player.getWorld().getName();
                            } else {
                                sender.sendMessage("Must provide world!");
                                return true;
                            }
                        } catch (NumberFormatException e) {
                            sender.sendMessage(command.getUsage());
                            return true;
                        }
                    }
                    break;
                    case 4: {
                        try {
                            x = Integer.parseInt(args[0]);
                            y = Integer.parseInt(args[1]);
                            z = Integer.parseInt(args[2]);
                            worldName = args[3];
                        } catch (NumberFormatException e) {
                            sender.sendMessage(command.getUsage());
                            return true;
                        }
                    }
                    break;
                    default: {
                        sender.sendMessage(command.getUsage());
                        return true;
                    }
                }
                World world = Pregenerator.this.getServer().getWorld(worldName);
                if (world == null)  {
                    sender.sendMessage(String.format("Invalid world: %s", worldName));
                    return true;
                }

                world.setSpawnLocation(x, y, z);
                sender.sendMessage(String.format("Set spawn point in world %s to (%d,%d,%d)", worldName, x, y, z));
                return true;
            }
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    private static final class ChunkPos {
        public final int x;
        public final int z;
        public final World world;

        public ChunkPos(int x, int z, World world) {
            this.x = x;
            this.z = z;
            this.world = world;
        }
    }
}
