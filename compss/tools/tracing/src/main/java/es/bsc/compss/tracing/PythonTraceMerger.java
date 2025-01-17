/*
 *  Copyright 2002-2022 Barcelona Supercomputing Center (www.bsc.es)
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package es.bsc.compss.tracing;

import es.bsc.compss.types.tracing.ApplicationComposition;
import es.bsc.compss.types.tracing.EventsDefinition;
import es.bsc.compss.types.tracing.InfrastructureElement;
import es.bsc.compss.types.tracing.SynchEvent;
import es.bsc.compss.types.tracing.Thread;
import es.bsc.compss.types.tracing.ThreadIdentifier;
import es.bsc.compss.types.tracing.Trace;
import es.bsc.compss.types.tracing.paraver.PRVThreadIdentifier;
import es.bsc.compss.types.tracing.paraver.PRVTrace;
import es.bsc.compss.util.tracing.ThreadTranslator;
import es.bsc.compss.util.tracing.TraceMerger;
import es.bsc.compss.util.tracing.TraceTransformation;
import es.bsc.compss.util.tracing.transformations.ThreadTranslation;
import es.bsc.compss.util.tracing.transformations.TimeOffset;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


public class PythonTraceMerger extends TraceMerger {

    private final Trace mergeOnTrace;


    /**
     * Initializes class attributes for python trace merging.
     *
     * @param outputTrace Working directory
     * @param workerTraces set of traces with python events to be merged into the main
     * @throws java.io.FileNotFoundException Master PRVTrace or workers traces not found
     * @throws IOException Error merging files
     */
    public PythonTraceMerger(Trace[] workerTraces, Trace outputTrace) throws FileNotFoundException, IOException {
        super(workerTraces);
        LOGGER.debug("Trace's merger initialization successful");
        this.mergeOnTrace = outputTrace;
    }

    /**
     * Merges the python traces with the master.
     */
    public void merge() throws Exception {
        Trace masterTrace = this.mergeOnTrace;
        String dir;
        dir = masterTrace.getDirectory();
        String tmpName;
        tmpName = masterTrace.getName() + ".tmp";
        String date;
        date = masterTrace.getDate();
        String duration;
        duration = masterTrace.getDuration();
        ApplicationComposition threads;
        threads = masterTrace.getThreadOrganization();
        ArrayList<InfrastructureElement> infrastructure;
        infrastructure = masterTrace.getInfrastructure();

        EventsDefinition events;
        events = masterTrace.getEventsDefinition();
        events.defineNewHWCounters(getAllHWCounters());

        LOGGER.debug("Parsing master sync events");
        Map<Integer, List<SynchEvent>> masterSyncEvents = masterTrace.getSyncEvents(-1);
        LOGGER.debug("Merging task traces into master which contains " + masterSyncEvents.size() + " lines.");
        TraceTransformation[][] modifications = new TraceTransformation[this.inputTraces.length + 1][];

        for (int idx = 0; idx < this.inputTraces.length; idx++) {
            Trace workerTrace = this.inputTraces[idx];
            if (workerTrace != masterTrace) {
                Integer workerIdx;
                LOGGER.debug("Merging worker " + workerTrace);
                String workerFileName = workerTrace.getName();
                try {
                    String wID = "";
                    for (int i = 0; workerFileName.charAt(i) != '_'; ++i) {
                        wID += workerFileName.charAt(i);
                    }
                    workerIdx = Integer.parseInt(wID);
                } catch (Exception e) {
                    // If workerId cannot be retrieved it is the master
                    workerIdx = 0;
                }

                Integer workerID = workerIdx + 1;
                Map<Integer, List<SynchEvent>> workerSyncEvents = workerTrace.getSyncEvents(workerID);

                SynchEvent synchOffset = computeOffset(masterSyncEvents.get(workerID), workerSyncEvents.get(workerID));
                long timeOffset = synchOffset.getTimestamp();
                modifications[idx] = new TraceTransformation[2];
                modifications[idx][0] = new TimeOffset(timeOffset);

                PythonMergeTranslation translation = new PythonMergeTranslation(threads, workerIdx);
                modifications[idx][1] = new ThreadTranslation(translation);
            } else {
                modifications[idx] = new TraceTransformation[0];
            }
        }

        // Maintain trace structure from master Trace
        PRVTrace tmpTrace = PRVTrace.generateNew(dir, tmpName, date, duration, infrastructure, threads, events);
        mergeEvents(this.inputTraces, modifications, tmpTrace);
        tmpTrace.renameAs(masterTrace.getDirectory(), masterTrace.getName());
        LOGGER.debug("Merging finished.");
    }

    private SynchEvent computeOffset(List<SynchEvent> referenceSyncEvents, List<SynchEvent> localSyncEvents)
        throws Exception {
        if (referenceSyncEvents.size() < 3) {
            throw new Exception("ERROR: Malformed master trace. Master sync events not found");
        }
        if (localSyncEvents.size() < 3) {
            throw new Exception("ERROR: Malformed worker trace. Worker sync events not found");
        }

        SynchEvent refStart = referenceSyncEvents.get(0); // numero de threads del master al arrancar el runtime
        // LineInfo refEnd = masterSyncEvents.get(1);
        SynchEvent refSync = referenceSyncEvents.get(2);
        // LineInfo localStart = workerSyncEvents.get(0);
        // LineInfo localEnd = workerSyncEvents.get(1);
        SynchEvent localSync = localSyncEvents.get(2);

        // Take the sync event emitted by the reference (master) and local(worker) and compare their value (timestamp)
        // The worker events real start is the difference between reference and the local
        // minus the timestamp difference.
        Long syncDifference = Math.abs((refSync.getValue() / 1000) - localSync.getValue());
        Long realStart = Math.abs(refSync.getTimestamp() - localSync.getTimestamp()) - syncDifference;

        return new SynchEvent(refStart.getResourceId(), "", realStart, refStart.getValue());
    }


    private static class PythonMergeTranslation implements ThreadTranslator {

        private final ApplicationComposition threads;
        private final ApplicationComposition task;


        public PythonMergeTranslation(ApplicationComposition threads, int workerId) {
            this.threads = threads;
            ApplicationComposition app = (ApplicationComposition) threads.getSubComponents().get(0);
            task = (ApplicationComposition) app.getSubComponents().get(workerId);
        }

        @Override
        public ThreadIdentifier getNewThreadId(ThreadIdentifier threadId) {
            PRVThreadIdentifier prvId = (PRVThreadIdentifier) threadId;
            String oldId = prvId.getApp();
            int oldApp = Integer.parseInt(oldId);
            int newThreadId = task.getNumberOfDirectSubcomponents() - oldApp;

            Thread thread = (Thread) task.getSubComponents().get(newThreadId);
            return thread.getIdentifier();
        }

        @Override
        public ApplicationComposition getNewThreadOrganization() {
            return threads;
        }

    }


    /**
     * Main method to start the merging of python traces into a main trace.
     *
     * @param args Tracer arguments: 0 - tracing folder 1 - main trace name 2> - Python traces to merge
     * @throws Exception The merger raised an error
     */
    public static void main(String[] args) throws Exception {
        String workingDir = args[0];
        String traceName = args[1];

        final PRVTrace mainTrace = new PRVTrace(workingDir, traceName);
        if (!mainTrace.exists()) {
            throw new FileNotFoundException("Master trace " + traceName + " not found at directory " + workingDir);
        }
        int numPythonTraces = args.length - 2;
        if (numPythonTraces > 0) {
            PRVTrace[] traces = new PRVTrace[numPythonTraces + 1];
            traces[0] = mainTrace;
            for (int i = 2; i < args.length; i++) {
                File trace = new File(args[i]);
                traces[i - 1] = new PRVTrace(trace);
            }

            PythonTraceMerger merger = new PythonTraceMerger(traces, mainTrace);
            merger.merge();
        }
    }

}
