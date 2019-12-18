package com.ibasco.ucgdisplay.tools.beans;

import java.util.*;

public class Controller implements Comparable<Controller> {

    private String name;

    private Set<Vendor> vendorList = new HashSet<>();

    public Controller(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public Set<Vendor> getVendorList() {
        return vendorList;
    }

    @Override
    public String toString() {
        return name.toUpperCase();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Controller that = (Controller) o;
        return name.equals(that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public int compareTo(Controller o) {
        return this.name.compareTo(o.name);
    }
}
