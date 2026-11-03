package com.arcanerealms;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
public final class PlayerMagic {
    private PlayerMagic(){}
    public static CompoundTag data(ServerPlayer p){
        CompoundTag root=p.getPersistentData();
        if(!root.contains("ArcaneRealms")){CompoundTag d=new CompoundTag();d.putInt("skill_points",5);d.putInt("mana",100);root.put("ArcaneRealms",d);}
        return root.getCompoundOrEmpty("ArcaneRealms");
    }
    public static int value(ServerPlayer p,String key,int fallback){return data(p).getIntOr(key,fallback);}
    public static int level(ServerPlayer p,String element){return Math.max(1,value(p,"lvl_"+element,1));}
    public static boolean learned(ServerPlayer p,String id){return data(p).getBooleanOr("unlocked_"+id,false);}
    public static void message(ServerPlayer p,String text){p.sendSystemMessage(Component.literal("[Arcane Realms] "+text));}
    public static long time(ServerPlayer p){return p.level().getServer().overworld().getGameTime();}
    public static int unlock(ServerPlayer p,String id){
        Spell s=Spell.ALL.get(id);if(s==null||learned(p,id))return 0;
        if(!s.element().equals("combo")&&level(p,s.element())<s.tier()){message(p,"Нужен уровень "+s.tier());return 0;}
        for(String parent:s.prerequisites())if(!learned(p,parent)){message(p,"Сначала изучите "+Spell.ALL.get(parent).name());return 0;}
        int points=value(p,"skill_points",0);if(points<=0){message(p,"Нужны очки навыков.");return 0;}
        data(p).putInt("skill_points",points-1);data(p).putBoolean("unlocked_"+id,true);message(p,"Изучено: "+s.name());return 1;
    }
    public static void award(ServerPlayer p,String element){
        int level=level(p,element),xp=value(p,"xp_"+element,0)+10;
        if(xp>=Spell.xpNeeded(level)&&level<50){xp-=Spell.xpNeeded(level);data(p).putInt("lvl_"+element,++level);data(p).putInt("skill_points",value(p,"skill_points",0)+1);message(p,"Новый уровень "+element+": "+level+"! +1 очко.");}
        data(p).putInt("xp_"+element,Math.min(xp,Spell.xpNeeded(50)));
    }
    public static boolean spend(ServerPlayer p,Spell s){
        if(!p.isAlive()||p.isSpectator()||!learned(p,s.id())||!Duels.canCast(p))return false;
        CompoundTag d=data(p);long now=time(p);
        if(now<d.getLongOr("cd_"+s.id(),0)||now<d.getLongOr("global_cd",0))return false;
        if(value(p,"mana",100)<s.mana()){message(p,"Недостаточно маны.");return false;}
        d.putInt("mana",value(p,"mana",100)-s.mana());d.putLong("cd_"+s.id(),now+s.cooldown()*20L);d.putLong("global_cd",now+5);
        if(s.element().equals("combo"))for(String parent:s.prerequisites())award(p,Spell.ALL.get(parent).element());else award(p,s.element());
        return true;
    }
}
