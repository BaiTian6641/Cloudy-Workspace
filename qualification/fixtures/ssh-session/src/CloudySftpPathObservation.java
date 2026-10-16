import java.nio.file.Path;
import org.apache.sshd.common.file.root.RootedFileSystem;
import org.apache.sshd.common.file.root.RootedPath;

/** Pure allowlist: never stringify an arbitrary host/remote path into evidence. */
final class CloudySftpPathObservation {
    private CloudySftpPathObservation() { }

    static String classify(Path trustedRoot, Path actual) {
        if (!(actual instanceof RootedPath) || !actual.isAbsolute()) return "other";
        RootedFileSystem fs = (RootedFileSystem) actual.getFileSystem();
        if (!fs.getRoot().equals(trustedRoot)) return "other";
        Path normalized = actual.normalize();
        if (normalized.equals(fs.getPath("/nested"))) return "nested-directory";
        if (normalized.equals(fs.getPath("/nested/nested.txt"))) return "nested-file";
        return "other";
    }

    static String readCompletion(int readLength, Throwable failure) {
        if (failure != null) return "failed";
        if (readLength < 0) return "eof";
        return readLength == 0 ? "empty" : "completed";
    }
}
