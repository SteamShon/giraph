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

package org.apache.giraph.utils;

import org.apache.giraph.GiraphConfiguration;
import org.apache.giraph.ImmutableClassesGiraphConfiguration;
import org.apache.giraph.graph.BspUtils;
import org.apache.giraph.graph.EdgeListVertex;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.DoubleWritable;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.io.WritableComparable;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static org.junit.Assert.assertEquals;

/**
 * Benchmark tests to insure that object creation is fast
 */
public class BspUtilsTest {
  @Rule
  public TestName name = new TestName();
  private static final Time TIME = SystemTime.get();
  private static final long COUNT = 200000;
  private Configuration conf = new Configuration();
  private long startNanos = -1;
  private long totalNanos = -1;
  private long total = 0;
  private long expected = COUNT * (COUNT - 1) / 2L;
  private ImmutableClassesGiraphConfiguration<LongWritable, LongWritable,
      LongWritable, LongWritable> configuration;

  @Before
  public void setUp() {
    conf.setClass(GiraphConfiguration.VERTEX_ID_CLASS, IntWritable.class,
        WritableComparable.class);
    conf.setClass(GiraphConfiguration.VERTEX_VALUE_CLASS, LongWritable.class,
        Writable.class);
    conf.setClass(GiraphConfiguration.EDGE_VALUE_CLASS, DoubleWritable.class,
        Writable.class);
    conf.setClass(GiraphConfiguration.MESSAGE_VALUE_CLASS, LongWritable.class,
        Writable.class);
    GiraphConfiguration conf = new GiraphConfiguration();
    conf.setVertexClass(ImmutableVertex.class);
    configuration =
        new ImmutableClassesGiraphConfiguration<LongWritable, LongWritable,
            LongWritable, LongWritable>(conf);
    total = 0;
    System.gc();
  }

  @After
  public void cleanUp() {
    totalNanos = Times.getNanosSince(TIME, startNanos);
    System.out.println(name.getMethodName() + ": took "
        + totalNanos +
        " ns for " + COUNT + " elements " + (totalNanos * 1f / COUNT) +
        " ns / element");
    assertEquals(expected, total);
    System.gc();
  }

  @Test
  public void testCreateClass() {
    startNanos = TIME.getNanoseconds();
    for (int i = 0; i < COUNT; ++i) {
      LongWritable value = BspUtils.createVertexValue(conf);
      value.set(i);
      total += value.get();
    }
  }

  @Test
  public void testNativeCreateClass() {
    startNanos = TIME.getNanoseconds();
    for (int i = 0; i < COUNT; ++i) {
      LongWritable value = new LongWritable();
      value.set(i);
      total += value.get();
    }
  }

  private Class<?> getLongWritableClass() {
    return LongWritable.class;
  }

  @Test
  public void testNewInstance()
      throws IllegalAccessException, InstantiationException {
    startNanos = TIME.getNanoseconds();
    for (int i = 0; i < COUNT; ++i) {
      LongWritable value = (LongWritable)
          getLongWritableClass().newInstance();
      value.set(i);
      total += value.get();
    }
  }

  private synchronized Class<?> getSyncLongWritableClass() {
    return LongWritable.class;
  }

  @Test
  public void testSyncNewInstance()
      throws IllegalAccessException, InstantiationException {
    startNanos = TIME.getNanoseconds();
    for (int i = 0; i < COUNT; ++i) {
      LongWritable value = (LongWritable)
          getSyncLongWritableClass().newInstance();
      value.set(i);
      total += value.get();
    }
  }

  @Test
  public void testReflectionUtilsNewInstance()
      throws IllegalAccessException, InstantiationException {
    // Throwaway to put into cache
    org.apache.hadoop.util.ReflectionUtils.newInstance(LongWritable.class,
        null);
    startNanos = TIME.getNanoseconds();
    for (int i = 0; i < COUNT; ++i) {
      LongWritable value = (LongWritable)
          org.apache.hadoop.util.ReflectionUtils.newInstance(
              getLongWritableClass(), null);
      value.set(i);
      total += value.get();
    }
  }

  @Test
  public void testConstructorNewInstance()
      throws IllegalAccessException, InstantiationException,
      NoSuchMethodException, InvocationTargetException {
    Constructor<?> constructor = LongWritable.class.getDeclaredConstructor
        (new Class[]{});
    startNanos = TIME.getNanoseconds();
    for (int i = 0; i < COUNT; ++i) {
      LongWritable value = (LongWritable) constructor.newInstance();
      value.set(i);
      total += value.get();
    }
  }

  private static class ImmutableVertex extends EdgeListVertex<LongWritable,
      LongWritable, LongWritable, LongWritable> {
    @Override
    public void compute(Iterable<LongWritable> messages) throws IOException {
    }
  }

  private ImmutableClassesGiraphConfiguration<LongWritable, LongWritable,
      LongWritable, LongWritable> getConfiguration() {
    return configuration;
  }

  @Test
  public void testImmutableClassesGiraphConfigurationNewInstance() {
    startNanos = TIME.getNanoseconds();
    for (int i = 0; i < COUNT; ++i) {
      LongWritable value = getConfiguration().createVertexValue();
      value.set(i);
      total += value.get();
    }
  }
}
