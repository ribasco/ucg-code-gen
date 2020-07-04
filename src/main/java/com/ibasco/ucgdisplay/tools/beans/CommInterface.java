package com.ibasco.ucgdisplay.tools.beans;

public record   CommInterface(int index, String name, String setPinFunction, String arduinoComProcedure,
                            String arduinoGpioProcedure, String pinsWithType, String pinsPlain,
                            String pinsMarkdown, String genericComProcedure) {
    @Override
    public String toString() {
        return "name='" + name + '\'' +
                ", setPinFunction='" + setPinFunction + '\'' +
                ", arduinoComProcedure='" + arduinoComProcedure + '\'' +
                ", arduinoGpioProcedure='" + arduinoGpioProcedure + '\'' +
                ", pinsWithType='" + pinsWithType + '\'' +
                ", pinsPlain='" + pinsPlain + '\'' +
                ", pinsMarkdown='" + pinsMarkdown + '\'' +
                ", genericComProcedure='" + genericComProcedure + '\'';
    }
}
