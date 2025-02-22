/*
 * Copyright 2019 The gRPC Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.grpc.services;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.truth.Truth;
import io.grpc.Context;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Tests for {@link CallMetricRecorder}. */
@RunWith(JUnit4.class)
public class CallMetricRecorderTest {

  private final CallMetricRecorder recorder = new CallMetricRecorder();

  @Test
  public void dumpGivesEmptyResultWhenNoSavedMetricValues() {
    assertThat(recorder.finalizeAndDump()).isEmpty();
  }

  @Test
  public void dumpDumpsAllSavedMetricValues() {
    recorder.recordUtilizationMetric("util1", 0.154353423);
    recorder.recordUtilizationMetric("util2", 0.1367);
    recorder.recordUtilizationMetric("util3", 0.143734);
    recorder.recordRequestCostMetric("cost1", 37465.12);
    recorder.recordRequestCostMetric("cost2", 10293.0);
    recorder.recordRequestCostMetric("cost3", 1.0);
    recorder.recordCpuUtilizationMetric(0.1928);
    recorder.recordMemoryUtilizationMetric(0.474);
    recorder.recordQpsMetric(2522.54);

    MetricReport dump = recorder.finalizeAndDump2();
    Truth.assertThat(dump.getUtilizationMetrics())
        .containsExactly("util1", 0.154353423, "util2", 0.1367, "util3", 0.143734);
    Truth.assertThat(dump.getRequestCostMetrics())
        .containsExactly("cost1", 37465.12, "cost2", 10293.0, "cost3", 1.0);
    Truth.assertThat(dump.getCpuUtilization()).isEqualTo(0.1928);
    Truth.assertThat(dump.getMemoryUtilization()).isEqualTo(0.474);
    Truth.assertThat(dump.getQps()).isEqualTo(2522.54);
  }

  @Test
  public void noMetricsRecordedAfterSnapshot() {
    Map<String, Double> initDump = recorder.finalizeAndDump();
    recorder.recordUtilizationMetric("cost", 0.154353423);
    assertThat(recorder.finalizeAndDump()).isEqualTo(initDump);
  }

  @Test
  public void noMetricsRecordedIfUtilizationIsGreaterThanUpperBound() {
    recorder.recordCpuUtilizationMetric(1.001);
    recorder.recordMemoryUtilizationMetric(1.001);
    recorder.recordUtilizationMetric("util1", 1.001);

    MetricReport dump = recorder.finalizeAndDump2();
    Truth.assertThat(dump.getCpuUtilization()).isEqualTo(1.001);
    Truth.assertThat(dump.getMemoryUtilization()).isEqualTo(0);
    Truth.assertThat(dump.getQps()).isEqualTo(0);
    Truth.assertThat(dump.getUtilizationMetrics()).isEmpty();
    Truth.assertThat(dump.getRequestCostMetrics()).isEmpty();
  }

  @Test
  public void noMetricsRecordedIfUtilizationAndQpsAreLessThanLowerBound() {
    recorder.recordCpuUtilizationMetric(-0.001);
    recorder.recordMemoryUtilizationMetric(-0.001);
    recorder.recordQpsMetric(-0.001);
    recorder.recordUtilizationMetric("util1", -0.001);

    MetricReport dump = recorder.finalizeAndDump2();
    Truth.assertThat(dump.getCpuUtilization()).isEqualTo(0);
    Truth.assertThat(dump.getMemoryUtilization()).isEqualTo(0);
    Truth.assertThat(dump.getQps()).isEqualTo(0);
    Truth.assertThat(dump.getUtilizationMetrics()).isEmpty();
    Truth.assertThat(dump.getRequestCostMetrics()).isEmpty();
  }

  @Test
  public void lastValueWinForMetricsWithSameName() {
    recorder.recordRequestCostMetric("cost1", 3412.5435);
    recorder.recordRequestCostMetric("cost2", 6441.341);
    recorder.recordRequestCostMetric("cost1", 6441.341);
    recorder.recordRequestCostMetric("cost1", 4654.67);
    recorder.recordRequestCostMetric("cost2", 75.83);
    recorder.recordMemoryUtilizationMetric(0.13);
    recorder.recordMemoryUtilizationMetric(0.31);
    recorder.recordUtilizationMetric("util1", 0.2837421);
    recorder.recordMemoryUtilizationMetric(0.93840);
    recorder.recordUtilizationMetric("util1", 0.843233);
    recorder.recordQpsMetric(1928.3);
    recorder.recordQpsMetric(100.8);

    MetricReport dump = recorder.finalizeAndDump2();
    Truth.assertThat(dump.getRequestCostMetrics())
        .containsExactly("cost1", 4654.67, "cost2", 75.83);
    Truth.assertThat(dump.getMemoryUtilization()).isEqualTo(0.93840);
    Truth.assertThat(dump.getUtilizationMetrics())
        .containsExactly("util1", 0.843233);
    Truth.assertThat(dump.getCpuUtilization()).isEqualTo(0);
    Truth.assertThat(dump.getQps()).isEqualTo(100.8);
  }

  @Test
  public void getCurrent_sameEnabledInstance() {
    CallMetricRecorder recorder = new CallMetricRecorder();
    Context ctx = Context.ROOT.withValue(CallMetricRecorder.CONTEXT_KEY, recorder);
    Context origCtx = ctx.attach();
    try {
      assertThat(CallMetricRecorder.getCurrent()).isSameInstanceAs(recorder);
      assertThat(recorder.isDisabled()).isFalse();
    } finally {
      ctx.detach(origCtx);
    }
  }

  @Test
  public void getCurrent_blankContext() {
    Context blankCtx = Context.ROOT;
    Context origCtx = blankCtx.attach();
    try {
      assertThat(CallMetricRecorder.getCurrent().isDisabled()).isTrue();
    } finally {
      blankCtx.detach(origCtx);
    }
  }
}
