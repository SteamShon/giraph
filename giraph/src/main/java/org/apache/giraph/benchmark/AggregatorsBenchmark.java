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

package org.apache.giraph.benchmark;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.giraph.aggregators.LongSumAggregator;
import org.apache.giraph.graph.DefaultMasterCompute;
import org.apache.giraph.graph.DefaultWorkerContext;
import org.apache.giraph.graph.EdgeListVertex;
import org.apache.giraph.graph.GiraphJob;
import org.apache.giraph.io.PseudoRandomVertexInputFormat;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.log4j.Logger;

import java.io.IOException;

/**
 * Benchmark for aggregators. Also checks the correctness.
 */
public class AggregatorsBenchmark implements Tool {
  /** Class logger */
  private static final Logger LOG =
      Logger.getLogger(AggregatorsBenchmark.class);
  /** Number of aggregators setting */
  private static final String AGGREGATORS_NUM = "aggregatorsbenchmark.num";
  /** Configuration */
  private Configuration conf;

  /**
   * Vertex class for AggregatorsBenchmark
   */
  public static class AggregatorsBenchmarkVertex extends
      EdgeListVertex<LongWritable, DoubleWritable, DoubleWritable,
          DoubleWritable> {
    @Override
    public void compute(Iterable<DoubleWritable> messages) throws IOException {
      int n = getNumAggregators(getConf());
      long superstep = getSuperstep();
      int w = getWorkerContextAggregated(getConf(), superstep);
      for (int i = 0; i < n; i++) {
        aggregate("w" + i, new LongWritable((superstep + 1) * i));
        aggregate("p" + i, new LongWritable(i));

        assertEquals(superstep * (getTotalNumVertices() * i) + w,
            ((LongWritable) getAggregatedValue("w" + i)).get());
        assertEquals(-(superstep * i),
            ((LongWritable) getAggregatedValue("m" + i)).get());
        assertEquals(superstep * getTotalNumVertices() * i,
            ((LongWritable) getAggregatedValue("p" + i)).get());
      }
      if (superstep > 2) {
        voteToHalt();
      }
    }
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  @Override
  public void setConf(Configuration conf) {
    this.conf = conf;
  }

  /**
   * MasterCompute class for AggregatorsBenchmark
   */
  public static class AggregatorsBenchmarkMasterCompute extends
      DefaultMasterCompute {
    @Override
    public void initialize() throws InstantiationException,
        IllegalAccessException {
      int n = getNumAggregators(getConf());
      for (int i = 0; i < n; i++) {
        registerAggregator("w" + i, LongSumAggregator.class);
        registerAggregator("m" + i, LongSumAggregator.class);
        registerPersistentAggregator("p" + i, LongSumAggregator.class);
      }
    }

    @Override
    public void compute() {
      int n = getNumAggregators(getConf());
      long superstep = getSuperstep();
      int w = getWorkerContextAggregated(getConf(), superstep);
      for (int i = 0; i < n; i++) {
        setAggregatedValue("m" + i, new LongWritable(-superstep * i));

        if (superstep > 0) {
          assertEquals(superstep * (getTotalNumVertices() * i) + w,
              ((LongWritable) getAggregatedValue("w" + i)).get());
          assertEquals(superstep * getTotalNumVertices() * i,
              ((LongWritable) getAggregatedValue("p" + i)).get());
        }
      }
    }
  }

  /**
   * WorkerContext class for AggregatorsBenchmark
   */
  public static class AggregatorsBenchmarkWorkerContext
      extends DefaultWorkerContext {
    @Override
    public void preSuperstep() {
      addToWorkerAggregators(1);
      checkAggregators();
    }

    @Override
    public void postSuperstep() {
      addToWorkerAggregators(2);
      checkAggregators();
    }

    /**
     * Check if aggregator values are correct for current superstep
     */
    private void checkAggregators() {
      int n = getNumAggregators(getContext().getConfiguration());
      long superstep = getSuperstep();
      int w = getWorkerContextAggregated(
          getContext().getConfiguration(), superstep);
      for (int i = 0; i < n; i++) {
        assertEquals(superstep * (getTotalNumVertices() * i) + w,
            ((LongWritable) getAggregatedValue("w" + i)).get());
        assertEquals(-(superstep * i),
            ((LongWritable) getAggregatedValue("m" + i)).get());
        assertEquals(superstep * getTotalNumVertices() * i,
            ((LongWritable) getAggregatedValue("p" + i)).get());
      }
    }

    /**
     * Add some value to worker aggregators.
     *
     * @param valueToAdd Which value to add
     */
    private void addToWorkerAggregators(int valueToAdd) {
      int n = getNumAggregators(getContext().getConfiguration());
      for (int i = 0; i < n; i++) {
        aggregate("w" + i, new LongWritable(valueToAdd));
      }
    }
  }

  /**
   * Get the number of aggregators from configuration
   *
   * @param conf Configuration
   * @return Number of aggregators
   */
  private static int getNumAggregators(Configuration conf) {
    return conf.getInt(AGGREGATORS_NUM, 0);
  }

  /**
   * Get the value which should be aggreagted by worker context
   *
   * @param conf Configuration
   * @param superstep Superstep
   * @return The value which should be aggregated by worker context
   */
  private static int getWorkerContextAggregated(Configuration conf,
      long superstep) {
    return (superstep <= 0) ? 0 : conf.getInt("workers", 0) * 3;
  }

  /**
   * Check if values are equal, throw an exception if they aren't
   *
   * @param expected Expected value
   * @param actual Actual value
   */
  private static void assertEquals(long expected, long actual) {
    if (expected != actual) {
      throw new RuntimeException("expected: " + expected +
          ", actual: " + actual);
    }
  }

  @Override
  public final int run(final String[] args) throws Exception {
    Options options = new Options();
    options.addOption("h", "help", false, "Help");
    options.addOption("v", "verbose", false, "Verbose");
    options.addOption("w",
        "workers",
        true,
        "Number of workers");
    options.addOption("V",
        "aggregateVertices",
        true,
        "Aggregate vertices");
    options.addOption("a",
        "aggregators",
        true,
        "Aggregators");
    HelpFormatter formatter = new HelpFormatter();
    if (args.length == 0) {
      formatter.printHelp(getClass().getName(), options, true);
      return 0;
    }
    CommandLineParser parser = new PosixParser();
    CommandLine cmd = parser.parse(options, args);
    if (cmd.hasOption('h')) {
      formatter.printHelp(getClass().getName(), options, true);
      return 0;
    }
    if (!cmd.hasOption('w')) {
      LOG.info("Need to choose the number of workers (-w)");
      return -1;
    }
    if (!cmd.hasOption('V')) {
      LOG.info("Need to set the aggregate vertices (-V)");
      return -1;
    }
    if (!cmd.hasOption('a')) {
      LOG.info("Need to set number of aggregators (-a)");
      return -1;
    }

    int workers = Integer.parseInt(cmd.getOptionValue('w'));
    GiraphJob job = new GiraphJob(getConf(), getClass().getName());
    job.getConfiguration().setVertexClass(AggregatorsBenchmarkVertex.class);
    job.getConfiguration().setMasterComputeClass(
        AggregatorsBenchmarkMasterCompute.class);
    job.getConfiguration().setVertexInputFormatClass(
        PseudoRandomVertexInputFormat.class);
    job.getConfiguration().setWorkerContextClass(
        AggregatorsBenchmarkWorkerContext.class);
    job.getConfiguration().setWorkerConfiguration(workers, workers, 100.0f);
    job.getConfiguration().setLong(
        PseudoRandomVertexInputFormat.AGGREGATE_VERTICES,
        Long.parseLong(cmd.getOptionValue('V')));
    job.getConfiguration().setLong(
        PseudoRandomVertexInputFormat.EDGES_PER_VERTEX,
        1);
    job.getConfiguration().setInt(AGGREGATORS_NUM,
        Integer.parseInt(cmd.getOptionValue('a')));
    job.getConfiguration().setInt("workers", workers);

    boolean isVerbose = false;
    if (cmd.hasOption('v')) {
      isVerbose = true;
    }
    if (job.run(isVerbose)) {
      return 0;
    } else {
      return -1;
    }
  }

  /**
   * Execute the benchmark.
   *
   * @param args Typically the command line arguments.
   * @throws Exception Any exception from the computation.
   */
  public static void main(final String[] args) throws Exception {
    System.exit(ToolRunner.run(new AggregatorsBenchmark(), args));
  }
}
