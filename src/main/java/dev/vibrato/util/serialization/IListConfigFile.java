package dev.vibrato.util.serialization;

public interface IListConfigFile {
    void onLoadStart();
    void addLine(String line);
}
