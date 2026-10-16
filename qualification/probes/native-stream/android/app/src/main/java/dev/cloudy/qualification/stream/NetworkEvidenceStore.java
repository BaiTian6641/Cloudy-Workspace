package dev.cloudy.qualification.stream;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Worker-only shared admission. Reconciliation acknowledges exact old bytes, never rewrites them. */
final class NetworkEvidenceStore {
    private static final int MAX_BYTES=32768;
    private NetworkEvidenceStore() {}
    private static final class Ref {
        final String path,hash,process;
        final boolean closed,error;
        Ref(String path,String hash,String process,boolean closed,boolean error) {
            this.path=path;this.hash=hash;this.process=process;this.closed=closed;this.error=error;
        }
    }
    static void check(File noBackup) throws Exception {
        Map<String,String> receipts=receipts(noBackup);
        for (Ref ref:refs(noBackup)) if (!NetworkJournalPolicy.acceptable(true,ref.closed,ref.error,ref.hash.equals(receipts.get(ref.path)))) throw new IOException("network-reconciliation-required");
    }
    static JSONArray reconcile(File noBackup,String currentProcess) throws Exception {
        Map<String,String> receipts=receipts(noBackup); JSONArray acknowledged=new JSONArray();
        for (Ref ref:refs(noBackup)) {
            if (NetworkJournalPolicy.acceptable(true,ref.closed,ref.error,ref.hash.equals(receipts.get(ref.path)))) continue;
            if (ref.process.isEmpty() || currentProcess.equals(ref.process)) throw new IOException("fresh-process-or-manual-repair-required");
            acknowledged.put(new JSONObject().put("path",ref.path).put("sha256",ref.hash));
        }
        return acknowledged;
    }
    private static Map<String,String> receipts(File noBackup) throws Exception {
        Map<String,String> result=new HashMap<>(); File dir=new File(noBackup,"network-reconciliation");
        if (!dir.exists()) return result;
        File[] files=dir.listFiles(); if (files==null || files.length>16) throw new IOException();
        for (File file:files) {
            if (!file.getName().matches("[a-f0-9-]{36}\\.json")) throw new IOException();
            JSONObject json=read(file);
            if (!"RECONCILE".equals(json.getString("operation"))) throw new IOException();
            if (!"completed".equals(json.getString("status"))) continue;
            if (!"UNKNOWN".equals(json.getString("hostState")) || json.getBoolean("networkReplayed") || !json.getBoolean("localProcessInterruptionAcknowledged")) throw new IOException();
            JSONArray rows=json.getJSONArray("reconciledRecords"); if (rows.length()>192) throw new IOException();
            for (int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i); String path=row.getString("path"),hash=row.getString("sha256");
                if (!path.matches("(?:pairing|playback|catalog)-journal/[a-f0-9-]{36}\\.json(?:\\.pending)?") || !hash.matches("[a-f0-9]{64}")) throw new IOException();
                result.put(path,hash);
            }
        }
        return result;
    }
    private static List<Ref> refs(File noBackup) throws Exception {
        List<Ref> result=new ArrayList<>();
        for (String name:new String[]{"pairing-journal","playback-journal","catalog-journal"}) {
            File dir=new File(noBackup,name); if (!dir.exists()) continue;
            File[] files=dir.listFiles(); if (files==null || files.length>64) throw new IOException();
            for (File file:files) {
                // Partial AtomicFile artifacts and malformed/unreadable evidence require manual
                // repair; the UI cannot invent enough state to acknowledge their contents.
                if (!file.getName().matches("[a-f0-9-]{36}\\.json(?:\\.pending)?")) throw new IOException();
                byte[] bytes=bytes(file); JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                Object closed=json.get("closed"); if (!(closed instanceof Boolean)) throw new IOException();
                Object schema=json.get("schemaVersion");
                if (!(schema instanceof Integer) || (Integer)schema!=1) throw new IOException();
                String status=json.getString("status");
                if (!java.util.Set.of("reservation","in-progress","authorization-required","cancelled","failed","completed").contains(status)
                        || ((Boolean)closed && !java.util.Set.of("cancelled","failed","completed").contains(status))) throw new IOException();
                String process="";
                if (json.has("processRef")) {
                    Object value=json.get("processRef");
                    if (!(value instanceof String) || !((String)value).matches("[a-f0-9-]{36}")) throw new IOException();
                    process=(String)value;
                }
                String operation=json.getString("operation");
                if (!(name.equals("pairing-journal") ? operation.equals("PAIR") : name.equals("playback-journal") ? operation.equals("PLAY") : operation.equals("CATALOG"))) throw new IOException();
                String hash=hex(MessageDigest.getInstance("SHA-256").digest(bytes));
                result.add(new Ref(name+"/"+file.getName(),hash,process,(Boolean)closed,"EVIDENCE_UNAVAILABLE".equals(json.optString("failureCode"))));
            }
        }
        return result;
    }
    private static JSONObject read(File file) throws Exception { return new JSONObject(new String(bytes(file),StandardCharsets.UTF_8)); }
    private static byte[] bytes(File file) throws IOException {
        if (!file.isFile() || file.length()>MAX_BYTES) throw new IOException();
        try (FileInputStream input=new FileInputStream(file)) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1); if (bytes.length>MAX_BYTES) throw new IOException(); return bytes;
        }
    }
    private static String hex(byte[] bytes) { StringBuilder text=new StringBuilder(); for (byte b:bytes) text.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return text.toString(); }
}
