package mind.beta.screens.clickgui.impl.background.search;

import lombok.Getter;
import org.lwjgl.glfw.GLFW;
import mind.beta.IMinecraft;
import mind.beta.Initialization;
import mind.beta.modules.module.ModuleStructure;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
public class SearchHandler implements IMinecraft {

    private boolean searchActive = false;
    private boolean searchClosing = false;
    private String searchText = "";
    private int searchCursorPosition = 0;
    private int searchSelectionStart = -1;
    private int searchSelectionEnd = -1;
    private float searchCursorBlink = 0f;

    private float searchBoxAnimation = 0f;
    private float searchFocusAnimation = 0f;
    private float searchPanelAlpha = 0f;
    private float normalPanelAlpha = 1f;
    private float searchSelectionAnimation = 0f;

    private List<ModuleStructure> searchResults = new ArrayList<>();
    private List<ModuleStructure> searchRenderResults = new ArrayList<>();
    private Map<ModuleStructure, Float> searchResultAnimations = new HashMap<>();
    private Map<ModuleStructure, Long> searchResultAnimStartTimes = new HashMap<>();
    private Map<ModuleStructure, Float> searchResultAnimationTargets = new HashMap<>();
    private Map<ModuleStructure, Float> searchResultPositions = new HashMap<>();
    private Map<ModuleStructure, Float> searchResultPositionTargets = new HashMap<>();

    private float searchScrollOffset = 0f;
    private float searchTargetScroll = 0f;
    private int hoveredSearchIndex = -1;
    private ModuleStructure selectedSearchModule = null;
    private float searchViewportHeight = 150f;

    private static final float SEARCH_ANIM_SPEED = 8f;
    private static final float PANEL_FADE_SPEED = 12f;
    private static final float SEARCH_RESULT_HEIGHT = 18f;
    private static final float SEARCH_RESULT_GAP = 3f;
    private static final float SEARCH_RESULT_ANIM_DURATION = 220f;

    public void setSearchActive(boolean active) {
        if (active) {
            searchClosing = false;

            if (!searchActive) {
                searchText = "";
                searchCursorPosition = 0;
                searchSelectionStart = -1;
                searchSelectionEnd = -1;
                searchCursorBlink = 0f;
                searchSelectionAnimation = 0f;
                searchResults.clear();
                searchRenderResults.clear();
                searchResultAnimations.clear();
                searchResultAnimStartTimes.clear();
                searchResultAnimationTargets.clear();
                searchResultPositions.clear();
                searchResultPositionTargets.clear();
                searchScrollOffset = 0f;
                searchTargetScroll = 0f;
                hoveredSearchIndex = -1;
                selectedSearchModule = null;
            }

            searchActive = true;
            return;
        }

        if (searchActive) {
            searchActive = false;
            searchClosing = true;
        }
    }

    public boolean isSearchClosing() {
        return searchClosing;
    }

    public void setSearchViewportHeight(float height) {
        searchViewportHeight = Math.max(1f, height);
    }

    public void updateAnimations(float deltaTime) {
        float searchTarget = searchActive ? 1f : 0f;

        searchBoxAnimation = updateAnimation(
                searchBoxAnimation,
                searchTarget,
                SEARCH_ANIM_SPEED,
                deltaTime
        );

        searchFocusAnimation = updateAnimation(
                searchFocusAnimation,
                searchTarget,
                SEARCH_ANIM_SPEED,
                deltaTime
        );

        searchPanelAlpha = updateLinearAnimation(
                searchPanelAlpha,
                searchTarget,
                PANEL_FADE_SPEED,
                deltaTime
        );

        normalPanelAlpha = updateAnimation(
                normalPanelAlpha,
                1f,
                PANEL_FADE_SPEED,
                deltaTime
        );

        if (searchActive) {
            searchSelectionAnimation = updateAnimation(
                    searchSelectionAnimation,
                    hasSearchSelection() ? 1f : 0f,
                    SEARCH_ANIM_SPEED,
                    deltaTime
            );

            searchCursorBlink += deltaTime * 2f;

            if (searchCursorBlink > 1f) {
                searchCursorBlink -= 1f;
            }

            updateResultAnimations(deltaTime);
            updateScrollAnimation(deltaTime);
        }

        if (searchClosing && searchPanelAlpha <= 0.001f) {
            searchPanelAlpha = 0f;
            searchClosing = false;

            searchText = "";
            searchCursorPosition = 0;
            searchSelectionStart = -1;
            searchSelectionEnd = -1;
            searchCursorBlink = 0f;
            searchSelectionAnimation = 0f;

            searchResults.clear();
            searchRenderResults.clear();
            searchResultAnimations.clear();
            searchResultAnimStartTimes.clear();
            searchResultAnimationTargets.clear();
            searchResultPositions.clear();
            searchResultPositionTargets.clear();

            searchScrollOffset = 0f;
            searchTargetScroll = 0f;
            hoveredSearchIndex = -1;
            selectedSearchModule = null;
        }
    }

