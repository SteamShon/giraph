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

import org.apache.hadoop.io.NullWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;

import com.google.common.base.Function;
import com.google.common.collect.Iterables;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mutable vertex with no edge values.
 *
 * @param <I> Vertex id
 * @param <V> Vertex data
 * @param <M> Message data
 */
public abstract class SimpleMutableVertex<I extends WritableComparable,
    V extends Writable, M extends Writable> extends MutableVertex<I, V,
    NullWritable, M> {
  /**
   * Set the neighbors of this vertex.
   *
   * @param neighbors Set of destination vertex ids.
   */
  public abstract void setNeighbors(Set<I> neighbors);

  @Override
  public void setEdges(Map<I, NullWritable> edges) {
    setNeighbors(edges.keySet());
  }

  /**
   * Get a read-only view of the neighbors of this
   * vertex, i.e. the target vertices of its out-edges.
   *
   * @return the neighbors (sort order determined by subclass implementation).
   */
  public abstract Iterable<I> getNeighbors();

  @Override
  public Iterable<Edge<I, NullWritable>> getEdges() {
    return Iterables.transform(getNeighbors(), new Function<I, Edge<I,
        NullWritable>>() {
      public Edge<I, NullWritable> apply(I targetVertexId) {
        return new Edge<I, NullWritable>(targetVertexId, NullWritable.get());
      }
    });
  }

  @Override
  public NullWritable getEdgeValue(I targetVertexId) {
    return NullWritable.get();
  }

  /**
   * Add an edge for this vertex (happens immediately)
   *
   * @param targetVertexId target vertex
   * @return Return true if succeeded, false otherwise
   */
  public abstract boolean addEdge(I targetVertexId);

  @Override
  public boolean addEdge(I targetVertexId, NullWritable value) {
    return addEdge(targetVertexId);
  }

  /**
   * Request to add an edge of a vertex in the graph
   * (processed just prior to the next superstep)
   *
   * @param sourceVertexId Source vertex id of edge
   */
  public void addEdgeRequest(I sourceVertexId) throws IOException {
    getGraphState().getWorkerClientRequestProcessor().
        addEdgeRequest(sourceVertexId, new Edge<I,
            NullWritable>(sourceVertexId, NullWritable.get()));
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    I vertexId = (I) getConf().createVertexId();
    vertexId.readFields(in);
    V vertexValue = (V) getConf().createVertexValue();
    vertexValue.readFields(in);

    int numEdges = in.readInt();
    Map<I, NullWritable> edges = new HashMap<I, NullWritable>(numEdges);
    for (int i = 0; i < numEdges; ++i) {
      I targetVertexId = (I) getConf().createVertexId();
      targetVertexId.readFields(in);
      edges.put(targetVertexId, NullWritable.get());
    }

    initialize(vertexId, vertexValue, edges);

    readHaltBoolean(in);
  }

  @Override
  public void write(DataOutput out) throws IOException {
    getId().write(out);
    getValue().write(out);

    out.writeInt(getNumEdges());
    for (I neighbor : getNeighbors()) {
      neighbor.write(out);
    }

    out.writeBoolean(isHalted());
  }
}
