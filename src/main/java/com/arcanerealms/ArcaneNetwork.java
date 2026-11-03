package com.arcanerealms;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.*;
public final class ArcaneNetwork {
    static final SimpleChannel CHANNEL=ChannelBuilder.named("arcanerealms:main").networkProtocolVersion(1).simpleChannel();
    public static CompoundTag clientData=new CompoundTag();
    public static String clientInvitation="";
    public static long clientTime;
    record Action(int action,String id){}
    record State(CompoundTag data,String invitation,long time){}
    public static void init(){
        CHANNEL.messageBuilder(Action.class,NetworkDirection.PLAY_TO_SERVER)
            .encoder((m,b)->{b.writeByte(m.action());b.writeUtf(m.id(),64);})
            .decoder(b->new Action(b.readUnsignedByte(),b.readUtf(64)))
            .consumerMainThread((m,c)->{
                ServerPlayer p=c.getSender();if(p==null||!p.isAlive())return;
                long now=PlayerMagic.time(p);if(now<PlayerMagic.data(p).getLongOr("packet_cd",0))return;
                PlayerMagic.data(p).putLong("packet_cd",now+2);
                switch(m.action()){
                    case 0->PlayerMagic.unlock(p,m.id());case 1->Magic.cast(p,m.id());case 2->Duels.accept(p);case 3->Duels.decline(p);case 4->Duels.surrender(p);case 5->Duels.draw(p);
                    case 6->{String[] args=m.id().split(":",2);if(args.length==2){ServerPlayer other=p.level().getServer().getPlayerList().getPlayerByName(args[0]);if(other!=null)Duels.challenge(p,other,args[1]);}}
                    case 7->{String[] args=m.id().split(":",2);if(args.length==2&&Set.of("0","1","2").contains(args[0])&&Spell.ALL.containsKey(args[1])&&PlayerMagic.learned(p,args[1]))PlayerMagic.data(p).putString("slot_"+args[0],args[1]);}
                    default->{}
                }
                sync(p);
            }).add();
        CHANNEL.messageBuilder(State.class,NetworkDirection.PLAY_TO_CLIENT)
            .encoder((m,b)->{b.writeNbt(m.data());b.writeUtf(m.invitation(),128);b.writeLong(m.time());})
            .decoder(b->new State(b.readNbt(),b.readUtf(128),b.readLong()))
            .consumerMainThread((m,c)->{clientData=m.data()==null?new CompoundTag():m.data();clientInvitation=m.invitation();clientTime=m.time();}).add();
    }
    public static void send(int action,String id){CHANNEL.send(new Action(action,id),PacketDistributor.SERVER.noArg());}
    public static void sync(ServerPlayer p){
        CompoundTag source=PlayerMagic.data(p),d=new CompoundTag();
        for(String key:new String[]{"mana","skill_points","rating","wins","losses","draws"})d.putInt(key,source.getIntOr(key,key.equals("rating")?1000:0));
        for(String e:Spell.ELEMENTS){d.putInt("lvl_"+e,PlayerMagic.level(p,e));d.putInt("xp_"+e,source.getIntOr("xp_"+e,0));}
        for(Spell spell:Spell.ALL.values()){d.putBoolean("unlocked_"+spell.id(),PlayerMagic.learned(p,spell.id()));d.putLong("cd_"+spell.id(),source.getLongOr("cd_"+spell.id(),0));}
        for(int i=0;i<3;i++)d.putString("slot_"+i,source.getStringOr("slot_"+i,""));
        CHANNEL.send(new State(d,Duels.invitation(p),PlayerMagic.time(p)),PacketDistributor.PLAYER.with(p));
    }
}
