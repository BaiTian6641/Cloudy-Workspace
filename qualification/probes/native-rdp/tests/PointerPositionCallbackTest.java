import com.freerdp.freerdpcore.services.LibFreeRDP;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;

/** Exercises the actual authored Java callback without loading JNI or simulating a server. */
public final class PointerPositionCallbackTest {
    private static void check(boolean value) {
        if (!value) throw new AssertionError("pointer-position callback boundary");
    }

    public static void main(String[] arguments) throws Exception {
        Method callback = LibFreeRDP.class.getDeclaredMethod("OnPointerSetPosition", long.class, int.class, int.class);
        callback.setAccessible(true);
        int[] delivered = {0};
        LibFreeRDP.Sink receiver = (LibFreeRDP.Sink) Proxy.newProxyInstance(
            LibFreeRDP.Sink.class.getClassLoader(), new Class<?>[] {LibFreeRDP.Sink.class},
            (proxy, method, values) -> {
                check(method.getName().equals("pointerPosition"));
                check(Arrays.equals(values, new Object[] {17L, 100, 200}));
                delivered[0]++;
                return null;
            });
        LibFreeRDP.setSink(receiver);
        callback.invoke(null, 17L, 100, 200);
        check(delivered[0] == 1);
        LibFreeRDP.setSink(null);
        callback.invoke(null, 17L, 100, 200);
        check(delivered[0] == 1);

        int[] failures = {0};
        int[] newerCalls = {0};
        LibFreeRDP.Sink newer = (LibFreeRDP.Sink) Proxy.newProxyInstance(
            LibFreeRDP.Sink.class.getClassLoader(), new Class<?>[] {LibFreeRDP.Sink.class},
            (proxy, method, values) -> { newerCalls[0]++; return null; });
        LibFreeRDP.Sink failing = (LibFreeRDP.Sink) Proxy.newProxyInstance(
            LibFreeRDP.Sink.class.getClassLoader(), new Class<?>[] {LibFreeRDP.Sink.class},
            (proxy, method, values) -> {
                if (method.getName().equals("pointerPosition")) {
                    LibFreeRDP.setSink(newer);
                    throw new IllegalArgumentException("private message must not cross failure boundary");
                }
                check(method.getName().equals("callbackFailure"));
                check(values[0].equals(19L));
                check(values[1].equals("pointer-position"));
                check(values[2].equals("IllegalArgumentException"));
                check(Arrays.equals((int[]) values[3], new int[] {300, 400}));
                failures[0]++;
                // Even a failed diagnostic callback must not escape back into JNI.
                throw new IllegalStateException("failed owner callback");
            });
        LibFreeRDP.setSink(failing);
        callback.invoke(null, 19L, 300, 400);
        check(failures[0] == 1 && newerCalls[0] == 0);
        LibFreeRDP.setSink(null);
        System.out.println("actual Java pointer-position callback: delivery, absent sink, failure containment and retained owner passed");
    }
}
