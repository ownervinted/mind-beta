package mind.beta.modules.module;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import mind.beta.modules.impl.combat.*;
import mind.beta.modules.impl.misc.*;
import mind.beta.modules.impl.movement.*;
import mind.beta.modules.impl.player.*;
import mind.beta.modules.impl.render.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class ModuleRepository {
    List<ModuleStructure> moduleStructures = new ArrayList<>();
    List<ModuleStructure> hiddenModules = new ArrayList<>();
    Set<Class<? extends ModuleStructure>> registeredClasses = new HashSet<>();

    public void setup() {
        builder()
                .add(new StorageViewer())
                .add(new Macros())
                .add(new TargetPearl())
                .add(new Predictions())
                .add(new Hud())
                .add(new Aura())
                .add(new MaceHelper())
                .add(new AutoSpear())
                .add(new AutoCrystal())
                .add(new HitEffect())
                .add(new Esp())
                .add(new BlockESP())
                .add(new AutoTool())
                .add(new RegionExploit())
                .add(new WorldParticles())
                .add(new Arrows())
                .add(new Particles())
                .add(new AuctionHelper())
                .add(new GlassHands())
                .add(new TriggerBot())
                .add(new BowSpammer())
                .add(new Ambience())
                .add(new AutoTotem())
                .add(new TapeMouse())
                .add(new ElytraHelper())
                .add(new ChinaHat())
                .add(new AutoPotion())
                .add(new Jesus())
                .add(new ClientSounds())
                .add(new AutoGApple())
                .add(new TargetESP())
                .add(new BlockOverlay())
                .add(new HitSound())
                .add(new ClickPearl())
                .add(new JumpEffect())
                .add(new ItemScroller())
                .add(new AutoLeave())
                .add(new Strafe())
                .add(new NoWeb())
                .add(new AutoTpAccept())
                .add(new Spider())
                .add(new ClickFriend())
                .add(new FreeLook())
                .add(new Fly())
                .add(new ElytraMotion())
                .add(new FullBright())
                .add(new CameraSettings())
                .add(new ItemPhysic())
                .add(new NoDelay())
                .add(new ServerRPSpoofer())
                .add(new SeeInvisible())
                .add(new AutoPilot())
                .add(new NoFallDamage())
                .add(new NoRender())
                .add(new ShiftTap())
                .add(new HitBoxModule())
                .add(new NameProtect())
                .add(new NoFriendDamage())
                .add(new InventoryMove())
                .add(new ChestStealer())
                .add(new NoInteract())
                .add(new AntiBot())
                .add(new ViewModel())
                .add(new ElytraTarget())
                .add(new FreeCam())
                .add(new Speed())
                .add(new NoEntityTrace())
                .add(new AutoRespawn())
                .add(new AutoSwap())
                .add(new NoPush())
                .add(new NoSlow())
                .add(new Velocity())
                .add(new SwingAnimation())
                .add(new AutoSprint())
                .add(new CustomRain())
                .add(new FakePlayer())
                .add(new WebTrap());
    }

    public ModuleBuilder builder() {
        return new ModuleBuilder(this);
    }

    void registerModule(ModuleStructure module, boolean hidden) {
        Class<? extends ModuleStructure> clazz = module.getClass();
        if (registeredClasses.contains(clazz)) {
            throw new DuplicateModuleException(clazz.getSimpleName());
        }
        registeredClasses.add(clazz);
        if (hidden) {
            hiddenModules.add(module);
            module.setState(true);
        } else {
            moduleStructures.add(module);
        }
    }

    public synchronized void registerDynamicModule(ModuleStructure module) {
        if (module == null) {
            throw new IllegalArgumentException("module");
        }
        boolean duplicateName = allModules().stream()
                .anyMatch(existing -> existing.getName().equalsIgnoreCase(module.getName()));
        if (duplicateName) {
            throw new IllegalArgumentException("Module with name '" + module.getName() + "' is already registered");
        }
        moduleStructures.add(module);
    }

    public synchronized void unregisterDynamicModule(ModuleStructure module) {
        if (module == null) {
            return;
        }
        if (module.isState()) {
            module.setState(false);
        }
        moduleStructures.remove(module);
        hiddenModules.remove(module);
    }

    public List<ModuleStructure> modules() {
        return moduleStructures;
    }

    public List<ModuleStructure> hiddenModules() {
        return hiddenModules;
    }

    public List<ModuleStructure> allModules() {
        List<ModuleStructure> all = new ArrayList<>(moduleStructures);
        all.addAll(hiddenModules);
        return all;
    }
}