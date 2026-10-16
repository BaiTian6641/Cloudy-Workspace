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
    static void check(File noBackup,String currentProcess) throws Exception {
        try {
            Map<String,String> receipts=receipts(noBackup);
            for (Ref ref:refs(noBackup)) {
                EvidenceReason reason=EvidenceReason.record(ref.closed,ref.error,ref.hash.equals(receipts.get(ref.path)),ref.process,currentProcess);
                if (reason!=EvidenceReason.NONE) throw new EvidenceAdmissionException(reason.name());
            }
        } catch (EvidenceAdmissionException known) { throw known; }
        catch (IOException unavailable) { throw new EvidenceAdmissionException("EVIDENCE_UNAVAILABLE"); }
        catch (Exception malformed) { throw new EvidenceAdmissionException("EVIDENCE_MALFORMED"); }
    }
    static JSONArray reconcile(File noBackup,String currentProcess) throws Exception {
        try { return reconcileUnchecked(noBackup,currentProcess); }
        catch (EvidenceAdmissionException known) { throw known; }
        catch (IOException unavailable) { throw new EvidenceAdmissionException("EVIDENCE_UNAVAILABLE"); }
        catch (Exception malformed) { throw new EvidenceAdmissionException("EVIDENCE_MALFORMED"); }
    }
    private static JSONArray reconcileUnchecked(File noBackup,String currentProcess) throws Exception {
        Map<String,String> receipts=receipts(noBackup); JSONArray acknowledged=new JSONArray();
        for (Ref ref:refs(noBackup)) {
            if (NetworkJournalPolicy.acceptable(true,ref.closed,ref.error,ref.hash.equals(receipts.get(ref.path)))) continue;
            if (ref.process.isEmpty() || currentProcess.equals(ref.process)) throw new EvidenceAdmissionException(ref.process.isEmpty() ? "EVIDENCE_MALFORMED" : ref.error ? "EVIDENCE_RESTART_REQUIRED" : "EVIDENCE_UNFINISHED_CURRENT");
            acknowledged.put(new JSONObject().put("path",ref.path).put("sha256",ref.hash));
        }
        return acknowledged;
    }
    private static Map<String,String> receipts(File noBackup) throws Exception {
        Map<String,String> result=new HashMap<>(); File dir=new File(noBackup,"network-reconciliation");
        if (!dir.exists()) return result;
        File[] files=dir.listFiles(); if (files==null) throw new EvidenceAdmissionException("EVIDENCE_UNAVAILABLE");
        if (files.length>16) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
        for (File file:files) {
            if (!file.getName().matches("[a-f0-9-]{36}\\.json")) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
            JSONObject json=read(file);
            if (!"RECONCILE".equals(json.getString("operation"))) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
            if (!"completed".equals(json.getString("status"))) continue;
            if (!"UNKNOWN".equals(json.getString("hostState")) || json.getBoolean("networkReplayed") || !json.getBoolean("localProcessInterruptionAcknowledged")) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
            JSONArray rows=json.getJSONArray("reconciledRecords"); if (rows.length()>192) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
            for (int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i); String path=row.getString("path"),hash=row.getString("sha256");
                if (!path.matches("(?:pairing|playback|catalog)-journal/[a-f0-9-]{36}\\.json(?:\\.pending)?") || !hash.matches("[a-f0-9]{64}")) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
                result.put(path,hash);
            }
        }
        return result;
    }
    private static List<Ref> refs(File noBackup) throws Exception {
        List<Ref> result=new ArrayList<>();
        for (String name:new String[]{"pairing-journal","playback-journal","catalog-journal"}) {
            File dir=new File(noBackup,name); if (!dir.exists()) continue;
            File[] files=dir.listFiles(); if (files==null) throw new EvidenceAdmissionException("EVIDENCE_UNAVAILABLE");
            if (files.length>64) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
            for (File file:files) {
                // Partial AtomicFile artifacts and malformed/unreadable evidence require manual
                // repair; the UI cannot invent enough state to acknowledge their contents.
                if (!file.getName().matches("[a-f0-9-]{36}\\.json(?:\\.pending)?")) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
                byte[] bytes=bytes(file); JSONObject json=new JSONObject(new String(bytes,StandardCharsets.UTF_8));
                Object closed=json.get("closed"); if (!(closed instanceof Boolean)) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
                Object schema=json.get("schemaVersion");
                if (!(schema instanceof Integer) || (Integer)schema!=1) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
                String status=json.getString("status");
                if (!java.util.Set.of("reservation","in-progress","authorization-required","cancelled","failed","completed").contains(status)
                        || ((Boolean)closed && !java.util.Set.of("cancelled","failed","completed").contains(status))) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
                String process="";
                if (json.has("processRef")) {
                    Object value=json.get("processRef");
                    if (!(value instanceof String) || !((String)value).matches("[a-f0-9-]{36}")) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
                    process=(String)value;
                }
                String operation=json.getString("operation");
                if (!(name.equals("pairing-journal") ? operation.equals("PAIR") : name.equals("playback-journal") ? operation.equals("PLAY") : operation.equals("CATALOG"))) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
                String hash=hex(MessageDigest.getInstance("SHA-256").digest(bytes));
                result.add(new Ref(name+"/"+file.getName(),hash,process,(Boolean)closed,"EVIDENCE_UNAVAILABLE".equals(json.optString("failureCode"))));
            }
        }
        return result;
    }
    private static JSONObject read(File file) throws Exception { return new JSONObject(new String(bytes(file),StandardCharsets.UTF_8)); }
    private static byte[] bytes(File file) throws IOException {
        if (!file.isFile() || file.length()>MAX_BYTES) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED");
        try (FileInputStream input=new FileInputStream(file)) {
            byte[] bytes=input.readNBytes(MAX_BYTES+1); if (bytes.length>MAX_BYTES) throw new EvidenceAdmissionException("EVIDENCE_MALFORMED"); return bytes;
        }
    }
    private static String hex(byte[] bytes) { StringBuilder text=new StringBuilder(); for (byte b:bytes) text.append(String.format(java.util.Locale.ROOT,"%02x",b&255)); return text.toString(); }
}
