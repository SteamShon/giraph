/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.giraph.graph;

import org.apache.giraph.graph.partition.PartitionOwner;
import org.apache.hadoop.io.Writable;

import com.google.common.collect.Lists;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Helper class to write descriptions of master, workers and partition owners
 */
public class AddressesAndPartitionsWritable implements Writable {
  /** Master information */
  private MasterInfo masterInfo;
  /** List of all workers */
  private List<WorkerInfo> workerInfos;
  /** Collection of partitions */
  private Collection<PartitionOwner> partitionOwners;
  /** Partition owner class, used to deserialize object */
  private Class<? extends PartitionOwner> partitionOwnerClass;

  /**
   * Constructor when we want to serialize object
   *
   * @param masterInfo Master information
   * @param workerInfos List of all workers
   * @param partitionOwners Collection of partitions
   */
  public AddressesAndPartitionsWritable(MasterInfo masterInfo,
      List<WorkerInfo> workerInfos,
      Collection<PartitionOwner> partitionOwners) {
    this.masterInfo = masterInfo;
    this.workerInfos = workerInfos;
    this.partitionOwners = partitionOwners;
  }

  /**
   * Constructor when we want to deserialize object
   *
   * @param partitionOwnerClass Partition owner class
   */
  public AddressesAndPartitionsWritable(
      Class<? extends PartitionOwner> partitionOwnerClass) {
    this.partitionOwnerClass = partitionOwnerClass;
  }

  /**
   * Get master information
   *
   * @return Master information
   */
  public MasterInfo getMasterInfo() {
    return masterInfo;
  }

  /**
   * Get all workers
   *
   * @return List of all workers
   */
  public List<WorkerInfo> getWorkerInfos() {
    return workerInfos;
  }

  /**
   * Get partition owners
   *
   * @return Collection of partition owners
   */
  public Collection<PartitionOwner> getPartitionOwners() {
    return partitionOwners;
  }

  @Override
  public void write(DataOutput output) throws IOException {
    masterInfo.write(output);

    output.writeInt(workerInfos.size());
    for (WorkerInfo workerInfo : workerInfos) {
      workerInfo.write(output);
    }

    output.writeInt(partitionOwners.size());
    for (PartitionOwner partitionOwner : partitionOwners) {
      partitionOwner.write(output);
    }
  }

  @Override
  public void readFields(DataInput input) throws IOException {
    masterInfo = new MasterInfo();
    masterInfo.readFields(input);

    int workerInfosSize = input.readInt();
    workerInfos = Lists.newArrayListWithCapacity(workerInfosSize);
    for (int i = 0; i < workerInfosSize; i++) {
      WorkerInfo workerInfo = new WorkerInfo();
      workerInfo.readFields(input);
      workerInfos.add(workerInfo);
    }

    int partitionOwnersSize = input.readInt();
    partitionOwners = Lists.newArrayListWithCapacity(partitionOwnersSize);
    for (int i = 0; i < partitionOwnersSize; i++) {
      try {
        PartitionOwner partitionOwner = partitionOwnerClass.newInstance();
        partitionOwner.readFields(input);
        partitionOwners.add(partitionOwner);
      } catch (InstantiationException e) {
        throw new IllegalStateException("readFields: " +
            "InstantiationException on partition owner class " +
            partitionOwnerClass, e);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException("readFields: " +
            "IllegalAccessException on partition owner class " +
            partitionOwnerClass, e);
      }
    }
  }
}
