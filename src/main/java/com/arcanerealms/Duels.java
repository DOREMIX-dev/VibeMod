package com.arcanerealms;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.resources.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.*;
import net.minecraftforge.event.level.*;
public final class Duels {
    public static final ResourceKey<Level> ARENA=ResourceKey.create(Registries.DIMENSION,Identifier.fromNamespaceAndPath(ArcaneRealms.MOD_ID,"arena"));
    record Invite(UUID from,String mode,long expires){}
    static final Map<UUID,Invite> INVITES=new HashMap<>();
    static final Map<UUID,Fight> FIGHTS=new HashMap<>();
    static final class Fight {
        final UUID a,b;final ServerLevel level;final Vec3 center;final long start;final boolean ranked;final int slot;
        final Set<UUID> draw=new HashSet<>();
        Fight(ServerPlayer a,ServerPlayer b,Vec3 center,boolean ranked,int slot){this.a=a.getUUID();this.b=b.getUUID();level=a.level();this.center=center;start=PlayerMagic.time(a)+100;this.ranked=ranked;this.slot=slot;}
        UUID other(UUID id){return id.equals(a)?b:a;}
    }
    private static void msg(ServerPlayer p,String text){PlayerMagic.message(p,text);}
    public static boolean canCast(ServerPlayer p){Fight f=FIGHTS.get(p.getUUID());return f==null||PlayerMagic.time(p)>=f.start;}
    public static boolean allowed(ServerPlayer p,LivingEntity target){
        Fight a=FIGHTS.get(p.getUUID()),b=FIGHTS.get(target.getUUID());
        if(a!=null)return b==a&&a.other(p.getUUID()).equals(target.getUUID())&&canCast(p);
        return b==null&&!(target instanceof ServerPlayer);
    }
    public static String invitation(ServerPlayer p){
        Invite i=INVITES.get(p.getUUID());if(i==null)return "";
        ServerPlayer from=p.level().getServer().getPlayerList().getPlayer(i.from());
        return from==null?"":from.getName().getString()+" — "+i.mode();
    }
    public static int challenge(ServerPlayer a,ServerPlayer b,String mode){
        if(mode.equals("overworld"))mode="world";
        if(!Set.of("world","arena","ranked").contains(mode)||a==b||!eligible(a)||!eligible(b)||a.level()!=b.level()||a.distanceToSqr(b)>1024)return 0;
        if(INVITES.containsKey(a.getUUID())||INVITES.containsKey(b.getUUID())||INVITES.values().stream().anyMatch(i->i.from().equals(a.getUUID())||i.from().equals(b.getUUID())))return 0;
        INVITES.put(b.getUUID(),new Invite(a.getUUID(),mode,PlayerMagic.time(a)+600));msg(a,"Вызов отправлен.");msg(b,"Вызов от "+a.getName().getString()+". Откройте K для ответа.");return 1;
    }
    private static boolean eligible(ServerPlayer p){return p.isAlive()&&!p.isSpectator()&&!p.isCreative()&&!FIGHTS.containsKey(p.getUUID())&&!PlayerMagic.data(p).contains("duel_return");}
    static void snapshot(ServerPlayer p){
        CompoundTag r=new CompoundTag();
        r.putString("dimension",p.level().dimension().identifier().toString());r.putDouble("x",p.getX());r.putDouble("y",p.getY());r.putDouble("z",p.getZ());r.putFloat("yaw",p.getYRot());r.putFloat("pitch",p.getXRot());
        r.putFloat("health",p.getHealth());r.putInt("mana",PlayerMagic.value(p,"mana",100));r.putInt("food",p.getFoodData().getFoodLevel());r.putFloat("saturation",p.getFoodData().getSaturationLevel());
        r.store("effects",MobEffectInstance.CODEC.listOf(),p.registryAccess().createSerializationContext(NbtOps.INSTANCE),new ArrayList<>(p.getActiveEffects()));
        PlayerMagic.data(p).put("duel_return",r);
    }
    public static void recover(ServerPlayer p){
        CompoundTag d=PlayerMagic.data(p);if(!d.contains("duel_return"))return;CompoundTag r=d.getCompoundOrEmpty("duel_return");
        ServerLevel level=p.level().getServer().getLevel(ResourceKey.create(Registries.DIMENSION,Identifier.parse(r.getStringOr("dimension","minecraft:overworld"))));
        if(level==null){msg(p,"Исходное измерение недоступно. Точка возврата сохранена.");return;}
        boolean moved=p.teleportTo(level,r.getDoubleOr("x",0),r.getDoubleOr("y",80),r.getDoubleOr("z",0),Set.of(),r.getFloatOr("yaw",0),r.getFloatOr("pitch",0),true);
        if(!moved){msg(p,"Возврат не выполнен. Точка сохранена.");return;}
        p.removeAllEffects();r.read("effects",MobEffectInstance.CODEC.listOf(),p.registryAccess().createSerializationContext(NbtOps.INSTANCE)).ifPresent(effects->effects.forEach(p::addEffect));
        p.getFoodData().setFoodLevel(r.getIntOr("food",20));p.getFoodData().setSaturation(r.getFloatOr("saturation",5));p.setHealth(Math.max(1,Math.min(p.getMaxHealth(),r.getFloatOr("health",20))));p.clearFire();p.fallDistance=0;
        d.putInt("mana",r.getIntOr("mana",100));d.remove("duel_return");
    }
    public static int accept(ServerPlayer b){
        Invite i=INVITES.remove(b.getUUID());if(i==null||i.expires()<PlayerMagic.time(b))return 0;
        MinecraftServer server=b.level().getServer();ServerPlayer a=server.getPlayerList().getPlayer(i.from());
        if(a==null||!eligible(a)||!eligible(b)||a.level()!=b.level()||a.distanceToSqr(b)>1024)return 0;
        Vec3 center=a.position().add(b.position()).scale(.5);int slot=-1;ServerLevel destination=a.level();
        if(!i.mode().equals("world")){
            destination=server.getLevel(ARENA);if(destination==null){msg(b,"Измерение арены не загружено.");return 0;}
            Set<Integer> used=new HashSet<>();for(Fight f:FIGHTS.values())used.add(f.slot);
            slot=0;while(used.contains(slot))slot++;if(slot>=32){msg(b,"Все арены заняты.");return 0;}
            center=new Vec3(slot*128+.5,81,.5);
            for(int x=-25;x<=25;x++)for(int z=-25;z<=25;z++){
                destination.setBlock(new BlockPos(slot*128+x,80,z),Blocks.SMOOTH_STONE.defaultBlockState(),3);
                if(Math.abs(x)==25||Math.abs(z)==25)for(int y=81;y<=87;y++)destination.setBlock(new BlockPos(slot*128+x,y,z),Blocks.BARRIER.defaultBlockState(),3);
            }
        }else for(Fight f:new HashSet<>(FIGHTS.values()))if(f.level==destination&&f.center.distanceToSqr(center)<10404){msg(b,"Слишком близко к другой дуэли.");return 0;}
        snapshot(a);snapshot(b);server.getPlayerList().saveAll();
        if(slot>=0){
            boolean first=a.teleportTo(destination,center.x-8,81,center.z,Set.of(),-90,0,true);
            boolean second=first&&b.teleportTo(destination,center.x+8,81,center.z,Set.of(),90,0,true);
            if(!second){recover(a);recover(b);server.getPlayerList().saveAll();return 0;}
        }
        Fight f=new Fight(a,b,center,i.mode().equals("ranked"),slot);FIGHTS.put(f.a,f);FIGHTS.put(f.b,f);
        for(ServerPlayer p:List.of(a,b)){p.setHealth(p.getMaxHealth());PlayerMagic.data(p).putInt("mana",100);msg(p,"Бой через 5 секунд. Радиус 24 блока, выход — поражение.");}return 1;
    }
    public static int decline(ServerPlayer p){INVITES.remove(p.getUUID());return 1;}
    public static int surrender(ServerPlayer p){Fight f=FIGHTS.get(p.getUUID());if(f==null)return 0;finish(p.level().getServer(),f,f.other(p.getUUID()),"Сдача");return 1;}
    public static int draw(ServerPlayer p){Fight f=FIGHTS.get(p.getUUID());if(f==null)return 0;f.draw.add(p.getUUID());if(f.draw.size()==2)finish(p.level().getServer(),f,null,"Ничья");else{ServerPlayer other=p.level().getServer().getPlayerList().getPlayer(f.other(p.getUUID()));if(other!=null)msg(other,"Предложена ничья: /arcane duel draw");}return 1;}
    static void finish(MinecraftServer server,Fight f,UUID winner,String reason){
        if(FIGHTS.remove(f.a)==null)return;FIGHTS.remove(f.b);Magic.remove(f.a);Magic.remove(f.b);
        ServerPlayer a=server.getPlayerList().getPlayer(f.a),b=server.getPlayerList().getPlayer(f.b);
        int delta=a!=null&&b!=null&&winner!=null?Spell.ratingDelta(PlayerMagic.value(winner.equals(f.a)?a:b,"rating",1000),PlayerMagic.value(winner.equals(f.a)?b:a,"rating",1000)):12;
        for(ServerPlayer p:Arrays.asList(a,b))if(p!=null){
            if(winner!=null){boolean win=winner.equals(p.getUUID());String key=win?"wins":"losses";PlayerMagic.data(p).putInt(key,PlayerMagic.value(p,key,0)+1);if(f.ranked)PlayerMagic.data(p).putInt("rating",Math.max(0,PlayerMagic.value(p,"rating",1000)+(win?delta:-delta)));}
            else PlayerMagic.data(p).putInt("draws",PlayerMagic.value(p,"draws",0)+1);
            recover(p);msg(p,reason+(winner==null?"":" — "+(winner.equals(p.getUUID())?"победа":"поражение")));
        }
        server.getPlayerList().saveAll();
    }
    public static void logout(ServerPlayer p){Fight f=FIGHTS.get(p.getUUID());if(f!=null)finish(p.level().getServer(),f,f.other(p.getUUID()),"Отключение");INVITES.entrySet().removeIf(e->e.getKey().equals(p.getUUID())||e.getValue().from().equals(p.getUUID()));}
    public static void stop(MinecraftServer server){for(Fight f:new HashSet<>(FIGHTS.values()))finish(server,f,null,"Остановка сервера");INVITES.clear();FIGHTS.clear();}
    public static void tick(MinecraftServer server){
        long now=server.overworld().getGameTime();INVITES.entrySet().removeIf(e->e.getValue().expires()<now);
        for(Fight f:new HashSet<>(FIGHTS.values())){
            ServerPlayer a=server.getPlayerList().getPlayer(f.a),b=server.getPlayerList().getPlayer(f.b);
            if(a==null||b==null){finish(server,f,a==null?f.b:f.a,"Отключение");continue;}
            boolean invalidA=a.level()!=f.level||a.position().distanceToSqr(f.center)>576||a.isSpectator()||a.isCreative(),invalidB=b.level()!=f.level||b.position().distanceToSqr(f.center)>576||b.isSpectator()||b.isCreative();
            if(invalidA||invalidB){finish(server,f,invalidA&&invalidB?null:invalidA?f.b:f.a,"Выход из зоны");continue;}
            if(now>=f.start+6000){finish(server,f,null,"Лимит времени");continue;}
            if(now<f.start&&now%20==0){msg(a,"До боя: "+((f.start-now)/20));msg(b,"До боя: "+((f.start-now)/20));}
        }
    }
    public static void register(){
        ExplosionEvent.Detonate.BUS.addListener(e->e.getAffectedBlocks().removeIf(pos->protectedAt(e.getLevel(),pos)));
        LivingAttackEvent.BUS.addListener(e->{
            Fight target=FIGHTS.get(e.getEntity().getUUID());Entity attacker=e.getSource().getEntity();Fight source=attacker==null?null:FIGHTS.get(attacker.getUUID());
            if(target!=null||source!=null)return target==null||target!=source||!Magic.authorizedDamage(attacker==null?null:attacker.getUUID(),e.getEntity().getUUID())||PlayerMagic.time((ServerPlayer)e.getEntity())<target.start;
            return false;
        });
        LivingDamageEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p){Fight f=FIGHTS.get(p.getUUID());if(f!=null&&e.getAmount()>=p.getHealth()){finish(p.level().getServer(),f,f.other(p.getUUID()),"Бой завершён без смерти");return true;}}return false;});
        LivingDeathEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p){Fight f=FIGHTS.get(p.getUUID());if(f!=null){finish(p.level().getServer(),f,f.other(p.getUUID()),"Бой завершён");return true;}}return false;});
        BlockEvent.BreakEvent.BUS.addListener(e->{return protectedAt(e.getLevel(),e.getPos())||FIGHTS.containsKey(e.getPlayer().getUUID());});
        BlockEvent.EntityPlaceEvent.BUS.addListener(e->{return protectedAt(e.getLevel(),e.getPos())||e.getEntity()!=null&&FIGHTS.containsKey(e.getEntity().getUUID());});
    }
    private static boolean protectedAt(Object level,BlockPos pos){if(level instanceof ServerLevel l){if(l.dimension().equals(ARENA))return true;for(Fight f:FIGHTS.values())if(f.level==l&&Vec3.atCenterOf(pos).distanceToSqr(f.center)<625)return true;}return false;}
}
