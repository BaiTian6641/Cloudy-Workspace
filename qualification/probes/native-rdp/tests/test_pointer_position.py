"""Compile/run the actual authored Java callback; no JNI, Android runtime or protocol pass."""

from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[4]
TEST = Path(__file__).with_name("PointerPositionCallbackTest.java")
ADAPTER = ROOT / "qualification/probes/native-rdp/app/src/main/java/com/freerdp/freerdpcore/services/LibFreeRDP.java"


class PointerPositionCallbackTest(unittest.TestCase):
    def test_actual_java_callback_retains_owner_and_contains_failures(self):
        # Empty Android type declarations satisfy signatures only. No Android API
        # or replacement LibFreeRDP implementation is executed by this test.
        javac = shutil.which("javac")
        java = shutil.which("java")
        self.assertIsNotNone(javac, "JDK javac required for callback boundary test")
        self.assertIsNotNone(java, "JDK java required for callback boundary test")
        with tempfile.TemporaryDirectory(prefix="cloudy-pointer-java-") as temporary:
            stage = Path(temporary)
            stubs = []
            for package, name in (("android.content", "Context"), ("android.graphics", "Bitmap")):
                path = stage / (name + ".java")
                path.write_text(f"package {package}; public class {name} {{}}\n")
                stubs.append(str(path))
            subprocess.run([javac, "-Xlint:all", "-Werror", "-d", str(stage),
                            *stubs, str(ADAPTER), str(TEST)], check=True, timeout=30)
            subprocess.run([java, "-cp", str(stage), "PointerPositionCallbackTest"],
                           check=True, timeout=15)


if __name__ == "__main__":
    unittest.main()
