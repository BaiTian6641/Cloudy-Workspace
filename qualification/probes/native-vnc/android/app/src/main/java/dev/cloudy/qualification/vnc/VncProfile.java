package dev.cloudy.qualification.vnc;

import android.content.Context;
import android.net.InetAddresses;
import org.json.JSONObject;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.Set;

final class VncProfile {
    final String id, address;
    final int port, duration;
    final byte[] pin;
    private VncProfile(String id, String address, int port, int duration, byte[] pin) {
        this.id = id; this.address = address; this.port = port; this.duration = duration; this.pin = pin;
    }
    private static int exactInt(JSONObject json,String name) throws Exception {
        Object value=json.get(name);
        if (!(value instanceof Integer)) throw new IllegalArgumentException();
        return (Integer)value;
    }
    static VncProfile read(Context context) throws Exception {
        File file = new File(context.getFilesDir(), "vnc-config.json");
        if (!file.isFile() || file.length() > 16384) throw new IllegalArgumentException();
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (bytes.length > 16384) throw new IllegalArgumentException();
        JSONObject json = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
        Set<String> allowed = Set.of("schema", "profileId", "numericAddress", "port", "certificateSha256", "durationSeconds");
        for (Iterator<String> keys = json.keys(); keys.hasNext();) if (!allowed.contains(keys.next())) throw new IllegalArgumentException();
        if (json.length() != allowed.size() || exactInt(json,"schema") != 1) throw new IllegalArgumentException();
        String id = json.getString("profileId"), address = json.getString("numericAddress");
        String hex = json.getString("certificateSha256");
        int port = exactInt(json,"port"), duration = exactInt(json,"durationSeconds");
        if (!id.matches("[A-Za-z0-9_-]{1,64}") || address.length() > 64 || address.contains("%")
                || !hex.matches("[0-9a-fA-F]{64}") || port < 1 || port > 65535 || duration < 10 || duration > 600) throw new IllegalArgumentException();
        InetAddresses.parseNumericAddress(address);
        byte[] pin = new byte[32];
        for (int i = 0; i < pin.length; i++) pin[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        return new VncProfile(id, address, port, duration, pin);
    }
}