    private float updateLinearAnimation(
            float current,
            float target,
            float speed,
            float deltaTime
    ) {
        float step = speed * deltaTime;

        if (Math.abs(target - current) < step) {
            return target;
        }

        float result = current + Math.signum(target - current) * step;
        return Math.max(0f, Math.min(1f, result));
    }

    private float updateAnimation(
            float current,
            float target,
            float speed,
            float deltaTime
    ) {
        float factor = 1f - (float) Math.exp(-speed * deltaTime);
        float result = current + (target - current) * factor;

        if (Math.abs(target - result) < 0.001f) {
            return target;
        }

        return Math.max(0f, Math.min(1f, result));
    }

    private void updateResultAnimations(float deltaTime) {
        long currentTime = System.currentTimeMillis();
        float itemHeight = SEARCH_RESULT_HEIGHT + SEARCH_RESULT_GAP;

        for (int i = 0; i < searchResults.size(); i++) {
            ModuleStructure module = searchResults.get(i);
            float targetY = i * itemHeight;
            searchResultPositionTargets.put(module, targetY);

            if (!searchResultPositions.containsKey(module)) {
                searchResultPositions.put(module, targetY + 10f);
            }
        }

        for (ModuleStructure module : new ArrayList<>(searchRenderResults)) {
            Float currentValue = searchResultAnimations.get(module);
            if (currentValue == null) {
                currentValue = 0f;
            }

            Float target = searchResultAnimationTargets.get(module);
            if (target == null) {
                target = searchResults.contains(module) ? 1f : 0f;
            }

            Long startTime = searchResultAnimStartTimes.get(module);
            boolean waitingForEntrance =
                    target > currentValue
                            && startTime != null
                            && currentTime < startTime;

            if (!waitingForEntrance) {
                float speed = target > currentValue ? 11f : 13f;
                float factor = 1f - (float) Math.exp(-speed * Math.max(0.001f, deltaTime));
                float next = currentValue + (target - currentValue) * factor;

                if (Math.abs(target - next) < 0.0015f) {
                    next = target;
                }

                searchResultAnimations.put(module, Math.max(0f, Math.min(1f, next)));
            }

            Float currentY = searchResultPositions.get(module);
            if (currentY == null) {
                currentY = 0f;
            }

            Float targetY = searchResultPositionTargets.get(module);
            if (targetY == null) {
                targetY = currentY;
            }

            if (target <= 0f) {
                targetY = currentY;
            }

            float positionFactor = 1f - (float) Math.exp(-14f * Math.max(0.001f, deltaTime));
            float nextY = currentY + (targetY - currentY) * positionFactor;

            if (Math.abs(targetY - nextY) < 0.03f) {
                nextY = targetY;
            }

            searchResultPositions.put(module, nextY);

            Float animation = searchResultAnimations.get(module);
            if (target <= 0f && animation != null && animation <= 0.002f) {
                searchRenderResults.remove(module);
                searchResultAnimations.remove(module);
                searchResultAnimStartTimes.remove(module);
                searchResultAnimationTargets.remove(module);
                searchResultPositions.remove(module);
                searchResultPositionTargets.remove(module);
            }
        }
    }

    private void updateScrollAnimation(float deltaTime) {
        float difference = searchTargetScroll - searchScrollOffset;

        if (Math.abs(difference) < 0.5f) {
            searchScrollOffset = searchTargetScroll;
            return;
        }

        float factor = 1f - (float) Math.exp(-12f * deltaTime);

        searchScrollOffset += difference * factor;
    }

    private float easeInOutCubic(float value) {
        return value < 0.5f
                ? 4f * value * value * value
                : 1f - (float) Math.pow(-2f * value + 2f, 3f) / 2f;
    }

    public boolean hasSearchSelection() {
        return searchSelectionStart != -1
                && searchSelectionEnd != -1
                && searchSelectionStart != searchSelectionEnd;
    }

    public int getSearchSelectionStart() {
        return Math.min(searchSelectionStart, searchSelectionEnd);
    }

