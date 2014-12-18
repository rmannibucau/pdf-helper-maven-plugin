package com.github.rmannibucau.maven.plugin;

public class Pdf {
    private String title;
    private String description;
    private String location;
    private int titleSize = 50;
    private String titleFont = "Helvetica-Bold";
    private int descriptionSize = 15;
    private String descriptionFont = "Helvetica";

    public void setTitle(final String title) {
        this.title = title;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public void setLocation(final String location) {
        this.location = location;
    }

    public void setTitleFont(final String titleFont) {
        this.titleFont = titleFont;
    }

    public void setDescriptionFont(final String descriptionFont) {
        this.descriptionFont = descriptionFont;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getLocation() {
        return location;
    }

    public String getTitleFont() {
        return titleFont;
    }

    public String getDescriptionFont() {
        return descriptionFont;
    }

    public int getTitleSize() {
        return titleSize;
    }

    public void setTitleSize(final int titleSize) {
        this.titleSize = titleSize;
    }

    public int getDescriptionSize() {
        return descriptionSize;
    }

    public void setDescriptionSize(final int descriptionSize) {
        this.descriptionSize = descriptionSize;
    }
}
