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

import org.apache.giraph.ImmutableClassesGiraphConfiguration;
import org.apache.giraph.graph.partition.PartitionOwner;
import org.apache.giraph.metrics.GiraphMetrics;
import org.apache.giraph.metrics.GiraphMetricsRegistry;
import org.apache.giraph.utils.LoggerUtils;
import org.apache.giraph.utils.MemoryUtils;
import org.apache.giraph.zk.ZooKeeperExt;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.apache.hadoop.mapreduce.InputSplit;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import com.yammer.metrics.core.Counter;

import java.io.IOException;
import java.util.List;

/**
 * Load as many vertex input splits as possible.
 * Every thread will has its own instance of WorkerClientRequestProcessor
 * to send requests.
 *
 * @param <I> Vertex index value
 * @param <V> Vertex value
 * @param <E> Edge value
 * @param <M> Message data
 */
public class VertexInputSplitsCallable<I extends WritableComparable,
    V extends Writable, E extends Writable, M extends Writable>
    extends InputSplitsCallable<I, V, E, M> {
  /** Class logger */
  private static final Logger LOG =
      Logger.getLogger(VertexInputSplitsCallable.class);
  /** Total vertices loaded */
  private long totalVerticesLoaded = 0;
  /** Total edges loaded */
  private long totalEdgesLoaded = 0;
  /** Input split max vertices (-1 denotes all) */
  private final long inputSplitMaxVertices;
  /** Bsp service worker (only use thread-safe methods) */
  private final BspServiceWorker<I, V, E, M> bspServiceWorker;

  // Metrics
  /** number of vertices loaded counter */
  private final Counter verticesLoadedCounter;
  /** number of edges loaded counter */
  private final Counter edgesLoadedCounter;

  /**
   * Constructor.
   *
   * @param context Context
   * @param graphState Graph state
   * @param configuration Configuration
   * @param bspServiceWorker service worker
   * @param inputSplitPathList List of the paths of the input splits
   * @param workerInfo This worker's info
   * @param zooKeeperExt Handle to ZooKeeperExt
   */
  public VertexInputSplitsCallable(
      Mapper<?, ?, ?, ?>.Context context,
      GraphState<I, V, E, M> graphState,
      ImmutableClassesGiraphConfiguration<I, V, E, M> configuration,
      BspServiceWorker<I, V, E, M> bspServiceWorker,
      List<String> inputSplitPathList,
      WorkerInfo workerInfo,
      ZooKeeperExt zooKeeperExt)  {
    super(context, graphState, configuration, bspServiceWorker,
        inputSplitPathList, workerInfo, zooKeeperExt,
        BspServiceWorker.VERTEX_INPUT_SPLIT_RESERVED_NODE,
        BspServiceWorker.VERTEX_INPUT_SPLIT_FINISHED_NODE,
        bspServiceWorker.vertexInputSplitsEvents);

    inputSplitMaxVertices = configuration.getInputSplitMaxVertices();
    this.bspServiceWorker = bspServiceWorker;

    // Initialize Metrics
    GiraphMetricsRegistry jobMetrics = GiraphMetrics.getInstance().perJob();
    verticesLoadedCounter = jobMetrics.getCounter(COUNTER_VERTICES_LOADED);
    edgesLoadedCounter = jobMetrics.getCounter(COUNTER_EDGES_LOADED);
  }

  /**
   * Read vertices from input split.  If testing, the user may request a
   * maximum number of vertices to be read from an input split.
   *
   * @param inputSplit Input split to process with vertex reader
   * @param graphState Current graph state
   * @return Vertices and edges loaded from this input split
   * @throws IOException
   * @throws InterruptedException
   */
  @Override
  protected VertexEdgeCount readInputSplit(
      InputSplit inputSplit,
      GraphState<I, V, E, M> graphState)
    throws IOException, InterruptedException {
    VertexInputFormat<I, V, E, M> vertexInputFormat =
        configuration.createVertexInputFormat();
    VertexReader<I, V, E, M> vertexReader =
        vertexInputFormat.createVertexReader(inputSplit, context);
    vertexReader.initialize(inputSplit, context);
    long inputSplitVerticesLoaded = 0;
    long inputSplitEdgesLoaded = 0;
    while (vertexReader.nextVertex()) {
      Vertex<I, V, E, M> readerVertex =
          vertexReader.getCurrentVertex();
      if (readerVertex.getId() == null) {
        throw new IllegalArgumentException(
            "readInputSplit: Vertex reader returned a vertex " +
                "without an id!  - " + readerVertex);
      }
      if (readerVertex.getValue() == null) {
        readerVertex.setValue(configuration.createVertexValue());
      }
      readerVertex.setConf(configuration);
      readerVertex.setGraphState(graphState);

      PartitionOwner partitionOwner =
          bspServiceWorker.getVertexPartitionOwner(readerVertex.getId());
      graphState.getWorkerClientRequestProcessor().sendVertexRequest(
          partitionOwner, readerVertex);
      context.progress(); // do this before potential data transfer
      ++inputSplitVerticesLoaded;
      inputSplitEdgesLoaded += readerVertex.getNumEdges();

      // Update status every 250k vertices
      if (((inputSplitVerticesLoaded + totalVerticesLoaded) % 250000) == 0) {
        LoggerUtils.setStatusAndLog(context, LOG, Level.INFO,
            "readInputSplit: Loaded " +
                (inputSplitVerticesLoaded + totalVerticesLoaded) +
                " vertices " +
                (inputSplitEdgesLoaded + totalEdgesLoaded) + " edges " +
                MemoryUtils.getRuntimeMemoryStats());
      }

      // For sampling, or to limit outlier input splits, the number of
      // records per input split can be limited
      if (inputSplitMaxVertices > 0 &&
          inputSplitVerticesLoaded >= inputSplitMaxVertices) {
        if (LOG.isInfoEnabled()) {
          LOG.info("readInputSplit: Leaving the input " +
              "split early, reached maximum vertices " +
              inputSplitVerticesLoaded);
        }
        break;
      }
    }
    vertexReader.close();
    totalVerticesLoaded += inputSplitVerticesLoaded;
    verticesLoadedCounter.inc(inputSplitVerticesLoaded);
    totalEdgesLoaded += inputSplitEdgesLoaded;
    edgesLoadedCounter.inc(inputSplitEdgesLoaded);
    return new VertexEdgeCount(
        inputSplitVerticesLoaded, inputSplitEdgesLoaded);
  }
}

