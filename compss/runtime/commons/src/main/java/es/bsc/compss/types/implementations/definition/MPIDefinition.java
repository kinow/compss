/*
 *  Copyright 2002-2019 Barcelona Supercomputing Center (www.bsc.es)
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
package es.bsc.compss.types.implementations.definition;

import es.bsc.compss.types.implementations.MethodType;
import es.bsc.compss.types.implementations.TaskType;
import es.bsc.compss.util.EnvironmentLoader;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.util.List;


public class MPIDefinition implements AbstractMethodImplementationDefinition {

    /**
     * Runtime Objects have serialization ID 1L.
     */
    private static final long serialVersionUID = 1L;

    public static final int NUM_PARAMS = 6;
    public static final String SIGNATURE = "mpi.MPI";

    private String mpiRunner;
    private String mpiFlags;
    private String binary;
    private String workingDir;
    private boolean scaleByCU;
    private boolean failByEV;


    /**
     * Creates a new MPIImplementation for serialization.
     */
    public MPIDefinition() {
        // For externalizable
    }

    /**
     * Creates a new MPIImplementation instance from the given parameters.
     * 
     * @param binary MPI binary path.
     * @param workingDir Binary working directory.
     * @param mpiRunner Path to the MPI command.
     * @param scaleByCU Scale by computing units property.
     * @param failByEV Flag to enable failure with EV.
     */
    public MPIDefinition(String binary, String workingDir, String mpiRunner, String mpiFlags, boolean scaleByCU,
        boolean failByEV) {
        this.mpiRunner = mpiRunner;
        this.mpiFlags = mpiFlags;
        this.workingDir = workingDir;
        this.binary = binary;
        this.scaleByCU = scaleByCU;
        this.failByEV = failByEV;
    }

    /**
     * Creates a new Definition from string array.
     * 
     * @param implTypeArgs String array.
     * @param offset Element from the beginning of the string array.
     */
    public MPIDefinition(String[] implTypeArgs, int offset) {
        this.binary = EnvironmentLoader.loadFromEnvironment(implTypeArgs[offset]);
        this.workingDir = EnvironmentLoader.loadFromEnvironment(implTypeArgs[offset + 1]);
        this.mpiRunner = EnvironmentLoader.loadFromEnvironment(implTypeArgs[offset + 2]);
        this.mpiFlags = EnvironmentLoader.loadFromEnvironment(implTypeArgs[offset + 3]);
        this.scaleByCU = Boolean.parseBoolean(implTypeArgs[offset + 4]);
        this.failByEV = Boolean.parseBoolean(implTypeArgs[offset + 5]);
        if (mpiRunner == null || mpiRunner.isEmpty()) {
            throw new IllegalArgumentException("Empty mpiRunner annotation for MPI method ");
        }
        if (this.binary == null || this.binary.isEmpty()) {
            throw new IllegalArgumentException("Empty binary annotation for MPI method ");
        }
    }

    @Override
    public void appendToArgs(List<String> lArgs, String auxParam) {
        lArgs.add(this.binary);
        lArgs.add(this.workingDir);
        lArgs.add(this.mpiRunner);
        lArgs.add(this.mpiFlags);
        lArgs.add(Boolean.toString(scaleByCU));
        lArgs.add(Boolean.toString(failByEV));
    }

    /**
     * Returns the binary path.
     * 
     * @return The binary path.
     */
    public String getBinary() {
        return this.binary;
    }

    /**
     * Returns the binary working directory.
     * 
     * @return The binary working directory.
     */
    public String getWorkingDir() {
        return this.workingDir;
    }

    /**
     * Returns the path to the MPI command.
     * 
     * @return The path to the MPI command.
     */
    public String getMpiRunner() {
        return this.mpiRunner;
    }

    /**
     * Returns the flags for the MPI command.
     * 
     * @return Flags for the MPI command.
     */
    public String getMpiFlags() {
        return this.mpiFlags;
    }

    /**
     * Returns the scale by computing units property.
     * 
     * @return scale by computing units property value.
     */
    public boolean getScaleByCU() {
        return this.scaleByCU;
    }

    /**
     * Check if fail by exit value is enabled.
     * 
     * @return True is fail by exit value is enabled.
     */
    public boolean isFailByEV() {
        return failByEV;
    }

    @Override
    public MethodType getMethodType() {
        return MethodType.MPI;
    }

    @Override
    public String toMethodDefinitionFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("[MPI RUNNER=").append(this.mpiRunner);
        sb.append(", MPI_FLAGS=").append(this.mpiFlags);
        sb.append(", BINARY=").append(this.binary);
        sb.append("]");

        return sb.toString();
    }

    @Override
    public String toShortFormat() {
        return "MPI Method with binary " + this.binary + " and MPIrunner " + this.mpiRunner;
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        this.mpiRunner = (String) in.readObject();
        this.mpiFlags = (String) in.readObject();
        this.binary = (String) in.readObject();
        this.workingDir = (String) in.readObject();
        this.scaleByCU = in.readBoolean();
        this.failByEV = in.readBoolean();
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(this.mpiRunner);
        out.writeObject(this.mpiFlags);
        out.writeObject(this.binary);
        out.writeObject(this.workingDir);
        out.writeBoolean(this.scaleByCU);
        out.writeBoolean(this.failByEV);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("MPI Implementation \n");
        sb.append("\t Binary: ").append(binary).append("\n");
        sb.append("\t MPI runner: ").append(mpiRunner).append("\n");
        sb.append("\t MPI flags: ").append(mpiFlags).append("\n");
        sb.append("\t Working directory: ").append(workingDir).append("\n");
        sb.append("\t Scale by Computing Units: ").append(scaleByCU).append("\n");
        sb.append("\t Fail by EV: ").append(this.failByEV).append("\n");
        return sb.toString();
    }

    @Override
    public TaskType getTaskType() {
        return TaskType.METHOD;
    }

}
