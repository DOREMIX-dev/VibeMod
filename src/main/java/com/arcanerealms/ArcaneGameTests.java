package com.arcanerealms;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.*;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.gametest.*;
@GameTestNamespace(ArcaneRealms.MOD_ID)
public final class ArcaneGameTests {
    public static void register(net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext context){
        var tests=net.minecraftforge.registries.DeferredRegister.create(net.minecraft.core.registries.Registries.TEST_FUNCTION,ArcaneRealms.MOD_ID);
        tests.register("mana",()->ArcaneGameTests::mana);
        tests.register("requirements",()->ArcaneGameTests::requirements);
        tests.register("clone",()->ArcaneGameTests::cloneProgress);
        tests.register("protection",()->ArcaneGameTests::protection);
        tests.register(context.getModBusGroup());
    }
    private static ServerPlayer player(GameTestHelper h){return new ServerPlayer(h.getLevel().getServer(),h.getLevel(),new GameProfile(UUID.randomUUID(),"ArcaneTest"),ClientInformation.createDefault()){@Override public void sendSystemMessage(Component message){}};}
    @GameTest public static void mana(GameTestHelper h){
        ServerPlayer p=player(h);Spell s=Spell.ALL.get("spark");h.assertFalse(PlayerMagic.spend(p,s),"Reject unlearned cast");h.assertTrue(PlayerMagic.unlock(p,"spark")==1,"Unlock starter");h.assertTrue(PlayerMagic.spend(p,s),"First cast");
        h.assertTrue(PlayerMagic.value(p,"mana",0)==92,"Charge once");h.assertFalse(PlayerMagic.spend(p,s),"Reject replay");h.assertTrue(PlayerMagic.value(p,"mana",0)==92,"Replay cannot charge");h.succeed();
    }
    @GameTest public static void requirements(GameTestHelper h){
        ServerPlayer p=player(h);h.assertTrue(PlayerMagic.unlock(p,"steam")==0,"Combo needs parents");h.assertTrue(PlayerMagic.value(p,"skill_points",0)==5,"Rejected unlock costs nothing");
        PlayerMagic.unlock(p,"heal");PlayerMagic.data(p).putInt("mana",0);h.assertFalse(PlayerMagic.spend(p,Spell.ALL.get("heal")),"Reject insufficient mana");h.assertTrue(PlayerMagic.data(p).getLongOr("cd_heal",0)==0,"Rejected cast cannot start cooldown");h.succeed();
    }
    @GameTest public static void cloneProgress(GameTestHelper h){
        ServerPlayer old=player(h),copy=player(h);PlayerMagic.unlock(old,"spark");PlayerMagic.data(old).putInt("rating",1200);PlayerEvent.Clone.BUS.post(new PlayerEvent.Clone(copy,old,true));
        h.assertTrue(PlayerMagic.learned(copy,"spark"),"Spell survives death");h.assertTrue(PlayerMagic.value(copy,"rating",0)==1200,"Rating survives death");PlayerMagic.data(copy).putInt("rating",900);h.assertTrue(PlayerMagic.value(old,"rating",0)==1200,"Copies must not alias");h.succeed();
    }
    @GameTest public static void protection(GameTestHelper h){
        ServerPlayer a=player(h),b=player(h);h.assertFalse(Duels.allowed(a,b),"Protect outsiders");h.assertTrue(h.getLevel().getServer().getResourceManager().getResource(net.minecraft.resources.Identifier.parse("arcanerealms:dimension/arena.json")).isPresent(),"Arena resource exists");h.succeed();
    }
}
