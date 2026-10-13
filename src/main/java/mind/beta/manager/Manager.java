package mind.beta.manager;

import lombok.Getter;
import mind.beta.client.draggables.HudManager;
import mind.beta.command.CommandManager;
import mind.beta.events.api.EventManager;
import mind.beta.modules.impl.combat.aura.attack.AttackExecutor;
import mind.beta.modules.module.ModuleRepository;
import mind.beta.modules.module.*;
import mind.beta.screens.clickgui.ClickGui;
import mind.beta.scripting.ScriptManager;
import mind.beta.util.config.ConfigSystem;
import mind.beta.util.config.impl.bind.BindConfig;
import mind.beta.util.config.impl.blockesp.BlockESPConfig;
import mind.beta.util.config.impl.drag.DragConfig;
import mind.beta.util.config.impl.friend.FriendConfig;
import mind.beta.util.config.impl.prefix.PrefixConfig;
import mind.beta.util.config.impl.proxy.ProxyConfig;
import mind.beta.util.config.impl.staff.StaffConfig;
import mind.beta.util.modules.ModuleProvider;
import mind.beta.util.modules.ModuleSwitcher;
import mind.beta.util.render.shader.RenderCore;
import mind.beta.util.render.shader.Scissor;
import mind.beta.util.render.font.FontInitializer;
import mind.beta.util.repository.macro.MacroRepository;
import mind.beta.util.repository.way.WayRepository;
import mind.beta.util.tps.TPSCalculate;

@Getter
public class Manager {
    public AttackExecutor attackPerpetrator = new AttackExecutor();
    private EventManager eventManager;
    private RenderCore renderCore;
    private Scissor scissor;
    private ModuleProvider moduleProvider;
    private ModuleRepository moduleRepository;
    private ModuleSwitcher moduleSwitcher;
    private ScriptManager scriptManager;
    private ClickGui clickgui;
    private ConfigSystem configSystem;
    private CommandManager commandManager;
    private TPSCalculate tpsCalculate;
    private HudManager hudManager = new HudManager();

    public void init() {
        MacroRepository.getInstance().init();
        WayRepository.getInstance().init();
        BlockESPConfig.getInstance().load();
        FriendConfig.getInstance().load();
        PrefixConfig.getInstance().load();
        StaffConfig.getInstance().load();
        ProxyConfig.getInstance().load();
        DragConfig.getInstance().load();
        BindConfig.getInstance();

        FontInitializer.register();

        tpsCalculate = new TPSCalculate();

        clickgui = new ClickGui();
        eventManager = new EventManager();
        renderCore = new RenderCore();
        scissor = new Scissor();
        hudManager = new HudManager();
        hudManager.initElements();
        moduleRepository = new ModuleRepository();
        moduleRepository.setup();
        scriptManager = new ScriptManager(moduleRepository);
        scriptManager.init();
        moduleProvider = new ModuleProvider(moduleRepository.modules());
        moduleSwitcher = new ModuleSwitcher(moduleRepository.modules(), eventManager);
        configSystem = new ConfigSystem();
        configSystem.init();
        commandManager = new CommandManager();
        commandManager.init();
    }
}