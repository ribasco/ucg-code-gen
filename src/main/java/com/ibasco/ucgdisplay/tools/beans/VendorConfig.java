package com.ibasco.ucgdisplay.tools.beans;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class VendorConfig {

    private static final Logger log = LoggerFactory.getLogger(VendorConfig.class);

    private transient Vendor vendor;

    private String cadName;

    private String cadNameShort;

    private List<Comm> supportedInterfaces = new ArrayList<>();

    public Vendor getVendor() {
        return vendor;
    }

    public void setVendor(Vendor vendor) {
        this.vendor = vendor;
    }

    public List<Comm> getSupportedInterfaces() {
        return supportedInterfaces;
    }

    public void setSupportedInterfaces(List<Comm> supportedInterfaces) {
        this.supportedInterfaces = supportedInterfaces;
    }

    public String getCadName() {
        return cadName;
    }

    public void setCadName(String cadName) {
        this.cadName = cadName;
    }

    public String getCadNameShort() {
        return cadNameShort;
    }

    public void setCadNameShort(String cadNameShort) {
        this.cadNameShort = cadNameShort;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VendorConfig that = (VendorConfig) o;
        return vendor.equals(that.vendor) &&
                cadName.equals(that.cadName) &&
                Objects.equals(cadNameShort, that.cadNameShort);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vendor, cadName, cadNameShort);
    }

    @Override
    public String toString() {
        return "config: " +
                "vendor=" + vendor +
                ", cadName='" + cadName + '\'' +
                ", cadNameShort='" + cadNameShort + '\'';
    }
}
