package hk.polyu.comp.fixja.util;

import ch.qos.logback.classic.Level;
import hk.polyu.comp.fixja.fixaction.FixAction;
import hk.polyu.comp.fixja.fixaction.Snippet;
import hk.polyu.comp.fixja.fixer.config.FixerOutput;
import hk.polyu.comp.fixja.fixer.log.LoggingService;
import hk.polyu.comp.fixja.monitor.LineLocation;
import hk.polyu.comp.fixja.monitor.snapshot.StateSnapshot;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static hk.polyu.comp.fixja.fixer.config.FixerOutput.LogFile.COMPILATION_ERRORS;
import static hk.polyu.comp.fixja.fixer.log.LoggingService.addFileLogger;
import static hk.polyu.comp.fixja.fixer.log.LoggingService.shouldLogDebug;

/**
 * Created by liushanchen on 16/10/6.
 */
public class LogUtil {
    private static long startTime;

    /**
     * Output the items within the collection to a specific log
     *
     * @param objectCollection the collection to be logged
     * @throws Exception
     */
    public static void logCollectionForDebug(Collection objectCollection, FixerOutput.LogFile logFileName, boolean removeLogger) throws Exception {
        if(shouldLogDebug()) {
            addFileLogger(logFileName, Level.INFO);
            LoggingService.infoFileOnly(logFileName.name() + " size:" + objectCollection.size(), logFileName);
            objectCollection.stream().forEach(f ->
                    LoggingService.infoFileOnly(f.toString(), logFileName));
            if (removeLogger)
                LoggingService.removeExtraLogger(logFileName);
        }
    }

    /**
     * Output the items within the map to a specific log
     *
     * @param objectCollection the map to be logged
     * @throws Exception
     */
    public static void logMapForDebug(Map objectCollection, FixerOutput.LogFile logFileName) throws Exception {
        addFileLogger(logFileName, Level.INFO);
        LoggingService.infoFileOnly(logFileName.name() + " size:" + objectCollection.size(), logFileName);
        objectCollection.forEach((k, v) ->
                LoggingService.infoFileOnly(k.toString() + "\n" + v.toString(), logFileName));
        LoggingService.removeExtraLogger(logFileName);
    }

    /**
     * Output items within a map
     *
     * @param objectCollection the map to be logged
     */
    public static void logMapForDebug(Map objectCollection) {
        if(shouldLogDebug()) {
            objectCollection.forEach((k, v) ->
                    LoggingService.debug(k.toString() + "\n" + v.toString()));
        }
    }

