package com.ibasco.ucgdisplay.tools.beans;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

public class Manifest {
    private List<Controller> controllers = new ArrayList<>();

    private ZonedDateTime lastUpdated;

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
