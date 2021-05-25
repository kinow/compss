/*
 *  Copyright 2002-2021 Barcelona Supercomputing Center (www.bsc.es)
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
package es.bsc.compss.execution;

import es.bsc.compss.execution.types.ExecutorContext;
import es.bsc.compss.execution.types.InvocationResources;
import es.bsc.compss.execution.utils.JobQueue;
import es.bsc.compss.execution.utils.ResourceManager;
import es.bsc.compss.executor.Executor;
import es.bsc.compss.executor.InvocationRunner;
import es.bsc.compss.executor.external.ExecutionPlatformMirror;
import es.bsc.compss.invokers.Invoker;
import es.bsc.compss.log.Loggers;
import es.bsc.compss.types.execution.Execution;
import es.bsc.compss.types.execution.Invocation;
import es.bsc.compss.types.execution.InvocationContext;
import es.bsc.compss.types.execution.exceptions.UnsufficientAvailableResourcesException;
import es.bsc.compss.types.resources.ResourceDescription;
import es.bsc.compss.util.TraceEvent;
import es.bsc.compss.util.Tracer;
import es.bsc.compss.utils.execution.ThreadedProperties;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * The thread pool is an utility to manage a set of threads for job execution.
 */
public class ExecutionPlatform implements ExecutorContext {

    private static final Logger LOGGER = LogManager.getLogger(Loggers.WORKER_POOL);

    private final String platformName;
    private final InvocationContext context;
    private final ResourceManager rm;

    private boolean reuseResourcesOnBlockedInvocation = true;
    private final JobQueue queue;

    private boolean started = false;
    private int nextThreadId = 0;
    private final TreeSet<Thread> workerThreads;
    private final LinkedList<Thread> finishedWorkerThreads;

    private final Semaphore startSemaphore;
    private final Semaphore stopSemaphore;
    private final Map<Class<?>, ExecutionPlatformMirror<?>> mirrors;

    private Timer timer;
    private Set<Integer> toCancel;
    private Map<Integer, Invoker> executingJobs;


    /**
     * Constructs a new thread pool but not the threads inside it.
     *
     * @param platformName Platform name
     * @param context Invocation Context
     * @param config configuration of the execution platform
     * @param resManager Resource Manager
     */
    public ExecutionPlatform(String platformName, InvocationContext context, ExecutionPlatformConfiguration config,
        ResourceManager resManager) {
        LOGGER.info("Initializing execution platform " + platformName);
        this.platformName = platformName;

        this.context = context;
        this.rm = resManager;
        this.mirrors = new HashMap<>();

        // Make system properties local to each thread
        System.setProperties(new ThreadedProperties(System.getProperties()));

        // Instantiate the message queue and the stop semaphore
        this.queue = new JobQueue();
        this.startSemaphore = new Semaphore(0);
        this.stopSemaphore = new Semaphore(0);

        // Instantiate worker thread structure
        this.reuseResourcesOnBlockedInvocation = config.isReuseResourcesOnBlockedRunner();
        this.workerThreads = new TreeSet<>(new Comparator<Thread>() {

            @Override
            public int compare(Thread t1, Thread t2) {
                return Long.compare(t1.getId(), t2.getId());
            }
        });
        this.finishedWorkerThreads = new LinkedList<>();

        this.toCancel = new HashSet<>();
        this.executingJobs = new ConcurrentHashMap<>();

        addWorkerThreads(config.getInitialSize());
    }

    /**
     * Adds a new task to the queue.
     *
     * @param exec Task execution description
     */
    public void execute(Execution exec) {
        this.queue.enqueue(exec);
    }

    /**
     * Creates and starts the threads of the pool and waits until they are created.
     */
    public final synchronized void start() {
        LOGGER.info("Starting execution platform " + this.platformName);
        startTimer();
        // Start is in inverse order so that Thread 1 is the last available
        for (Thread t : this.workerThreads.descendingSet()) {
            LOGGER.info("Starting Thread " + t.getName());
            t.start();
        }
        int size = this.workerThreads.size();
        this.startSemaphore.acquireUninterruptibly(size);
        this.started = true;
        LOGGER.info("Started execution platform " + this.platformName + " with " + size);
    }

