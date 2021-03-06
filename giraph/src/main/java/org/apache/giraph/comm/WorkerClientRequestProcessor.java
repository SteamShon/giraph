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
package org.apache.giraph.comm;

import java.io.IOException;
import org.apache.giraph.graph.Edge;
import org.apache.giraph.graph.Vertex;
import org.apache.giraph.graph.WorkerInfo;
import org.apache.giraph.graph.partition.Partition;
import org.apache.giraph.graph.partition.PartitionOwner;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;

/**
 * Aggregates IPC requests and sends them off
 *
 * @param <I> Vertex index value
 * @param <V> Vertex value
 * @param <E> Edge value
 * @param <M> Message data
 */
public interface WorkerClientRequestProcessor<I extends WritableComparable,
    V extends Writable, E extends Writable, M extends Writable> {
  /**
   * Sends a message to destination vertex.
   *
   * @param destVertexId Destination vertex id.
   * @param message Message to send.
   * @return true if any network I/O occurred.
   */
  boolean sendMessageRequest(I destVertexId, M message);

  /**
   * Sends a vertex to the appropriate partition owner
   *
   * @param partitionOwner Owner of the vertex
   * @param vertex Vertex to send
   */
  void sendVertexRequest(PartitionOwner partitionOwner,
                         Vertex<I, V, E, M> vertex);

  /**
   * Send a partition request (no batching).
   *
   * @param workerInfo Worker to send the partition to
   * @param partition Partition to send
   */
  void sendPartitionRequest(WorkerInfo workerInfo,
                            Partition<I, V, E, M> partition);

  /**
   * Sends a request to the appropriate vertex range owner to add an edge
   *
   * @param vertexIndex Index of the vertex to get the request
   * @param edge Edge to be added
   * @throws java.io.IOException
   */
  void addEdgeRequest(I vertexIndex, Edge<I, E> edge) throws IOException;

  /**
   * Sends a request to the appropriate vertex range owner to remove an edge
   *
   * @param vertexIndex Index of the vertex to get the request
   * @param destinationVertexIndex Index of the edge to be removed
   * @throws IOException
   */
  void removeEdgeRequest(I vertexIndex, I destinationVertexIndex)
    throws IOException;

  /**
   * Sends a request to the appropriate vertex range owner to add a vertex
   *
   * @param vertex Vertex to be added
   * @throws IOException
   */
  void addVertexRequest(Vertex<I, V, E, M> vertex) throws IOException;

  /**
   * Sends a request to the appropriate vertex range owner to remove a vertex
   *
   * @param vertexIndex Index of the vertex to be removed
   * @throws IOException
   */
  void removeVertexRequest(I vertexIndex) throws IOException;

  /**
   * Flush all outgoing messages.  This ensures that all the messages have bee
   * sent, but not guaranteed to have been delivered yet.
   *
   * @throws IOException
   */
  void flush() throws IOException;

  /**
   * Get the messages sent during this superstep and clear them.
   *
   * @return Number of messages sent before the reset.
   */
  long resetMessageCount();

  /**
   * Lookup PartitionOwner for a vertex.
   *
   * @param vertexId id to look up.
   * @return PartitionOwner holding the vertex.
   */
  PartitionOwner getVertexPartitionOwner(I vertexId);
}
