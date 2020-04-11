/*
 * (C) Copyright 2020 The DKPerms Project (Davide Wietlisbach & Philipp Elvin Friedhoff)
 *
 * @author Davide Wietlisbach
 * @since 08.04.20, 10:24
 * @website %web%
 *
 * %license%
 */

package ch.dkrieger.permissionsystem.lib.player;

public class PlayerDesign {

    public static PlayerDesign EMPTY = new PlayerDesign("","","","");

    private String prefix, suffix, display, color;

    public PlayerDesign(String prefix, String suffix, String display, String color) {
        this.prefix = prefix;
        this.suffix = suffix;
        this.display = display;
        this.color = color;
    }
    public String getPrefix() {
        return prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public String getDisplay() {
        return display;
    }

    public String getColor() {
        return color;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public void setDisplay(String display) {
        this.display = display;
    }

    public void setColor(String color) {
        this.color = color;
    }
}