    /**
     * Stops all the threads. Inserts as many null objects to the queue as threads are managed. It wakes up all the
     * threads and wait until they process the null objects inserted which will stop them.
     */
    public final synchronized void stop() {
        LOGGER.info("Stopping execution platform " + this.platformName);
        /*
         * Empty queue to discard any pending requests and make threads finish
         */
        int size = this.workerThreads.size();
        removeWorkerThreads(size);
        LOGGER.info("Stopping mirrors for execution platform " + this.platformName);
        for (ExecutionPlatformMirror<?> mirror : this.mirrors.values()) {
            mirror.stop();
        }
        this.mirrors.clear();
        stopTimer();
        this.started = false;
        LOGGER.info("Stopped execution platform " + this.platformName);
    }

    private void startTimer() {
        if (Tracer.basicModeEnabled()) {
            Tracer.enablePThreads(1);
        }
        this.timer = new Timer(platformName + " deadline reapper");
        if (Tracer.extraeEnabled()) {
            this.timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    if (Tracer.basicModeEnabled()) {
                        Tracer.disablePThreads(1);
                    }
                    if (Tracer.extraeEnabled()) {
                        Tracer.emitEvent(TraceEvent.TIMER_THREAD_ID.getId(), TraceEvent.TIMER_THREAD_ID.getType());
                    }
                }
            }, 0);
        }
    }

    private void stopTimer() {
        if (Tracer.extraeEnabled()) {
            Semaphore sem = new Semaphore(0);
            this.timer.schedule(new TimerTask() {

                @Override
                public void run() {
                    if (Tracer.extraeEnabled()) {
                        Tracer.emitEvent(Tracer.EVENT_END, TraceEvent.TIMER_THREAD_ID.getType());
                    }
                    sem.release();
                }
            }, 0);
            try {
                sem.acquire();
            } catch (InterruptedException ie) {
                // No need to do anything
            }
        }
        this.timer.cancel();
    }

    /**
     * Add worker threads to Execution Platform.
     * 
     * @param numWorkerThreads Number of new worker threads
     */
    public final synchronized void addWorkerThreads(int numWorkerThreads) {
        Semaphore startSem;
        if (this.started) {
            startSem = new Semaphore(numWorkerThreads);
        } else {
            startSem = this.startSemaphore;
        }
        if (Tracer.basicModeEnabled()) {
            Tracer.enablePThreads(numWorkerThreads);
        }
        for (int i = 0; i < numWorkerThreads; i++) {
            int id = this.nextThreadId++;
            Executor executor = new Executor(this.context, this, "executor" + id) {

                @Override
                public void run() {
                    startSem.release();
                    super.run();
                    synchronized (ExecutionPlatform.this.finishedWorkerThreads) {
                        ExecutionPlatform.this.finishedWorkerThreads.add(Thread.currentThread());
                    }
                    ExecutionPlatform.this.stopSemaphore.release();
                }
            };
            Thread t = new Thread(executor);
            t.setName(this.platformName + " executor thread # " + id);
            this.workerThreads.add(t);
            if (this.started) {
                t.start();
            }
        }
        if (this.started) {
            startSem.acquireUninterruptibly(numWorkerThreads);
        }
    }

    /**
     * Remove worker threads from execution platform.
     * 
     * @param numWorkerThreads Number of worker threads to reduce
     */
    public final synchronized void removeWorkerThreads(int numWorkerThreads) {
        if (numWorkerThreads > 0) {
            LOGGER.info("Stopping " + numWorkerThreads + " executors from execution platform " + this.platformName);
            // if (Tracer.basicModeEnabled()) {
            // Tracer.enablePThreads();
            // } // Request N threads to finish
            for (int i = 0; i < numWorkerThreads; i++) {
                this.queue.enqueue(new Execution(null, null));
            }
            LOGGER.info("Waking up all locks");
            this.queue.wakeUpAll();

            // Wait until all threads have completed their last request
            LOGGER.info("Waiting for " + numWorkerThreads + " threads finished");
            this.stopSemaphore.acquireUninterruptibly(numWorkerThreads);
            // if (Tracer.basicModeEnabled()) {
            // Tracer.disablePThreads();
            // }
            // Stop specific language components
            joinThreads();
            LOGGER.info("Stopped " + numWorkerThreads + " executors from execution platform " + this.platformName);
        }
    }

    private void joinThreads() {
        Iterator<Thread> iter = this.finishedWorkerThreads.iterator();
        while (iter.hasNext()) {
            Thread t = iter.next();
            if (t != null) {
                try {
                    t.join();
                    iter.remove();
                    this.workerThreads.remove(t);
                    t = null;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        // For tracing
        Runtime.getRuntime().gc();
    }

    /**
     * Cancels a running job or sets it to cancel if it is not running.
     * 
     * @param jobId Id of the job to cancel.
     */
    public void cancelJob(int jobId) {
        Invoker invoker = this.executingJobs.get(jobId);
        if (invoker == null) {
            LOGGER.debug("Job " + jobId + " is to be cancelled");
            this.toCancel.add(jobId);
        } else {
            LOGGER.debug("Cancelling running job " + jobId);
            this.executingJobs.get(jobId).cancel();
        }
    }

    @Override
    public void registerRunningJob(Invocation invocation, Invoker invoker, TimerTask timeOutHandler) {
        int jobId = invocation.getJobId();
        LOGGER.debug("Registering job " + jobId);
        this.executingJobs.put(jobId, invoker);
        if (this.toCancel.contains(jobId)) {
            cancelJob(jobId);
        } else {
            long timeout = invocation.getTimeOut();
            if (timeout > 0) {
                timer.schedule(timeOutHandler, timeout);
            }
        }
    }

    @Override
    public InvocationResources acquireResources(int jobId, ResourceDescription requirements,
        InvocationResources preferredAllocation) throws UnsufficientAvailableResourcesException {

        return this.rm.acquireResources(jobId, requirements, preferredAllocation);
    }

    @Override
    public void blockedRunner(Invocation invocation, InvocationRunner runner, InvocationResources assignedResources) {
        LOGGER.debug("Stalled execution of job " + invocation.getJobId());
        if (reuseResourcesOnBlockedInvocation) {
            LOGGER.debug("Releasing resources assigned to job " + invocation.getJobId());
            int jobId = invocation.getJobId();
            this.rm.releaseResources(jobId);
            this.addWorkerThreads(1);
            this.context.idleReservedResourcesDetected(invocation.getRequirements());
        }
    }

    @Override
    public void unblockedRunner(Invocation invocation, InvocationRunner runner, InvocationResources previousAllocation,
        Semaphore sem) {
        LOGGER.debug("Execution of job " + invocation.getJobId() + " ready to continue");
        if (reuseResourcesOnBlockedInvocation) {
            LOGGER.debug("Reacquiring resources assigned to job " + invocation.getJobId());
            this.removeWorkerThreads(1);
            int jobId = invocation.getJobId();
            this.rm.reacquireResources(jobId, invocation.getRequirements(), previousAllocation, sem);
            this.context.reactivatedReservedResourcesDetected(invocation.getRequirements());
        } else {
            sem.release();
        }
    }

    @Override
    public void releaseResources(int jobId) {
        this.rm.releaseResources(jobId);
    }

    @Override
    public void unregisterRunningJob(int jobId) {
        LOGGER.debug("Unregistering job " + jobId);
        this.executingJobs.remove(jobId);
        toCancel.remove(jobId);
    }

    @Override
    public int getSize() {
        return this.workerThreads.size();
    }

    @Override
    public Execution getJob() {
        return this.queue.dequeue();
    }

    @Override
    public ExecutionPlatformMirror<?> getMirror(Class<?> invoker) {
        return this.mirrors.get(invoker);
    }

    @Override
    public void registerMirror(Class<?> invoker, ExecutionPlatformMirror<?> mirror) {
        this.mirrors.put(invoker, mirror);
    }

    @Override
    public Collection<ExecutionPlatformMirror<?>> getMirrors() {
        return mirrors.values();
    }
}
