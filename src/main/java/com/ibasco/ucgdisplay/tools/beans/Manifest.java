package com.ibasco.ucgdisplay.tools.beans;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Manifest {

    private List<Controller> controllers = new ArrayList<>();

    private String md5Hash;

    private ZonedDateTime lastUpdated;

    public String getMd5Hash() {
        return md5Hash;
    }

    public void setMd5Hash(String md5Hash) {
        this.md5Hash = md5Hash;
    }

    public ZonedDateTime getLastUpdated() {
        return lastUpdated;
    }

    public void setLastUpdated(ZonedDateTime lastUpdated) {
        this.lastUpdated = lastUpdated;
    }

    public List<Controller> getControllers() {
        return controllers;
    }

    public void setControllers(List<Controller> controllers) {
        this.controllers = controllers;
    }
}
