package es.whiteblack;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public final class WhiteBlack extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private enum State { IDLE, RUNNING }
    private State state = State.IDLE;
    private final Map<UUID,Integer> round = new HashMap<>();
    private final Map<UUID,Integer> points = new HashMap<>();
    private final Set<String> pumpkins = new HashSet<>();
    private final Random random = new Random();
    private File dataFile; private YamlConfiguration data;
    private Location lobby, spawn, pos1, pos2; private World gameWorld;
    private int roundSeconds=180, pumpkinCount=50, rewardPoints=10;
    private double rewardMoney=100.0; private BukkitTask ticker; private int remaining;
    private Economy economy; private NamespacedKey pumpkinKey;

    @Override public void onEnable() {
        saveDefaultConfig(); dataFile=new File(getDataFolder(),"players.yml"); data=YamlConfiguration.loadConfiguration(dataFile);
        loadSettings(); pumpkinKey=new NamespacedKey(this,"wb_pumpkin");
        for(String id:data.getConfigurationSection("points") == null ? Collections.<String>emptySet() : data.getConfigurationSection("points").getKeys(false))
            try { points.put(UUID.fromString(id),data.getInt("points."+id)); } catch(IllegalArgumentException ignored) {}
        Bukkit.getPluginManager().registerEvents(this,this); Objects.requireNonNull(getCommand("wb")).setExecutor(this); getCommand("wb").setTabCompleter(this);
        setupEconomy(); getLogger().info("WhiteBlack habilitado. Configura /wb setworld y las ubicaciones.");
    }
    @Override public void onDisable(){ stopGame(false); saveData(); }
    private void setupEconomy(){ if(Bukkit.getPluginManager().getPlugin("Vault")==null)return; RegisteredServiceProvider<Economy> rsp=Bukkit.getServicesManager().getRegistration(Economy.class); if(rsp!=null)economy=rsp.getProvider(); }
    private void loadSettings(){ roundSeconds=getConfig().getInt("round-seconds",180); pumpkinCount=getConfig().getInt("pumpkins",50); rewardPoints=getConfig().getInt("reward-points",10); rewardMoney=getConfig().getDouble("reward-money",100); lobby=readLoc("lobby"); spawn=readLoc("spawn"); pos1=readLoc("pos1"); pos2=readLoc("pos2"); String w=getConfig().getString("world"); gameWorld=w==null?null:Bukkit.getWorld(w); }
    private Location readLoc(String key){ if(!getConfig().contains(key+".world"))return null; World w=Bukkit.getWorld(getConfig().getString(key+".world")); if(w==null)return null; return new Location(w,getConfig().getDouble(key+".x"),getConfig().getDouble(key+".y"),getConfig().getDouble(key+".z"), (float)getConfig().getDouble(key+".yaw"), (float)getConfig().getDouble(key+".pitch")); }
    private void writeLoc(String key,Location l){ getConfig().set(key+".world",l.getWorld().getName()); getConfig().set(key+".x",l.getX());getConfig().set(key+".y",l.getY());getConfig().set(key+".z",l.getZ());getConfig().set(key+".yaw",l.getYaw());getConfig().set(key+".pitch",l.getPitch());saveConfig(); }
    private void msg(CommandSender s,String m){s.sendMessage(ChatColor.GOLD+"[WhiteBlack] "+ChatColor.WHITE+m);}
    private boolean admin(CommandSender s){if(s.hasPermission("whiteblack.admin"))return true;msg(s,"No tienes permiso.");return false;}
    @Override public boolean onCommand(CommandSender s,Command c,String label,String[] a){
        if(a.length==0||a[0].equalsIgnoreCase("help")){ msg(s,"/wb setlobby | setspawn | setworld <mundo> | pos1 | pos2 | start | stop | next");msg(s,"/wb points <jugador> | addpoints <jugador> <cantidad> | removepoints <jugador> <cantidad> | top");msg(s,"/wb setround <min> | setpumpkins <cantidad> | setreward <puntos> <dinero> | reload");return true; }
        String sub=a[0].toLowerCase(Locale.ROOT); if(sub.equals("points")||sub.equals("top")){if(sub.equals("top")){points.entrySet().stream().sorted((x,y)->Integer.compare(y.getValue(),x.getValue())).limit(10).forEach(e->{OfflinePlayer p=Bukkit.getOfflinePlayer(e.getKey());msg(s,(p.getName()==null?e.getKey().toString():p.getName())+": "+e.getValue()+" puntos");});return true;} if(a.length<2){msg(s,"Uso: /wb points <jugador>");return true;} OfflinePlayer p=Bukkit.getOfflinePlayer(a[1]);msg(s,a[1]+" tiene "+points.getOrDefault(p.getUniqueId(),0)+" puntos.");return true;}
        if(!admin(s))return true;
        switch(sub){
            case "setlobby": case "setspawn": case "pos1": case "pos2": {if(!(s instanceof Player p)){msg(s,"Solo un jugador puede ejecutar esto.");break;} Location l=p.getLocation(); if(sub.equals("setlobby")){lobby=l;writeLoc("lobby",l);}else if(sub.equals("setspawn")){spawn=l;writeLoc("spawn",l);}else if(sub.equals("pos1")){pos1=l;writeLoc("pos1",l);}else{pos2=l;writeLoc("pos2",l);}msg(s,"Ubicación guardada: "+sub);break;}
            case "setworld": {if(a.length<2){msg(s,"Uso: /wb setworld <mundo>");break;} World w=Bukkit.getWorld(a[1]);if(w==null){msg(s,"Mundo no cargado. Créalo/cárgalo con Multiverse-Core primero.");break;}gameWorld=w;getConfig().set("world",w.getName());saveConfig();msg(s,"Mundo de juego: "+w.getName());break;}
            case "start": if(state==State.RUNNING){msg(s,"Ya hay una partida activa.");break;} if(gameWorld==null||spawn==null||pos1==null||pos2==null||pos1.getWorld()!=gameWorld||pos2.getWorld()!=gameWorld){msg(s,"Configura mundo, spawn y esquinas del mismo mundo.");break;}startGame();break;
            case "stop": if(state==State.IDLE)msg(s,"No hay partida activa.");else stopGame(true);break;
            case "next": if(state!=State.RUNNING)msg(s,"No hay ronda activa.");else finishRound();break;
            case "addpoints": case "removepoints": {if(a.length<3){msg(s,"Uso: /wb "+sub+" <jugador> <cantidad>");break;} OfflinePlayer p=Bukkit.getOfflinePlayer(a[1]);int n;try{n=Integer.parseInt(a[2]);}catch(NumberFormatException ex){msg(s,"Cantidad no válida.");break;}if(n<0){msg(s,"La cantidad no puede ser negativa.");break;}int old=points.getOrDefault(p.getUniqueId(),0);points.put(p.getUniqueId(),Math.max(0,old+(sub.equals("addpoints")?n:-n)));saveData();msg(s,"Puntos actualizados: "+points.get(p.getUniqueId()));break;}
            case "setround": case "setpumpkins": {if(a.length<2){msg(s,"Falta la cantidad.");break;}try{int n=Integer.parseInt(a[1]);if(n<1||n>60){msg(s,"La duración debe estar entre 1 y 60 minutos.");break;}if(sub.equals("setround")){roundSeconds=n*60;getConfig().set("round-seconds",roundSeconds);}else{pumpkinCount=Math.min(n,500);getConfig().set("pumpkins",pumpkinCount);}saveConfig();msg(s,"Configuración actualizada.");}catch(NumberFormatException ex){msg(s,"Número no válido.");}break;}
            case "setreward": {if(a.length<3){msg(s,"Uso: /wb setreward <puntos> <dinero>");break;}try{int p=Integer.parseInt(a[1]);double m=Double.parseDouble(a[2]);if(p<0||m<0||!Double.isFinite(m)){msg(s,"Recompensas no válidas.");break;}rewardPoints=p;rewardMoney=m;getConfig().set("reward-points",p);getConfig().set("reward-money",m);saveConfig();msg(s,"Recompensa actualizada.");}catch(NumberFormatException ex){msg(s,"Valores no válidos.");}break;}
            case "reload": reloadConfig();loadSettings();setupEconomy();msg(s,"Configuración recargada.");break;
            default: msg(s,"Subcomando desconocido. Usa /wb help.");
        }return true;
    }
    private void startGame(){state=State.RUNNING;Bukkit.broadcastMessage(ChatColor.GOLD+"[WhiteBlack] ¡Comienza la búsqueda de calabazas!");for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("whiteblack.play")){round.put(p.getUniqueId(),0);if(lobby!=null)p.teleport(lobby);}startRound();}
    private void startRound(){pumpkins.clear();remaining=roundSeconds;round.clear();for(Player p:Bukkit.getOnlinePlayers())if(p.hasPermission("whiteblack.play")){round.put(p.getUniqueId(),0);if(spawn!=null)p.teleport(spawn);}spawnPumpkins();Bukkit.broadcastMessage(ChatColor.GREEN+"Ronda iniciada: encuentra "+pumpkins.size()+" calabazas.");if(ticker!=null)ticker.cancel();ticker=Bukkit.getScheduler().runTaskTimer(this,()->{if(--remaining<=0)finishRound();else if(remaining==60||remaining==30||remaining<=10)Bukkit.broadcastMessage(ChatColor.YELLOW+"[WhiteBlack] Quedan "+remaining+" segundos.");},20L,20L);}
    private void spawnPumpkins(){int minX=Math.min(pos1.getBlockX(),pos2.getBlockX()),maxX=Math.max(pos1.getBlockX(),pos2.getBlockX()),minZ=Math.min(pos1.getBlockZ(),pos2.getBlockZ()),maxZ=Math.max(pos1.getBlockZ(),pos2.getBlockZ());int minY=Math.min(pos1.getBlockY(),pos2.getBlockY()),maxY=Math.max(pos1.getBlockY(),pos2.getBlockY());int tries=0;while(pumpkins.size()<pumpkinCount&&tries++<pumpkinCount*80){int x=minX+random.nextInt(maxX-minX+1),z=minZ+random.nextInt(maxZ-minZ+1),y=minY+random.nextInt(maxY-minY+1);Block b=gameWorld.getBlockAt(x,y,z);if(!b.getType().isAir()||!b.getRelative(0,-1,0).getType().isSolid())continue;b.setType(Material.CARVED_PUMPKIN);b.getChunk().load();org.bukkit.block.BlockState st=b.getState();st.getPersistentDataContainer().set(pumpkinKey,PersistentDataType.BYTE,(byte)1);st.update(true,false);pumpkins.add(key(b.getLocation()));}if(pumpkins.isEmpty())Bukkit.broadcastMessage(ChatColor.RED+"No se pudieron colocar calabazas; revisa la zona seleccionada.");}
    private String key(Location l){return l.getWorld().getName()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();}
    @EventHandler public void onBreak(BlockBreakEvent e){if(state!=State.RUNNING)return;Block b=e.getBlock();if(!pumpkins.contains(key(b.getLocation())))return;e.setDropItems(false);e.setExpToDrop(0);pumpkins.remove(key(b.getLocation()));round.merge(e.getPlayer().getUniqueId(),1,Integer::sum);e.getPlayer().sendMessage(ChatColor.GREEN+"¡Calabaza encontrada! Total: "+round.get(e.getPlayer().getUniqueId()));}
    @EventHandler public void onJoin(PlayerJoinEvent e){if(state==State.RUNNING&&e.getPlayer().hasPermission("whiteblack.play")){round.put(e.getPlayer().getUniqueId(),0);if(spawn!=null)e.getPlayer().teleport(spawn);}}
    private void finishRound(){if(state!=State.RUNNING)return;if(ticker!=null){ticker.cancel();ticker=null;}int max=round.values().stream().mapToInt(Integer::intValue).max().orElse(0);List<UUID>winners=round.entrySet().stream().filter(e->e.getValue()==max&&max>0).map(Map.Entry::getKey).toList();if(winners.isEmpty())Bukkit.broadcastMessage(ChatColor.GOLD+"[WhiteBlack] Ronda finalizada sin calabazas recogidas.");else{for(UUID id:winners){points.merge(id,rewardPoints,Integer::sum);Player p=Bukkit.getPlayer(id);if(economy!=null&&rewardMoney>0)economy.depositPlayer(Bukkit.getOfflinePlayer(id),rewardMoney);if(p!=null)p.sendMessage(ChatColor.GOLD+"¡Has ganado! +"+rewardPoints+" puntos"+(economy==null?" (Vault no disponible)":" y +"+rewardMoney+" dinero"));Bukkit.broadcastMessage(ChatColor.YELLOW+Bukkit.getOfflinePlayer(id).getName()+" gana la ronda con "+max+" calabazas.");}saveData();}for(String k:new HashSet<>(pumpkins)){String[]v=k.split(":");World w=Bukkit.getWorld(v[0]);if(w!=null){Block b=w.getBlockAt(Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3]));if(b.getType()==Material.CARVED_PUMPKIN)b.setType(Material.AIR);}}pumpkins.clear();Bukkit.getScheduler().runTaskLater(this,()->{if(state==State.RUNNING)startRound();},100L);}
    private void stopGame(boolean announce){if(ticker!=null){ticker.cancel();ticker=null;}for(String k:new HashSet<>(pumpkins)){String[]v=k.split(":");World w=Bukkit.getWorld(v[0]);if(w!=null){Block b=w.getBlockAt(Integer.parseInt(v[1]),Integer.parseInt(v[2]),Integer.parseInt(v[3]));if(b.getType()==Material.CARVED_PUMPKIN)b.setType(Material.AIR);}}pumpkins.clear();state=State.IDLE;round.clear();if(announce)Bukkit.broadcastMessage(ChatColor.RED+"[WhiteBlack] Partida detenida.");}
    private void saveData(){if(data==null)return;data.set("points",null);for(var e:points.entrySet())data.set("points."+e.getKey(),e.getValue());try{data.save(dataFile);}catch(IOException e){getLogger().severe("No se pudieron guardar los puntos: "+e.getMessage());}}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] args){if(args.length==1)return Arrays.asList("help","setlobby","setspawn","setworld","pos1","pos2","start","stop","next","points","addpoints","removepoints","top","setround","setpumpkins","setreward","reload").stream().filter(x->x.startsWith(args[0].toLowerCase())).toList();return Collections.emptyList();}
}
