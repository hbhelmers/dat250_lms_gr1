package no.uib.dat250.LMS.storage;

public interface FileStorage {
    void put(String key, byte[] bytes, String mediaType);
    byte[] get(String key);
    void delete(String key);
}
