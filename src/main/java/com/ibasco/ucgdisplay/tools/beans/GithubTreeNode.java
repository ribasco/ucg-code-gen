package com.ibasco.ucgdisplay.tools.beans;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class GithubTreeNode {

    @SerializedName("path")
    @Expose
    private String path;

    @SerializedName("mode")
    @Expose
    private String mode;

    @SerializedName("type")
    @Expose
    private String type;

    @SerializedName("sha")
    @Expose
    private String sha;

    @SerializedName("size")
    @Expose
    private Integer size;

    @SerializedName("url")
    @Expose
    private String url;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSha() {
        return sha;
    }

    public void setSha(String sha) {
        this.sha = sha;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

}