package javax.microedition.rms;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import com.emulator.j2me.core.EmulatorEngine;

public class RecordStore {
    private final String name;
    private final File dir;
    private boolean open;

    private RecordStore(String name, File dir) {
        this.name = name;
        this.dir = dir;
        this.open = true;
    }

    private static File getRmsBaseDir() {
        File baseDir = new File(EmulatorEngine.getRmsDir());
        if (!baseDir.exists()) {
            baseDir.mkdirs();
        }
        return baseDir;
    }

    public static RecordStore openRecordStore(String recordStoreName, boolean createIfNecessary) throws RecordStoreException {
        if (recordStoreName == null || recordStoreName.length() == 0 || recordStoreName.length() > 32) {
            throw new RecordStoreException("Invalid record store name");
        }
        File base = getRmsBaseDir();
        File storeDir = new File(base, recordStoreName);
        if (!storeDir.exists()) {
            if (createIfNecessary) {
                storeDir.mkdirs();
            } else {
                throw new RecordStoreNotFoundException("Record store not found: " + recordStoreName);
            }
        }
        return new RecordStore(recordStoreName, storeDir);
    }

    public void closeRecordStore() throws RecordStoreException {
        if (!open) {
            throw new RecordStoreNotOpenException("Record store already closed");
        }
        open = false;
    }

    public static void deleteRecordStore(String recordStoreName) throws RecordStoreException {
        File base = getRmsBaseDir();
        File storeDir = new File(base, recordStoreName);
        if (!storeDir.exists()) {
            throw new RecordStoreNotFoundException("Record store not found: " + recordStoreName);
        }
        deleteDir(storeDir);
    }

    private static void deleteDir(File file) {
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File child : files) {
                    deleteDir(child);
                }
            }
        }
        file.delete();
    }

    public static String[] listRecordStores() {
        File base = getRmsBaseDir();
        String[] list = base.list();
        if (list == null || list.length == 0) return null;
        return list;
    }

    private void checkOpen() throws RecordStoreNotOpenException {
        if (!open) {
            throw new RecordStoreNotOpenException("Record store is not open");
        }
    }

    public int getNumRecords() throws RecordStoreNotOpenException {
        checkOpen();
        int count = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith("rec_") && f.getName().endsWith(".bin")) {
                    count++;
                }
            }
        }
        return count;
    }

    public int getNextRecordID() throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        int maxId = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.getName().startsWith("rec_") && f.getName().endsWith(".bin")) {
                    try {
                        String idStr = f.getName().substring(4, f.getName().length() - 4);
                        int id = Integer.parseInt(idStr);
                        if (id > maxId) {
                            maxId = id;
                        }
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return maxId + 1;
    }

    public int addRecord(byte[] data, int offset, int numBytes) throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        int nextId = getNextRecordID();
        setRecord(nextId, data, offset, numBytes);
        return nextId;
    }

    public void setRecord(int recordId, byte[] data, int offset, int numBytes) throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        File recordFile = new File(dir, "rec_" + recordId + ".bin");
        try (FileOutputStream fos = new FileOutputStream(recordFile)) {
            if (data != null && numBytes > 0) {
                fos.write(data, offset, numBytes);
            }
        } catch (IOException e) {
            throw new RecordStoreException("Failed to write record: " + e.getMessage());
        }
    }

    public byte[] getRecord(int recordId) throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        File recordFile = new File(dir, "rec_" + recordId + ".bin");
        if (!recordFile.exists()) {
            throw new RecordStoreNotFoundException("Record ID " + recordId + " does not exist");
        }
        int len = (int) recordFile.length();
        byte[] buffer = new byte[len];
        try (FileInputStream fis = new FileInputStream(recordFile)) {
            int read = fis.read(buffer);
            if (read < 0) return new byte[0];
            return buffer;
        } catch (IOException e) {
            throw new RecordStoreException("Failed to read record: " + e.getMessage());
        }
    }

    public int getRecord(int recordId, byte[] buffer, int offset) throws RecordStoreNotOpenException, RecordStoreException {
        byte[] data = getRecord(recordId);
        if (buffer.length - offset < data.length) {
            throw new RecordStoreException("Buffer too small");
        }
        System.arraycopy(data, 0, buffer, offset, data.length);
        return data.length;
    }

    public void deleteRecord(int recordId) throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        File recordFile = new File(dir, "rec_" + recordId + ".bin");
        if (!recordFile.exists()) {
            throw new RecordStoreException("Record ID " + recordId + " does not exist");
        }
        recordFile.delete();
    }

    public int getRecordSize(int recordId) throws RecordStoreNotOpenException, RecordStoreException {
        checkOpen();
        File recordFile = new File(dir, "rec_" + recordId + ".bin");
        if (!recordFile.exists()) {
            throw new RecordStoreException("Record ID " + recordId + " does not exist");
        }
        return (int) recordFile.length();
    }

    public int getSize() throws RecordStoreNotOpenException {
        checkOpen();
        int size = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                size += f.length();
            }
        }
        return size;
    }

    public int getSizeAvailable() throws RecordStoreNotOpenException {
        checkOpen();
        return 1024 * 1024 * 32; 
    }

    public long getLastModified() throws RecordStoreNotOpenException {
        checkOpen();
        return dir.lastModified();
    }
}
