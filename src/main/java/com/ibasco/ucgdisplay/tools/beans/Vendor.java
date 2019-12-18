package com.ibasco.ucgdisplay.tools.beans;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Vendor {

    private Controller controller;

    private String name;

    private int tileWidth;

    private int tileHeight;

    private String bufferLayout;

    private String notes;

    private Set<VendorConfig> vendorConfigs = new HashSet<>();

    public Vendor(Controller controller, String name) {
        this.controller = controller;
        this.name = name;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Controller getController() {
        return controller;
    }

    public void setController(Controller controller) {
        this.controller = controller;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTileWidth() {
        return tileWidth;
    }

    public void setTileWidth(int tileWidth) {
        this.tileWidth = tileWidth;
    }

    public int getTileHeight() {
        return tileHeight;
    }

    public void setTileHeight(int tileHeight) {
        this.tileHeight = tileHeight;
    }

    public String getBufferLayout() {
        return bufferLayout;
    }

    public void setBufferLayout(String bufferLayout) {
        this.bufferLayout = bufferLayout;
    }

    public Set<VendorConfig> getVendorConfigs() {
        return vendorConfigs;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Vendor vendor = (Vendor) o;
        return name.equals(vendor.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return "vendor: " +
                "name='" + name + '\'' +
                ", tileWidth=" + tileWidth +
                ", tileHeight=" + tileHeight +
                ", bufferLayout='" + bufferLayout + '\'' +
                ", notes='" + notes + '\'';
    }
}