    public int getSearchSelectionEnd() {
        return Math.max(searchSelectionStart, searchSelectionEnd);
    }

    private void clearSearchSelection() {
        searchSelectionStart = -1;
        searchSelectionEnd = -1;
    }

    private void selectAllSearchText() {
        searchSelectionStart = 0;
        searchSelectionEnd = searchText.length();
        searchCursorPosition = searchText.length();
    }

    private void deleteSelectedSearchText() {
        if (!hasSearchSelection()) {
            return;
        }

        int start = getSearchSelectionStart();
        int end = getSearchSelectionEnd();

        searchText =
                searchText.substring(0, start)
                        + searchText.substring(end);

        searchCursorPosition = start;

        clearSearchSelection();
        updateSearchResults();
    }

    private String getSelectedSearchText() {
        if (!hasSearchSelection()) {
            return "";
        }

        return searchText.substring(
                getSearchSelectionStart(),
                getSearchSelectionEnd()
        );
    }

    private void copySearchToClipboard() {
        if (!hasSearchSelection()) {
            return;
        }

        GLFW.glfwSetClipboardString(
                mc.getWindow().getHandle(),
                getSelectedSearchText()
        );
    }

    private void pasteToSearch() {
        String clipboardText = GLFW.glfwGetClipboardString(
                mc.getWindow().getHandle()
        );

        if (clipboardText == null || clipboardText.isEmpty()) {
            return;
        }

        clipboardText = clipboardText.replaceAll("[\\n\\r\\t]", "");

        if (hasSearchSelection()) {
            deleteSelectedSearchText();
        }

        searchText =
                searchText.substring(0, searchCursorPosition)
                        + clipboardText
                        + searchText.substring(searchCursorPosition);

        searchCursorPosition += clipboardText.length();

        clearSearchSelection();
        updateSearchResults();
    }

