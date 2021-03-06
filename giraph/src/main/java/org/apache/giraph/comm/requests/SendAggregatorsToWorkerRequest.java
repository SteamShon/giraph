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

package org.apache.giraph.comm.requests;

import org.apache.giraph.comm.ServerData;
import org.apache.giraph.comm.aggregators.AggregatorUtils;
import org.apache.giraph.comm.aggregators.AllAggregatorServerData;
import org.apache.giraph.graph.Aggregator;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;

import java.io.DataInput;
import java.io.IOException;

/**
 * Request to send final aggregated values from worker which owns them to
 * other workers
 */
public class SendAggregatorsToWorkerRequest extends
    ByteArrayRequest implements WorkerRequest {

  /**
   * Constructor
   *
   * @param data Serialized aggregator data
   */
  public SendAggregatorsToWorkerRequest(byte[] data) {
    super(data);
  }

  /**
   * Constructor used for reflection only
   */
  public SendAggregatorsToWorkerRequest() {
  }

  @Override
  public void doRequest(ServerData serverData) {
    DataInput input = getDataInput();
    AllAggregatorServerData aggregatorData = serverData.getAllAggregatorData();
    try {
      int numAggregators = input.readInt();
      for (int i = 0; i < numAggregators; i++) {
        String aggregatorName = input.readUTF();
        String aggregatorClassName = input.readUTF();
        if (aggregatorName.equals(AggregatorUtils.SPECIAL_COUNT_AGGREGATOR)) {
          LongWritable count = new LongWritable(0);
          count.readFields(input);
          aggregatorData.receivedRequestCountFromWorker(count.get());
        } else {
          Class<Aggregator<Writable>> aggregatorClass =
              AggregatorUtils.getAggregatorClass(aggregatorClassName);
          aggregatorData.registerAggregatorClass(aggregatorName,
              aggregatorClass);
          Writable aggregatorValue =
              aggregatorData.createAggregatorInitialValue(aggregatorName);
          aggregatorValue.readFields(input);
          aggregatorData.setAggregatorValue(aggregatorName, aggregatorValue);
        }
      }
    } catch (IOException e) {
      throw new IllegalStateException("doRequest: " +
          "IOException occurred while processing request", e);
    }
    aggregatorData.receivedRequestFromWorker();
  }

  @Override
  public RequestType getType() {
    return RequestType.SEND_AGGREGATORS_TO_WORKER_REQUEST;
  }
}
