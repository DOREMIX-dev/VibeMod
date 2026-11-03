package com.arcanerealms;
import net.minecraft.client.*;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
@Mod.EventBusSubscriber(modid=ArcaneRealms.MOD_ID,value=Dist.CLIENT)
public final class ArcaneClient {
    static final KeyMapping BOOK=new KeyMapping("key.arcanerealms.book",InputConstants.KEY_K,KeyMapping.Category.GAMEPLAY);
    static final KeyMapping[] CAST={new KeyMapping("key.arcanerealms.cast1",InputConstants.KEY_R,KeyMapping.Category.GAMEPLAY),new KeyMapping("key.arcanerealms.cast2",InputConstants.KEY_Z,KeyMapping.Category.GAMEPLAY),new KeyMapping("key.arcanerealms.cast3",InputConstants.KEY_X,KeyMapping.Category.GAMEPLAY)};
    @SubscribeEvent public static void keys(RegisterKeyMappingsEvent e){e.register(BOOK);for(KeyMapping k:CAST)e.register(k);}
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent.Post e){
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null){ArcaneNetwork.clientData=new net.minecraft.nbt.CompoundTag();ArcaneNetwork.clientInvitation="";return;}
        if(mc.gui.screen()==null){while(BOOK.consumeClick())mc.gui.setScreen(new Book());for(int i=0;i<3;i++)while(CAST[i].consumeClick())ArcaneNetwork.send(1,ArcaneNetwork.clientData.getStringOr("slot_"+i,""));}
    }
    @SubscribeEvent public static void overlay(AddGuiOverlayLayersEvent e){e.getLayeredDraw().add(Identifier.fromNamespaceAndPath(ArcaneRealms.MOD_ID,"mana"),(g,d)->hud(g));}
    private static void hud(GuiGraphicsExtractor g){
        Minecraft mc=Minecraft.getInstance();if(mc.player==null||mc.gui.hud.isHidden())return;
        var d=ArcaneNetwork.clientData;int mana=Math.clamp(d.getIntOr("mana",100),0,100);
        g.fill(8,8,112,23,0xbb101521);g.fill(10,10,10+mana,21,0xff397ee8);g.text(mc.font,"Мана "+mana+" / 100",14,11,0xffffffff);
        for(int i=0;i<3;i++){Spell s=Spell.ALL.get(d.getStringOr("slot_"+i,""));long cd=s==null?0:Math.max(0,(d.getLongOr("cd_"+s.id(),0)-ArcaneNetwork.clientTime+19)/20);g.text(mc.font,CAST[i].getTranslatedKeyMessage().getString()+": "+(s==null?"—":s.name())+(cd>0?" ["+cd+"с]":""),10,29+i*12,0xffe5e5ff);}
        if(!ArcaneNetwork.clientInvitation.isEmpty())g.text(mc.font,"Вызов на дуэль! Откройте K",10,68,0xffffd47f);
    }
    static final class Book extends Screen {
        int tab,page,slot;EditBox name;
        Book(){super(Component.literal("Arcane Realms"));}
        void button(String text,int x,int y,int w,Runnable action){addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());}
        @Override protected void init(){
            int left=width/2-150;String[] names={"Огонь","Вода","Лёд","Молния","Природа","Смеси"};
            for(int i=0;i<6;i++){final int t=i;button(names[i],left+i*50,27,49,()->{tab=t;page=0;rebuildWidgets();});}
            var list=Spell.ALL.values().stream().filter(s->s.element().equals(Spell.ELEMENTS.get(tab))).toList();
            for(int i=0;i<3&&page*3+i<list.size();i++){
                Spell s=list.get(page*3+i);int y=52+i*36;
                Button learn=Button.builder(Component.literal(s.name()),b->ArcaneNetwork.send(0,s.id())).bounds(left,y,190,20).build();
                learn.active=!ArcaneNetwork.clientData.getBooleanOr("unlocked_"+s.id(),false);
                learn.setTooltip(Tooltip.create(Component.literal("Уровень "+s.tier()+"; 1 очко. Требования: "+(s.prerequisites().isEmpty()?"нет":String.join(", ",s.prerequisites())))));addRenderableWidget(learn);
                button("В слот "+(slot+1),left+194,y,106,()->ArcaneNetwork.send(7,slot+":"+s.id()));
            }
            button("<",left,162,25,()->{page=Math.max(0,page-1);rebuildWidgets();});button(">",left+28,162,25,()->{page=Math.min((list.size()-1)/3,page+1);rebuildWidgets();});
            button("Слот: "+(slot+1),left+58,162,80,()->{slot=(slot+1)%3;rebuildWidgets();});
            button("Сдаться",left+142,162,76,()->ArcaneNetwork.send(4,""));button("Ничья",left+222,162,78,()->ArcaneNetwork.send(5,""));
            name=new EditBox(font,left,187,112,20,Component.literal("Имя игрока"));name.setMaxLength(16);name.setHint(Component.literal("Имя игрока"));addRenderableWidget(name);
            button("Мир",left+116,187,56,()->ArcaneNetwork.send(6,name.getValue()+":world"));button("Арена",left+176,187,60,()->ArcaneNetwork.send(6,name.getValue()+":arena"));button("Рейтинг",left+240,187,60,()->ArcaneNetwork.send(6,name.getValue()+":ranked"));
            button("Принять вызов",left,212,148,()->ArcaneNetwork.send(2,""));button("Отклонить",left+152,212,148,()->ArcaneNetwork.send(3,""));
        }
        @Override public void extractRenderState(GuiGraphicsExtractor g,int mx,int my,float dt){
            g.fill(0,0,width,height,0xe8151924);int left=width/2-150;var d=ArcaneNetwork.clientData;
            g.centeredText(font,"Arcane Realms · Очки: "+d.getIntOr("skill_points",5)+" · Рейтинг: "+d.getIntOr("rating",1000),width/2,7,0xffffd47f);super.extractRenderState(g,mx,my,dt);
            String element=Spell.ELEMENTS.get(tab);if(!element.equals("combo"))g.text(font,"Ур. "+d.getIntOr("lvl_"+element,1)+" · Опыт "+d.getIntOr("xp_"+element,0)+"/"+Spell.xpNeeded(d.getIntOr("lvl_"+element,1)),left,18,0xffaaaaee);
            var list=Spell.ALL.values().stream().filter(s->s.element().equals(Spell.ELEMENTS.get(tab))).toList();
            for(int i=0;i<3&&page*3+i<list.size();i++){Spell s=list.get(page*3+i);boolean known=d.getBooleanOr("unlocked_"+s.id(),false);g.text(font,(known?"✓ ":"")+s.mana()+" маны · "+s.cooldown()+"с · ур. "+s.tier(),left+3,74+i*36,known?0xff89e9a6:0xffbbbbcc);}
            if(height>249)g.text(font,ArcaneNetwork.clientInvitation.isEmpty()?"Название — изучить; R/Z/X — магия.":"Вызов: "+ArcaneNetwork.clientInvitation,left,239,0xffe5e5ff);
        }
        @Override public boolean isPauseScreen(){return false;}
    }
}
