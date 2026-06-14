package com.vmod.modules;

import net.minecraft.client.MinecraftClient;

/**
 * Base class for all VMod modules.
 * Each module has a name, description, category, and enabled flag.
 */
public abstract class Module {

    public enum Category { COMBAT, MOVEMENT, RENDER, MISC }

    protected static final MinecraftClient mc = MinecraftClient.getInstance();

    private final String   name;
    private final String   description;
    private final Category category;
    private boolean        enabled = false;

    protected Module(String name, String description, Category category) {
        this.name        = name;
        this.description = description;
        this.category    = category;
    }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    public void onEnable()  {}
    public void onDisable() {}

    /** Called every client tick while the module is enabled. */
    public void onTick(MinecraftClient client) {}

    // ── Accessors ─────────────────────────────────────────────────────────────

    public String   getName()        { return name; }
    public String   getDescription() { return description; }
    public Category getCategory()    { return category; }
    public boolean  isEnabled()      { return enabled; }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (enabled) onEnable();
        else         onDisable();
    }

    public void toggle() { setEnabled(!enabled); }
}
