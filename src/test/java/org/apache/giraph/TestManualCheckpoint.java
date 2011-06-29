package org.apache.giraph;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;

import org.apache.giraph.examples.GeneratedVertexInputFormat;
import org.apache.giraph.examples.SimpleCheckpointVertex;
import org.apache.giraph.examples.SimpleVertexWriter;

import junit.framework.Test;
import junit.framework.TestSuite;

/**
 * Unit test for manual checkpoint restarting
 */
public class TestManualCheckpoint extends BspCase {
    /** Where the checkpoints will be stored and restarted */
    private final String HDFS_CHECKPOINT_DIR =
        "/tmp/testBspCheckpoints";

    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public TestManualCheckpoint(String testName) {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite() {
        return new TestSuite(TestManualCheckpoint.class);
    }

    /**
     * Run a sample BSP job locally and test checkpointing.
     * @throws IOException
     * @throws ClassNotFoundException
     * @throws InterruptedException
     */
    public void testBspCheckpoint()
        throws IOException, InterruptedException, ClassNotFoundException {
        Configuration conf = new Configuration();
        setupConfiguration(conf);
        FileSystem hdfs = FileSystem.get(conf);
        conf.setClass(BspJob.VERTEX_CLASS,
                      SimpleCheckpointVertex.class,
                      HadoopVertex.class);
        conf.setClass(BspJob.VERTEX_INPUT_FORMAT_CLASS,
                      GeneratedVertexInputFormat.class,
                      VertexInputFormat.class);
        conf.setClass(BspJob.VERTEX_WRITER_CLASS,
                      SimpleVertexWriter.class,
                      VertexWriter.class);
        conf.set(BspJob.CHECKPOINT_DIRECTORY,
                 HDFS_CHECKPOINT_DIR);
        conf.setBoolean(BspJob.CLEANUP_CHECKPOINTS_AFTER_SUCCESS, false);
        BspJob bspJob = new BspJob(conf, "testBspCheckpoint");
        Path outputPath = new Path("/tmp/testBspCheckpointOutput");
        hdfs.delete(outputPath, true);
        FileOutputFormat.setOutputPath(bspJob, outputPath);
        assertTrue(bspJob.run(true));
        long fileLen = 0;
        long idSum = 0;
        if (getJobTracker() == null) {
            FileStatus fileStatus = getSinglePartFileStatus(hdfs, outputPath);
            fileLen = fileStatus.getLen();
            idSum = SimpleCheckpointVertex.finalSum;
            System.out.println("testBspCheckpoint: idSum = " + idSum +
                               " fileLen = " + fileLen);
        }

        // Restart the test from superstep 3
        conf.setLong(BspJob.RESTART_SUPERSTEP, 3);
        System.out.println(
            "testBspCheckpoint: Restarting from superstep 3" +
            " with checkpoint path = " + HDFS_CHECKPOINT_DIR);
        BspJob bspRestartedJob = new BspJob(conf, "testBspCheckpointRestarted");
        outputPath = new Path("/tmp/testBspCheckpointRestartedOutput");
        hdfs.delete(outputPath, true);
        FileOutputFormat.setOutputPath(bspRestartedJob, outputPath);
        assertTrue(bspRestartedJob.run(true));
        if (getJobTracker() == null) {
            FileStatus fileStatus = getSinglePartFileStatus(hdfs, outputPath);
            fileLen = fileStatus.getLen();
            assertTrue(fileStatus.getLen() == fileLen);
            long idSumRestarted = SimpleCheckpointVertex.finalSum;
            System.out.println("testBspCheckpoint: idSumRestarted = " +
                               idSumRestarted);
            assertTrue(idSum == idSumRestarted);
        }
    }
}