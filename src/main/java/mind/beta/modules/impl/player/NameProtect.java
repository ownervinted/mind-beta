/**
 * Copyright (c) Mind beta
 */

package mind.beta.modules.impl.player;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.experimental.FieldDefaults;
import mind.beta.events.api.EventHandler;
import mind.beta.events.api.events.render.TextFactoryEvent;
import mind.beta.modules.module.ModuleStructure;
import mind.beta.modules.module.category.ModuleCategory;
import mind.beta.modules.module.setting.implement.BooleanSetting;
import mind.beta.modules.module.setting.implement.TextSetting;
import mind.beta.util.repository.friend.FriendUtils;

@Getter
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class NameProtect extends ModuleStructure {

    TextSetting nameSetting = new TextSetting(
            "Name",
            "Replacement nickname to display instead of your own"
    ).setText("Protected").setMax(32);

    BooleanSetting friendsSetting = new BooleanSetting(
            "Friends",
            "Hide friends' nicknames"
    ).setValue(true);

    public NameProtect() {
        super("NameProtect", "Name Protect", ModuleCategory.PLAYER);
        settings(
                nameSetting,
                friendsSetting
        );
    }

    @EventHandler
    public void onTextFactory(TextFactoryEvent e) {
        e.replaceText(mc.getSession().getUsername(), nameSetting.getText());

        if (friendsSetting.isValue()) {
            replaceFriendNames(e);
        }
    }

    private void replaceFriendNames(TextFactoryEvent e) {
        FriendUtils.getFriends().forEach(friend ->
                e.replaceText(friend.getName(), nameSetting.getText())
        );
    }
}