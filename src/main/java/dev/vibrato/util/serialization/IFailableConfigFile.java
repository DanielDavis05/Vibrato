package dev.vibrato.util.serialization;

public interface IFailableConfigFile {
    void onFailLoad();
    boolean failedToLoad();
}
