package com.arcanerealms;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
@Mod(ArcaneRealms.MOD_ID)
public final class ArcaneRealms {
    public static final String MOD_ID="arcanerealms";
    public ArcaneRealms(FMLJavaModLoadingContext context){
        ArcaneGameTests.register(context);ArcaneNetwork.init();Duels.register();Magic.register();
        RegisterCommandsEvent.BUS.addListener(this::commands);
        TickEvent.ServerTickEvent.Post.BUS.addListener(e->{
            Magic.tick(e.server());Duels.tick(e.server());
            for(ServerPlayer p:e.server().getPlayerList().getPlayers()){
                if(PlayerMagic.time(p)%20==0)PlayerMagic.data(p).putInt("mana",Math.min(100,PlayerMagic.value(p,"mana",100)+2));
                if(PlayerMagic.time(p)%10==0)ArcaneNetwork.sync(p);
            }
        });
        PlayerEvent.Clone.BUS.addListener(e->e.getEntity().getPersistentData().put("ArcaneRealms",e.getOriginal().getPersistentData().getCompoundOrEmpty("ArcaneRealms").copy()));
        PlayerEvent.PlayerLoggedInEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p){Duels.recover(p);ArcaneNetwork.sync(p);PlayerMagic.message(p,"K — книга магии, /arcane skills — заклинания.");}});
        PlayerEvent.PlayerLoggedOutEvent.BUS.addListener(e->{if(e.getEntity() instanceof ServerPlayer p)Duels.logout(p);});
        ServerStoppingEvent.BUS.addListener(e->{Duels.stop(e.getServer());Magic.clear();});
    }
    private void commands(RegisterCommandsEvent e){
        e.getDispatcher().register(Commands.literal("arcane")
            .then(Commands.literal("status").executes(c->{ServerPlayer p=c.getSource().getPlayerOrException();PlayerMagic.message(p,"Мана "+PlayerMagic.value(p,"mana",100)+"/100 | Очки "+PlayerMagic.value(p,"skill_points",5)+" | Рейтинг "+PlayerMagic.value(p,"rating",1000));return 1;}))
            .then(Commands.literal("skills").executes(c->{for(Spell s:Spell.ALL.values())PlayerMagic.message(c.getSource().getPlayerOrException(),s.id()+": "+s.name()+" | "+s.mana()+" маны | ур. "+s.tier()+" | "+s.prerequisites());return 1;}))
            .then(Commands.literal("unlock").then(Commands.argument("spell",StringArgumentType.word()).suggests((c,b)->{Spell.ALL.keySet().forEach(b::suggest);return b.buildFuture();}).executes(c->PlayerMagic.unlock(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"spell")))))
            .then(Commands.literal("cast").then(Commands.argument("spell",StringArgumentType.word()).suggests((c,b)->{Spell.ALL.keySet().forEach(b::suggest);return b.buildFuture();}).executes(c->Magic.cast(c.getSource().getPlayerOrException(),StringArgumentType.getString(c,"spell")))))
            .then(Commands.literal("duel")
                .then(Commands.literal("challenge").then(Commands.argument("player",EntityArgument.player()).then(Commands.argument("mode",StringArgumentType.word()).suggests((c,b)->{for(String s:new String[]{"world","arena","ranked"})b.suggest(s);return b.buildFuture();}).executes(c->Duels.challenge(c.getSource().getPlayerOrException(),EntityArgument.getPlayer(c,"player"),StringArgumentType.getString(c,"mode"))))))
                .then(Commands.literal("accept").executes(c->Duels.accept(c.getSource().getPlayerOrException())))
                .then(Commands.literal("decline").executes(c->Duels.decline(c.getSource().getPlayerOrException())))
                .then(Commands.literal("surrender").executes(c->Duels.surrender(c.getSource().getPlayerOrException())))
                .then(Commands.literal("draw").executes(c->Duels.draw(c.getSource().getPlayerOrException())))));
    }
}
