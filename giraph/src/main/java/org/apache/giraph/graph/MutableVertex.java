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

import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;

/**
 * Interface used by VertexReader to set the properties of a new vertex
 * or mutate the graph.
 *
 * @param <I> Vertex id
 * @param <V> Vertex data
 * @param <E> Edge data
 * @param <M> Message data
 */
@SuppressWarnings("rawtypes")
public abstract class MutableVertex<I extends WritableComparable,
    V extends Writable, E extends Writable, M extends Writable>
    extends Vertex<I, V, E, M> {
  /**
   * Add an edge for this vertex (happens immediately)
   *
   * @param targetVertexId target vertex
   * @param value value of the edge
   * @return Return true if succeeded, false otherwise
   */
  public abstract boolean addEdge(I targetVertexId, E value);

  /**
   * Removes an edge for this vertex (happens immediately).
   *
   * @param targetVertexId the target vertex id of the edge to be removed.
   * @return the value of the edge which was removed (or null if no
   *         edge existed to targetVertexId)
   */
  public abstract E removeEdge(I targetVertexId);

  /**
   * Sends a request to create a vertex that will be available during the
   * next superstep.
   *
   * @param id Vertex id
   * @param value Vertex value
   * @param edges Initial edges
   */
  public void addVertexRequest(I id, V value, Map<I, E> edges)
    throws IOException {
    Vertex<I, V, E, M> vertex = getConf().createVertex();
    vertex.initialize(id, value, edges);
    getGraphState().getWorkerClientRequestProcessor().addVertexRequest(vertex);
  }

  /**
   * Sends a request to create a vertex that will be available during the
   * next superstep.
   *
   * @param id Vertex id
   * @param value Vertex value
   */
  public void addVertexRequest(I id, V value) throws IOException {
    addVertexRequest(id, value, Collections.<I, E>emptyMap());
  }

  /**
   * Request to remove a vertex from the graph
   * (applied just prior to the next superstep).
   *
   * @param vertexId Id of the vertex to be removed.
   */
  public void removeVertexRequest(I vertexId) throws IOException {
    getGraphState().getWorkerClientRequestProcessor().
        removeVertexRequest(vertexId);
  }

  /**
   * Request to add an edge of a vertex in the graph
   * (processed just prior to the next superstep)
   *
   * @param sourceVertexId Source vertex id of edge
   * @param edge Edge to add
   */
  public void addEdgeRequest(I sourceVertexId, Edge<I, E> edge)
    throws IOException {
    getGraphState().getWorkerClientRequestProcessor().
        addEdgeRequest(sourceVertexId, edge);
  }

  /**
   * Request to remove an edge of a vertex from the graph
   * (processed just prior to the next superstep).
   *
   * @param sourceVertexId Source vertex id of edge
   * @param targetVertexId Destination vertex id of edge
   */
  public void removeEdgeRequest(I sourceVertexId, I targetVertexId)
    throws IOException {
    getGraphState().getWorkerClientRequestProcessor().
        removeEdgeRequest(sourceVertexId, targetVertexId);
  }
}
