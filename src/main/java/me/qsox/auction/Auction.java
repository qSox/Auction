package me.qsox.auction;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;

public class Auction extends JavaPlugin implements CommandExecutor, Listener {

    private AuctionItem currentAuction;

    @Override
    public void onEnable() {
        getCommand("auc").setExecutor(this);
        getCommand("bid").setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        if (cmd.getName().equalsIgnoreCase("auc")) {
            if (args.length != 1) {
                player.sendMessage("Usage: /auc <money amount>");
                return true;
            }

            double startingPrice;
            try {
                startingPrice = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid money amount.");
                return true;
            }

            if (currentAuction != null) {
                player.sendMessage("An auction is already running.");
                return true;
            }

            ItemStack itemInHand = player.getInventory().getItemInMainHand();
            if (itemInHand == null || itemInHand.getAmount() == 0) {
                player.sendMessage("You must hold an item to auction.");
                return true;
            }

            currentAuction = new AuctionItem(itemInHand, startingPrice, player);

            player.getInventory().removeItem(itemInHand);

            new BukkitRunnable() {
                int countdown = 60;

                @Override
                public void run() {
                    if (countdown == 30 || countdown == 3 || countdown == 2 || countdown == 1) {
                        getServer().broadcastMessage("Auction ending in " + countdown + " seconds.");
                    }

                    if (countdown == 0) {
                        endAuction();
                        cancel(); // Auction ended, cancel the runnable
                    }

                    countdown--;
                }
            }.runTaskTimer(this, 0, 20); // Run every second

            getServer().broadcastMessage(player.getName() + "An auction has started for " + startingPrice + " coins. Use /bid to participate. Bidding ends in 1 minute.");

        } else if (cmd.getName().equalsIgnoreCase("bid")) {
            if (currentAuction == null) {
                player.sendMessage("There is no active auction.");
                return true;
            }

            if (currentAuction.getOwner().equals(player)) {
                player.sendMessage("You cannot bid on your own auction.");
                return true;
            }

            if (args.length != 1) {
                player.sendMessage("Usage: /bid <money amount>");
                return true;
            }

            double bidAmount;
            try {
                bidAmount = Double.parseDouble(args[0]);
            } catch (NumberFormatException e) {
                player.sendMessage("Invalid money amount.");
                return true;
            }

            if (bidAmount <= currentAuction.getHighestBid()) {
                player.sendMessage("Your bid must be higher than the current highest bid.");
                return true;
            }

            currentAuction.setHighestBid(bidAmount);
            currentAuction.setHighestBidder(player);
            player.sendMessage("You have placed a bid of " + bidAmount + " coins.");
            getServer().broadcastMessage(player.getName() + " has placed a bid of " + bidAmount + " coins.");

        } else if (cmd.getName().equalsIgnoreCase("auctioninfo")) {
            openAuctionInfoGUI(player);
        }

        return true;
    }

    private void endAuction() {
        if (currentAuction == null) return;

        Player winner = currentAuction.getHighestBidder();
        if (winner != null) {
            winner.getInventory().addItem(currentAuction.getItem());
            winner.sendMessage("Congratulations! You won the auction for " + currentAuction.getHighestBid() + " coins.");
            getServer().broadcastMessage("Auction ended. " + winner.getName() + " won the auction for " + currentAuction.getHighestBid() + " coins.");
        } else {
            currentAuction.getOwner().getInventory().addItem(currentAuction.getItem());
            currentAuction.getOwner().sendMessage("Auction ended with no bids. Item returned to your inventory.");
            getServer().broadcastMessage("Auction ended with no bids.");
        }
        currentAuction = null;
    }

    private void openAuctionInfoGUI(Player player) {
        if (currentAuction == null) {
            player.sendMessage(ChatColor.RED + "No auction information available.");
            return;
        }

        Inventory gui = Bukkit.createInventory(player, 9, ChatColor.AQUA + "Auction Info");

        ItemStack itemStack = currentAuction.getItem().clone(); // Create a copy of the original item
        ItemMeta meta = itemStack.getItemMeta().clone(); // Create a copy of the original item's meta
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.YELLOW + "Starting Price: " + currentAuction.getStartingPrice());
        lore.add(ChatColor.YELLOW + "Current Highest Bid: " + currentAuction.getHighestBid());
        lore.add(ChatColor.YELLOW + "Owner: " + currentAuction.getOwner().getName());
        meta.setLore(lore);
        itemStack.setItemMeta(meta);

        gui.setItem(4, itemStack);

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof Player) {
            Player player = (Player) event.getInventory().getHolder();
            if (currentAuction != null && event.getView().getTitle().equalsIgnoreCase(ChatColor.AQUA + "Auction Info")) {
                event.setCancelled(true); // Cancelling inventory clicks to prevent picking up items

                // Remove lore from the original item after the GUI is closed or the item is clicked
                ItemStack itemStack = currentAuction.getItem();
                ItemMeta meta = itemStack.getItemMeta();
                meta.setLore(new ArrayList<>());
                itemStack.setItemMeta(meta);
            }
        }
    }


    private static class AuctionItem {
        private ItemStack item;
        private double startingPrice;
        private double highestBid;
        private Player highestBidder;
        private Player owner;

        public AuctionItem(ItemStack item, double startingPrice, Player owner) {
            this.item = item;
            this.startingPrice = startingPrice;
            this.highestBid = startingPrice;
            this.owner = owner;
        }

        public ItemStack getItem() {
            return item;
        }

        public double getStartingPrice() {
            return startingPrice;
        }

        public double getHighestBid() {
            return highestBid;
        }

        public void setHighestBid(double highestBid) {
            this.highestBid = highestBid;
        }

        public Player getHighestBidder() {
            return highestBidder;
        }

        public void setHighestBidder(Player highestBidder) {
            this.highestBidder = highestBidder;
        }

        public Player getOwner() {
            return owner;
        }
    }
}
