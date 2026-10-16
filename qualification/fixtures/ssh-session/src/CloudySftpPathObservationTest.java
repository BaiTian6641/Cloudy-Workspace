import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.apache.sshd.common.file.root.RootedFileSystem;
import org.apache.sshd.common.file.root.RootedFileSystemProvider;

/** Pure path/result mapping only; no listener, filesystem, credentials or server starts. */
public final class CloudySftpPathObservationTest {
    public static void main(String[] args) {
        Path root = Path.of("/synthetic-owned-root");
        RootedFileSystem fs = new RootedFileSystem(new RootedFileSystemProvider(), root, Map.of());
        RootedFileSystem foreign = new RootedFileSystem(new RootedFileSystemProvider(), Path.of("/foreign-root"), Map.of());
        expect("nested-directory", CloudySftpPathObservation.classify(root, fs.getPath("/nested")));
        expect("nested-file", CloudySftpPathObservation.classify(root, fs.getPath("/nested/nested.txt")));
        expect("nested-file", CloudySftpPathObservation.classify(root, fs.getPath("/nested/./nested.txt")));
        for (Path path : new Path[] {fs.getPath("nested/nested.txt"), fs.getPath("/nested.txt"),
                fs.getPath("/nested/nested.txt.extra"), fs.getPath("/nested/other.txt"),
                fs.getPath("/"), foreign.getPath("/nested/nested.txt"), Path.of("/nested/nested.txt")}) {
            expect("other", CloudySftpPathObservation.classify(root, path));
        }
        expect("completed", CloudySftpPathObservation.readCompletion(1, null));
        expect("empty", CloudySftpPathObservation.readCompletion(0, null));
        expect("eof", CloudySftpPathObservation.readCompletion(-1, null));
        expect("failed", CloudySftpPathObservation.readCompletion(12, new IOException("not emitted")));
        System.out.println("PATH_MAPPING_PASS_14");
    }
    private static void expect(String expected, String actual) {
        if (!expected.equals(actual)) throw new AssertionError("PATH_MAPPING_MISMATCH");
    }
}