    private boolean isControlDown() {
        long window = mc.getWindow().getHandle();

        return GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_LEFT_CONTROL
        ) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_RIGHT_CONTROL
        ) == GLFW.GLFW_PRESS;
    }

    private boolean isShiftDown() {
        long window = mc.getWindow().getHandle();

        return GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_LEFT_SHIFT
        ) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(
                window,
                GLFW.GLFW_KEY_RIGHT_SHIFT
        ) == GLFW.GLFW_PRESS;
    }

    public void updateSearchResults() {
        String query = searchText.toLowerCase();
        List<ModuleStructure> newResults = new ArrayList<>();

        if (!query.isEmpty()) {
            try {
                var repository =
                        Initialization.getInstance()
                                .getManager()
                                .getModuleRepository();

                if (repository != null) {
                    for (ModuleStructure module : repository.modules()) {
                        if (module.getName().toLowerCase().contains(query)) {
                            newResults.add(module);
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        long currentTime = System.currentTimeMillis();
        List<ModuleStructure> oldRenderResults = new ArrayList<>(searchRenderResults);

        for (ModuleStructure module : newResults) {
            int newIndex = newResults.indexOf(module);

            if (!searchRenderResults.contains(module)) {
                int insertIndex = searchRenderResults.size();

                for (int i = 0; i < searchRenderResults.size(); i++) {
                    ModuleStructure existing = searchRenderResults.get(i);
                    int existingIndex = newResults.indexOf(existing);

                    if (existingIndex >= 0 && existingIndex > newIndex) {
                        insertIndex = i;
                        break;
                    }
                }

                searchRenderResults.add(insertIndex, module);
                searchResultAnimations.put(module, 0f);
                searchResultPositions.put(
                        module,
                        newIndex * (SEARCH_RESULT_HEIGHT + SEARCH_RESULT_GAP) + 10f
                );
                searchResultAnimStartTimes.put(
                        module,
                        currentTime + Math.min(newIndex * 24L, 120L)
                );
            }

            searchResultAnimationTargets.put(module, 1f);
            searchResultPositionTargets.put(
                    module,
                    newIndex * (SEARCH_RESULT_HEIGHT + SEARCH_RESULT_GAP)
            );
        }

        for (ModuleStructure module : oldRenderResults) {
            if (!newResults.contains(module)) {
                searchResultAnimationTargets.put(module, 0f);
                searchResultAnimStartTimes.remove(module);
                searchResultPositionTargets.put(
                        module,
                        searchResultPositions.getOrDefault(module, 0f)
                );
            }
        }

        searchResults = newResults;

        if (searchResults.isEmpty()) {
            selectedSearchModule = null;
            hoveredSearchIndex = -1;
            searchTargetScroll = 0f;
        } else {
            if (selectedSearchModule == null
                    || !searchResults.contains(selectedSearchModule)) {
                selectedSearchModule = searchResults.get(0);
            }

            clampSelectionToScroll();
        }
    }

    private void clampSelectionToScroll() {
        if (selectedSearchModule == null) {
            return;
        }

        int index = searchResults.indexOf(selectedSearchModule);

        if (index < 0) {
            return;
        }

        float itemHeight =
                SEARCH_RESULT_HEIGHT + SEARCH_RESULT_GAP;

        float itemY = index * itemHeight;
        float visibleHeight = searchViewportHeight;

        if (itemY + SEARCH_RESULT_HEIGHT
                > -searchScrollOffset + visibleHeight) {

            searchTargetScroll =
                    -(itemY
                            + SEARCH_RESULT_HEIGHT
                            - visibleHeight);
        }

        if (itemY < -searchScrollOffset) {
            searchTargetScroll = -itemY;
        }
    }

    public boolean handleSearchChar(char chr) {
        if (!searchActive || searchClosing) {
            return false;
        }

        if (Character.isISOControl(chr)) {
            return false;
        }

        if (hasSearchSelection()) {
            deleteSelectedSearchText();
        }

        searchText =
                searchText.substring(0, searchCursorPosition)
                        + chr
                        + searchText.substring(searchCursorPosition);

        searchCursorPosition++;

        clearSearchSelection();
        searchCursorBlink = 0f;

        updateSearchResults();

        return true;
    }

    public boolean handleSearchKey(int keyCode) {
        if (isControlDown() && keyCode == GLFW.GLFW_KEY_F) {
            if (!searchActive && !searchClosing) {
                setSearchActive(true);
            }

            return true;
        }

        if (!searchActive || searchClosing) {
            return false;
        }

        if (isControlDown()) {
            switch (keyCode) {
                case GLFW.GLFW_KEY_A -> {
                    selectAllSearchText();
                    return true;
                }

                case GLFW.GLFW_KEY_C -> {
                    copySearchToClipboard();
                    return true;
                }

                case GLFW.GLFW_KEY_V -> {
                    pasteToSearch();
                    return true;
                }

                case GLFW.GLFW_KEY_X -> {
                    if (hasSearchSelection()) {
                        copySearchToClipboard();
                        deleteSelectedSearchText();
                    }

                    return true;
                }
            }
        }

        switch (keyCode) {
            case GLFW.GLFW_KEY_BACKSPACE -> {
                if (hasSearchSelection()) {
                    deleteSelectedSearchText();
                } else if (searchCursorPosition > 0) {
                    searchText =
                            searchText.substring(
                                    0,
                                    searchCursorPosition - 1
                            )
                                    + searchText.substring(
                                    searchCursorPosition
                            );

                    searchCursorPosition--;
                    searchCursorBlink = 0f;

                    updateSearchResults();
                }

                return true;
            }

            case GLFW.GLFW_KEY_DELETE -> {
                if (hasSearchSelection()) {
                    deleteSelectedSearchText();
                } else if (searchCursorPosition < searchText.length()) {
                    searchText =
                            searchText.substring(
                                    0,
                                    searchCursorPosition
                            )
                                    + searchText.substring(
                                    searchCursorPosition + 1
                            );

                    searchCursorBlink = 0f;

                    updateSearchResults();
                }

                return true;
            }

            case GLFW.GLFW_KEY_LEFT -> {
                handleLeftKey();
                searchCursorBlink = 0f;
                return true;
            }

            case GLFW.GLFW_KEY_RIGHT -> {
                handleRightKey();
                searchCursorBlink = 0f;
                return true;
            }

            case GLFW.GLFW_KEY_HOME -> {
                handleHomeKey();
                searchCursorBlink = 0f;
                return true;
            }

            case GLFW.GLFW_KEY_END -> {
                handleEndKey();
                searchCursorBlink = 0f;
                return true;
            }

            case GLFW.GLFW_KEY_UP -> {
                selectPrevious();
                return true;
            }

            case GLFW.GLFW_KEY_DOWN -> {
                selectNext();
                return true;
            }

            case GLFW.GLFW_KEY_ENTER -> {
                return true;
            }

            case GLFW.GLFW_KEY_ESCAPE -> {
                setSearchActive(false);
                return true;
            }
        }

        return false;
    }

    private void selectPrevious() {
        if (searchResults.isEmpty()) {
            return;
        }

        if (selectedSearchModule == null) {
            selectedSearchModule = searchResults.get(0);
            return;
        }

        int index = searchResults.indexOf(selectedSearchModule);

        if (index > 0) {
            selectedSearchModule =
                    searchResults.get(index - 1);

            clampSelectionToScroll();
        }
    }

    private void selectNext() {
        if (searchResults.isEmpty()) {
            return;
        }

        if (selectedSearchModule == null) {
            selectedSearchModule = searchResults.get(0);
            return;
        }

        int index = searchResults.indexOf(selectedSearchModule);

        if (index < searchResults.size() - 1) {
            selectedSearchModule =
                    searchResults.get(index + 1);

            clampSelectionToScroll();
        }
    }

    private void handleLeftKey() {
        if (hasSearchSelection() && !isShiftDown()) {
            searchCursorPosition =
                    getSearchSelectionStart();

            clearSearchSelection();
            return;
        }

        if (searchCursorPosition <= 0) {
            return;
        }

        if (isShiftDown()) {
            if (searchSelectionStart == -1) {
                searchSelectionStart =
                        searchCursorPosition;
            }

            searchCursorPosition--;
            searchSelectionEnd =
                    searchCursorPosition;
        } else {
            searchCursorPosition--;
            clearSearchSelection();
        }
    }

    private void handleRightKey() {
        if (hasSearchSelection() && !isShiftDown()) {
            searchCursorPosition =
                    getSearchSelectionEnd();

            clearSearchSelection();
            return;
        }

        if (searchCursorPosition >= searchText.length()) {
            return;
        }

        if (isShiftDown()) {
            if (searchSelectionStart == -1) {
                searchSelectionStart =
                        searchCursorPosition;
            }

            searchCursorPosition++;
            searchSelectionEnd =
                    searchCursorPosition;
        } else {
            searchCursorPosition++;
            clearSearchSelection();
        }
    }

    private void handleHomeKey() {
        if (isShiftDown()) {
            if (searchSelectionStart == -1) {
                searchSelectionStart =
                        searchCursorPosition;
            }

            searchCursorPosition = 0;
            searchSelectionEnd = 0;
        } else {
            searchCursorPosition = 0;
            clearSearchSelection();
        }
    }

    private void handleEndKey() {
        if (isShiftDown()) {
            if (searchSelectionStart == -1) {
                searchSelectionStart =
                        searchCursorPosition;
            }

            searchCursorPosition =
                    searchText.length();

            searchSelectionEnd =
                    searchCursorPosition;
        } else {
            searchCursorPosition =
                    searchText.length();

            clearSearchSelection();
        }
    }

    public void handleSearchScroll(
            double vertical,
            float panelHeight
    ) {
        if (!searchActive
                || searchClosing
                || searchResults.isEmpty()) {
            return;
        }

        float itemHeight =
                SEARCH_RESULT_HEIGHT + SEARCH_RESULT_GAP;

        float contentHeight =
                searchResults.size() * itemHeight
                        - SEARCH_RESULT_GAP;

        float visibleHeight =
                searchViewportHeight > 1f
                        ? searchViewportHeight
                        : panelHeight;

        float maxScroll =
                Math.max(
                        0f,
                        contentHeight - visibleHeight
                );

        searchTargetScroll =
                (float) Math.max(
                        -maxScroll,
                        Math.min(
                                0f,
                                searchTargetScroll
                                        + vertical * 25f
                        )
                );
    }

    public List<ModuleStructure> getSearchRenderResults() {
        return searchRenderResults;
    }

    public float getSearchResultPosition(ModuleStructure module) {
        return searchResultPositions.getOrDefault(
                module,
                0f
        );
    }

    public float getSearchResultHeight() {
        return SEARCH_RESULT_HEIGHT;
    }

    public float getSearchResultGap() {
        return SEARCH_RESULT_GAP;
    }

    public void setHoveredSearchIndex(int index) {
        if (searchClosing || !searchActive) {
            return;
        }

        hoveredSearchIndex = index;

        if (index >= 0
                && index < searchResults.size()) {

            selectedSearchModule =
                    searchResults.get(index);

            clampSelectionToScroll();
        }
    }

    public void setSelectedSearchModule(
            ModuleStructure module
    ) {
        if (searchClosing || !searchActive) {
            return;
        }

        if (module != null
                && searchResults.contains(module)) {

            selectedSearchModule = module;
            clampSelectionToScroll();
        }
    }
}