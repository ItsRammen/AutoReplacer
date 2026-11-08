package dev.rentaldisplay.probe;

import java.io.File;
import java.io.IOException;

final class ExportFiles {
    static File resolve(File directory,String name) throws IOException {
        if (name == null || !name.matches("rental-display-log-[a-zA-Z0-9-]{1,80}\\.txt"))
            throw new IOException("Invalid log export name");
        File file = new File(directory,name).getCanonicalFile();
        if (!directory.getCanonicalFile().equals(file.getParentFile())) throw new IOException("Export outside log directory");
        return file;
    }
}
