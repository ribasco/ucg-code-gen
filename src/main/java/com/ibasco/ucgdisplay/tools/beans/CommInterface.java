package com.ibasco.ucgdisplay.tools.beans;

import com.ibasco.ucgdisplay.drivers.glcd.enums.GlcdCommProtocol;

public record CommInterface(int index, GlcdCommProtocol protocol, String name, String setPinFunction, String arduinoComProcedure,
                            String arduinoGpioProcedure, String pinsWithType, String pinsPlain,
                            String pinsMarkdown, String genericComProcedure) {

    @Override
    public String toString() {
        return "index=" + index +
                ", protocol=" + protocol +
                ", name='" + name + '\'' +
                ", setPinFunction='" + setPinFunction + '\'' +
                ", arduinoComProcedure='" + arduinoComProcedure + '\'' +
                ", arduinoGpioProcedure='" + arduinoGpioProcedure + '\'' +
                ", pinsWithType='" + pinsWithType + '\'' +
                ", pinsPlain='" + pinsPlain + '\'' +
                ", pinsMarkdown='" + pinsMarkdown + '\'' +
                ", genericComProcedure='" + genericComProcedure + '\'';
    }
}