    public static void logCompilationErrorForDebug(DiagnosticCollector<JavaFileObject> diagnosticCollector) {
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnosticCollector.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                StringBuilder sb = new StringBuilder("Source: ")
                        .append(diagnostic.getSource().getName())
                        .append(" :: ").append(diagnostic.getLineNumber())
                        .append("\nMSG: ").append(diagnostic.getMessage(null));
                LoggingService.debugFileOnly(sb.toString(), COMPILATION_ERRORS);
            }
        }
    }

    public static void logLocationForDebug(Set<LineLocation> locations) throws Exception {
        if(shouldLogDebug()){
            FixerOutput.LogFile locationLog = FixerOutput.LogFile.EXE_LOCATIONS;

            FileUtil.ensureEmptyFile(locationLog.getLogFilePath());
            try(PrintWriter writer = new PrintWriter(locationLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())) {
                for (LineLocation l : locations) {
                    writer.println(l.toString());
                }
                writer.println("Valid locations.size:" + locations.size());
                writer.close();
            }
        }
        LoggingService.infoAll("Valid locations.size::" + locations.size());
    }

    public static void logEvaluatedSnapshotsForDebug(List<StateSnapshot> snapshots) throws Exception {
        if(shouldLogDebug()){
            FixerOutput.LogFile snapshotLog = FixerOutput.LogFile.SUSPICIOUS_STATE_SNAPSHOT;

            FileUtil.ensureEmptyFile(snapshotLog.getLogFilePath());
            try(PrintWriter writer = new PrintWriter(snapshotLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())) {
                for (StateSnapshot snapshot : snapshots) {
                    writer.println(snapshot + " --> " + snapshot.getSuspiciousness());
                }
                writer.println("Valid snapshots.size:" + snapshots.size());
                writer.close();
            }
        }
        LoggingService.infoAll("Valid snapshots.size::" + snapshots.size());
    }

    public static void logSnippetsForDebug(Map<StateSnapshot, Set<Snippet>> snippetMap) throws Exception {
        int size = 0;
        for(StateSnapshot snapshot: snippetMap.keySet()) {
            size+=snippetMap.get(snapshot).size();
        }
        if(shouldLogDebug()){
//            addFileLogger(FixerOutput.LogFile.SNIPPETS, Level.DEBUG);
            FixerOutput.LogFile snippetLog = FixerOutput.LogFile.SNIPPETS;

            FileUtil.ensureEmptyFile(snippetLog.getLogFilePath());
            try(PrintWriter writer = new PrintWriter(snippetLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())){
                for(StateSnapshot snapshot: snippetMap.keySet()){
                    writer.println("<<<<");
                    writer.println(snapshot.toString());
                    writer.println(">>>>");
                    Set<Snippet> snippets = snippetMap.get(snapshot);
                    for(Snippet snippet: snippets){
                        writer.print(snippet.toString());
                        writer.print("-----");
                    }
                }
//                for (Map.Entry<StateSnapshot, Set<Snippet>> snippetEntry : snippetMap.entrySet()) {
//                    writer.println(snippetEntry.getKey() + " --> " + snippetEntry.getValue());
//                    size += snippetEntry.getValue().size();
//                }
                writer.println("Valid snippets:" + size);
                writer.close();

            }
        }
        LoggingService.infoAll("Valid snippets::" + size);

    }

    public static void logFixActionsForDebug(Map<LineLocation, Set<FixAction>> fixActionMap) throws Exception {
        int size = 0;
        for (Map.Entry<LineLocation, Set<FixAction>> fixActionEntry : fixActionMap.entrySet()) {
            size += fixActionEntry.getValue().size();
        }
        if(shouldLogDebug()){
//            addFileLogger(FixerOutput.LogFile.ALL_FIX_ACTION, Level.DEBUG);
            FixerOutput.LogFile fixActionLog = FixerOutput.LogFile.ALL_FIX_ACTION;

            FileUtil.ensureEmptyFile(fixActionLog.getLogFilePath());
            try(PrintWriter writer = new PrintWriter(fixActionLog.getLogFilePath().toString(), StandardCharsets.UTF_8.toString())){
                for (Map.Entry<LineLocation, Set<FixAction>> fixActionEntry : fixActionMap.entrySet()) {
                    writer.println(fixActionEntry.getKey().toString() + "; Fix size:: " + fixActionEntry.getValue().size() + "\n");
                }
                writer.println("==================================\n");
                for (Map.Entry<LineLocation, Set<FixAction>> fixActionEntry : fixActionMap.entrySet()) {
                    for (FixAction fixAction : fixActionEntry.getValue()) {
                        writer.println(fixAction.toString());
                    }
                }
                writer.println("Generated fixactions:" + size);
                writer.close();

            }
        }
        LoggingService.infoAll("Generated fixactions::" + size);

    }

    public static void setStartTime(long startTime) {
        LogUtil.startTime = startTime;
    }

    public static void logSessionCosting(String msg) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        long duration = System.currentTimeMillis() - startTime;

        duration = duration / 1000;
        LoggingService.infoAll(new StringBuilder(msg)
                .append("   CostingTime: ")
                .append(duration / (60 * 60)).append(":")
                .append(duration / (60) % 60).append(":")
                .append(duration % 60).append(";    ")
                .append("UsedMemory: ").append(usedMemory / (1024 * 1024)).append("MB")
                .toString());
    }

    public static void logSessionCostingMemory() {
        Runtime runtime = Runtime.getRuntime();
        long usedMemory = runtime.totalMemory() - runtime.freeMemory();
        LoggingService.info(new StringBuilder("UsedMemory: ")
                .append(usedMemory / (1024 * 1024)).append("MB")
                .toString());
    }
}
