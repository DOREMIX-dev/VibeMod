package com.arcanerealms;
import java.util.*;
import net.minecraft.core.particles.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
public final class Magic {
    private static UUID damageOwner,damageTarget;
    private static final List<Bolt> BOLTS=new ArrayList<>();
    private static final List<Field> FIELDS=new ArrayList<>();
    private static final class Bolt {
        final UUID owner;final ServerLevel level;final Spell spell;final Vec3 velocity;Vec3 pos;int life=35;
        Bolt(ServerPlayer p,Spell s,Vec3 pos,Vec3 velocity){owner=p.getUUID();level=p.level();spell=s;this.pos=pos;this.velocity=velocity;}
    }
    private record Field(UUID owner,ServerLevel level,Spell spell,Vec3 pos,long ends,UUID guardian){}
    public static boolean authorizedDamage(UUID source,UUID target){return Objects.equals(source,damageOwner)&&Objects.equals(target,damageTarget)&&damageOwner!=null;}
    public static void register(){
        LivingAttackEvent.BUS.addListener(e->{return e.getEntity().getPersistentData().getBooleanOr("arcane_guardian",false);});
        EntityJoinLevelEvent.BUS.addListener(e->{return !e.getLevel().isClientSide()&&e.getEntity().getPersistentData().getBooleanOr("arcane_guardian",false)&&FIELDS.stream().noneMatch(f->e.getEntity().getUUID().equals(f.guardian));});
    }
    private static void discard(Field f){if(f.guardian!=null){Entity e=f.level.getEntity(f.guardian);if(e!=null)e.discard();}FIELDS.remove(f);}
    public static void clear(){BOLTS.clear();for(Field f:new ArrayList<>(FIELDS))discard(f);}
    public static void remove(UUID owner){BOLTS.removeIf(b->b.owner.equals(owner));for(Field f:new ArrayList<>(FIELDS))if(f.owner.equals(owner))discard(f);}
    static ParticleOptions particle(String e){return switch(e){case "fire"->ParticleTypes.FLAME;case "water"->ParticleTypes.SPLASH;case "ice"->ParticleTypes.SNOWFLAKE;case "lightning"->ParticleTypes.ELECTRIC_SPARK;case "nature"->ParticleTypes.HAPPY_VILLAGER;default->ParticleTypes.CLOUD;};}
    static void particles(ServerLevel l,Vec3 pos,String e,int count){l.sendParticles(particle(e),pos.x,pos.y,pos.z,count,.25,.35,.25,.02);}
    private static void line(ServerLevel l,Vec3 a,Vec3 b,String e){int count=Math.min(40,(int)(a.distanceTo(b)*3)+1);for(int n=0;n<count;n++)particles(l,a.lerp(b,(double)n/count),e,1);}
    private static boolean guardian(LivingEntity e){return e.getPersistentData().getBooleanOr("arcane_guardian",false);}
    private static Vec3 aim(ServerPlayer p,double range){
        Vec3 from=p.getEyePosition(),end=p.level().clip(new ClipContext(from,from.add(p.getLookAngle().scale(range)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getLocation();
        double nearest=from.distanceToSqr(end);Vec3 result=end;
        for(LivingEntity e:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(from,end).inflate(1),e->e!=p&&!guardian(e)&&Duels.allowed(p,e))){
            Optional<Vec3> hit=e.getBoundingBox().inflate(.3).clip(from,end);
            if(hit.isPresent()&&from.distanceToSqr(hit.get())<nearest){result=hit.get();nearest=from.distanceToSqr(result);}
        }
        return result;
    }
    private static List<LivingEntity> targets(ServerPlayer p,Vec3 center,double radius){
        return p.level().getEntitiesOfClass(LivingEntity.class,new AABB(center,center).inflate(radius),e->e!=p&&e.isAlive()&&!guardian(e)&&e.position().distanceToSqr(center)<=radius*radius&&Duels.allowed(p,e)&&p.hasLineOfSight(e));
    }
    private static void hit(ServerPlayer p,LivingEntity target,Spell s){
        if(!target.isAlive()||!Duels.allowed(p,target))return;
        float amount=switch(s.id()){case "meteor"->10;case "fireball","thermal_shock"->8;case "roots","freeze"->2;default->5;};
        damageOwner=p.getUUID();damageTarget=target.getUUID();
        try{target.hurtServer(p.level(),p.damageSources().indirectMagic(p,p),amount);}finally{damageOwner=null;damageTarget=null;}
        if(target instanceof ServerPlayer&&!Duels.allowed(p,target))return;
        switch(s.id()){
            case "spark","fireball","flame_wall","meteor","lava"->{if(!(target instanceof ServerPlayer))target.igniteForSeconds(3);}
            case "ice_needle","tidal_surge","electric_wave","thunderstorm"->target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,60,1));
            case "freeze","roots"->target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS,60,4));
            case "steam"->target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS,40,0));
        }
        particles(p.level(),target.getBoundingBox().getCenter(),s.element(),18);
    }
    private static void area(ServerPlayer p,Spell s,Vec3 center,double radius){for(LivingEntity e:targets(p,center,radius)){hit(p,e,s);if(s.id().equals("living_lightning"))p.heal(1);}}
    private static void field(ServerPlayer p,Spell s,Vec3 center){
        UUID id=null;var entity=s.id().equals("guardian")?EntityTypes.IRON_GOLEM.create(p.level(),EntitySpawnReason.MOB_SUMMONED):null;
        if(entity!=null){entity.setPos(center.x,center.y,center.z);entity.setNoAi(true);entity.setCustomName(net.minecraft.network.chat.Component.literal("Магический страж"));entity.getPersistentData().putBoolean("arcane_guardian",true);id=entity.getUUID();}
        FIELDS.add(new Field(p.getUUID(),p.level(),s,center,PlayerMagic.time(p)+100,id));
        if(entity!=null)p.level().addFreshEntity(entity);
    }
    public static int cast(ServerPlayer p,String id){
        Spell s=Spell.ALL.get(id);if(s==null||!PlayerMagic.spend(p,s))return 0;
        Vec3 center=aim(p,12);
        switch(id){
            case "heal"->{p.heal(6);particles(p.level(),p.position().add(0,1,0),"nature",30);}
            case "frost_armor","stone_shield","water_guard"->{p.addEffect(new MobEffectInstance(MobEffects.RESISTANCE,160,id.equals("stone_shield")?1:0));if(id.equals("water_guard"))p.clearFire();particles(p.level(),p.position().add(0,1,0),s.element(),30);}
            case "haste"->p.addEffect(new MobEffectInstance(MobEffects.SPEED,160,1));
            case "flame_wall","lava","thunderstorm","guardian"->field(p,s,id.equals("guardian")?p.position().add(p.getLookAngle().multiply(3,0,3)):center);
            case "tidal_surge","electric_wave"->{area(p,s,p.position().add(p.getLookAngle().scale(4)),6);for(int n=0;n<24;n++)particles(p.level(),p.position().add(Math.cos(n*Math.PI/12)*5,1,Math.sin(n*Math.PI/12)*5),s.element(),3);}
            case "chain_lightning","living_lightning"->{int n=0;for(LivingEntity e:targets(p,center,6)){if(n++>=4)break;line(p.level(),p.getEyePosition(),e.getEyePosition(),"lightning");hit(p,e,s);}if(id.equals("living_lightning")&&n>0)p.heal(Math.min(4,n));}
            case "steam","thermal_shock","freeze","roots","shock"->{line(p.level(),p.getEyePosition(),center,s.element());area(p,s,center,3);}
            case "meteor"->BOLTS.add(new Bolt(p,s,center.add(0,12,0),new Vec3(0,-.8,0)));
            default->BOLTS.add(new Bolt(p,s,p.getEyePosition().add(p.getLookAngle()),p.getLookAngle().scale(.8)));
        }
        return 1;
    }
    public static void tick(MinecraftServer server){
        for(Bolt b:new ArrayList<>(BOLTS)){
            if(!BOLTS.contains(b))continue;
            ServerPlayer p=server.getPlayerList().getPlayer(b.owner);
            if(p==null||p.level()!=b.level||--b.life<0){BOLTS.remove(b);continue;}
            Vec3 next=b.pos.add(b.velocity);boolean cancel=false;
            for(Bolt other:new ArrayList<>(BOLTS))if(other!=b&&!other.owner.equals(b.owner)&&other.level==b.level&&((b.spell.element().equals("fire")&&other.spell.element().equals("ice"))||(b.spell.element().equals("ice")&&other.spell.element().equals("fire")))&&other.pos.distanceToSqr(next)<1.5){
                particles(b.level,next,"combo",30);BOLTS.remove(other);BOLTS.remove(b);cancel=true;break;
            }
            if(cancel)continue;
            HitResult wall=b.level.clip(new ClipContext(b.pos,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));Vec3 end=wall.getLocation();
            LivingEntity victim=null;double nearest=b.pos.distanceToSqr(end);
            for(LivingEntity e:b.level.getEntitiesOfClass(LivingEntity.class,new AABB(b.pos,end).inflate(1),e->e!=p&&e.isAlive()&&!guardian(e)&&Duels.allowed(p,e))){
                Optional<Vec3> contact=e.getBoundingBox().inflate(.25).clip(b.pos,end);
                if(contact.isPresent()&&b.pos.distanceToSqr(contact.get())<=nearest){victim=e;nearest=b.pos.distanceToSqr(contact.get());}
            }
            line(b.level,b.pos,end,b.spell.element());b.pos=next;
            if(victim!=null){hit(p,victim,b.spell);if(b.spell.id().equals("meteor"))area(p,b.spell,end,4);BOLTS.remove(b);}
            else if(wall.getType()!=HitResult.Type.MISS){if(b.spell.id().equals("meteor"))area(p,b.spell,end,4);BOLTS.remove(b);}
        }
        long now=server.overworld().getGameTime();
        for(Field f:new ArrayList<>(FIELDS)){
            if(!FIELDS.contains(f))continue;ServerPlayer p=server.getPlayerList().getPlayer(f.owner);
            if(p==null||p.level()!=f.level||now>=f.ends){discard(f);continue;}
            if(now%5==0){
                if(f.spell.id().equals("lava"))f.level.sendParticles(ParticleTypes.LAVA,f.pos.x,f.pos.y+.2,f.pos.z,25,2,.1,2,.02);
                else for(int n=-3;n<=3;n++)particles(f.level,f.pos.add(n,f.spell.id().equals("flame_wall")?1:0,0),f.spell.element().equals("combo")?"lightning":f.spell.element(),8);
            }
            if(now%20==0){
                if(f.spell.id().equals("guardian")){List<LivingEntity> ts=targets(p,f.pos,7);if(!ts.isEmpty()){line(f.level,f.pos.add(0,1.5,0),ts.getFirst().getEyePosition(),"nature");hit(p,ts.getFirst(),f.spell);}}
                else if(f.spell.id().equals("flame_wall")){for(LivingEntity e:targets(p,f.pos,4))if(Math.abs(e.getZ()-f.pos.z)<1&&Math.abs(e.getY()-f.pos.y)<3)hit(p,e,f.spell);}
                else area(p,f.spell,f.pos,4);
            }
        }
    }
}
