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

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Map;

import org.apache.giraph.utils.UnmodifiableLongNullEdgeArrayIterable;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.NullWritable;

/**
 * Compact vertex representation with primitive arrays and null edges.
 */
public abstract class LongDoubleNullDoubleVertex extends
    Vertex<LongWritable, DoubleWritable, NullWritable, DoubleWritable> {
  /** long represented vertex id */
  private long id;
  /** double represented vertex value */
  private double value;
  /** long array of neighbor vertex ids */
  private long[] neighbors;

  @Override
  public void initialize(LongWritable vertexId, DoubleWritable vertexValue,
                         Map<LongWritable, NullWritable> edges) {
    id = vertexId.get();
    value = vertexValue.get();
    neighbors = new long[(edges != null) ? edges.size() : 0];
    int n = 0;
    if (edges != null) {
      for (LongWritable neighbor : edges.keySet()) {
        neighbors[n++] = neighbor.get();
      }
    }
  }

  @Override
  public LongWritable getId() {
    return new LongWritable(id);
  }

  @Override
  public DoubleWritable getValue() {
    return new DoubleWritable(value);
  }

  @Override
  public void setValue(DoubleWritable vertexValue) {
    value = vertexValue.get();
  }

  @Override
  public Iterable<Edge<LongWritable, NullWritable>> getEdges() {
    return new UnmodifiableLongNullEdgeArrayIterable(neighbors);
  }

  @Override
  public NullWritable getEdgeValue(LongWritable targetVertexId) {
    return NullWritable.get();
  }

  @Override
  public boolean hasEdge(LongWritable targetVertexId) {
    for (long neighbor : neighbors) {
      if (neighbor == targetVertexId.get()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getNumEdges() {
    return neighbors.length;
  }

  @Override
  public void sendMessageToAllEdges(final DoubleWritable message) {
    for (long neighbor : neighbors) {
      sendMessage(new LongWritable(neighbor), message);
    }
  }

  @Override
  public void write(final DataOutput out) throws IOException {
    out.writeLong(id);
    out.writeDouble(value);
    out.writeInt(neighbors.length);
    for (int n = 0; n < neighbors.length; n++) {
      out.writeLong(neighbors[n]);
    }
  }

  @Override
  public void readFields(DataInput in) throws IOException {
    id = in.readLong();
    value = in.readDouble();
    int numEdges = in.readInt();
    neighbors = new long[numEdges];
    for (int n = 0; n < numEdges; n++) {
      neighbors[n] = in.readLong();
    }
  }
}
